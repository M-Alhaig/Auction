package com.auction.itemservice.services;

import com.auction.itemservice.dto.CreateItemRequest;
import com.auction.itemservice.dto.ItemResponse;
import com.auction.itemservice.dto.UpdateItemRequest;
import com.auction.events.AuctionTimesUpdatedEvent;
import com.auction.itemservice.events.EventPublisher;
import com.auction.itemservice.exceptions.FreezeViolationException;
import com.auction.itemservice.exceptions.ItemNotFoundException;
import com.auction.itemservice.exceptions.UnauthorizedException;
import com.auction.itemservice.models.Category;
import com.auction.itemservice.models.Item;
import com.auction.itemservice.models.ItemStatus;
import com.auction.itemservice.repositories.CategoryRepository;
import com.auction.itemservice.repositories.ItemRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of ItemService for managing auction items. Handles user-facing CRUD operations and
 * queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemServiceImpl implements ItemService {

  private final ItemRepository itemRepository;
  private final CategoryRepository categoryRepository;
  private final ItemMapper itemMapper;
  private final EventPublisher eventPublisher;

  /**
   * Freeze period duration: 24 hours before auction start.
   *
   * <p>Once an auction is within this window of its start time, sellers cannot modify
   * startTime or endTime to prevent last-minute rule changes that could disadvantage bidders.
   */
  private static final Duration FREEZE_PERIOD = Duration.ofHours(24);

  /**
   * Create a new auction item using the provided request data and associate it with the given seller.
   *
   * @param request  the data for the item to create
   * @param sellerId the UUID of the seller who owns the new item
   * @return the created item's representation, including its assigned ID and persisted fields
   */

  @Override
  public ItemResponse createItem(CreateItemRequest request, UUID sellerId) {
    log.debug("Creating item for seller: {}, title: '{}'", sellerId, request.title());

    Set<Category> categories = validateAndFetchCategories(request.categoryIds());
    Item item = itemMapper.toEntity(request, categories, sellerId);
    item = itemRepository.save(item);

    log.info("Item created - ID: {}, seller: {}, title: '{}', startTime: {}, endTime: {}",
        item.getId(), sellerId, item.getTitle(), item.getStartTime(), item.getEndTime());

    return itemMapper.toItemResponse(item);
  }

  /**
   * Updates fields of an existing item owned by the authenticated user when the item is in PENDING status.
   *
   * <p>Applies the non-null fields from the provided request to the item, persists the changes, and returns the updated item representation.
   *
   * <p><strong>Event Publishing:</strong> If startTime or endTime is modified, publishes AuctionTimesUpdatedEvent
   * to notify downstream services (e.g., Bidding Service invalidates cached metadata).
   *
   * @param itemId              the identifier of the item to update
   * @param request             the update payload containing fields to apply
   * @param authenticatedUserId the identifier of the user performing the update; must be the item's seller
   * @return the updated ItemResponse representing the persisted item
   * @throws ItemNotFoundException if no item exists with the given {@code itemId}
   * @throws UnauthorizedException if the authenticated user is not the item's seller
   * @throws IllegalStateException if the item is not in PENDING status
   * @throws FreezeViolationException if attempting to modify times within 24 hours of start
   */
  @Override
  public ItemResponse updateItem(Long itemId, UpdateItemRequest request, UUID authenticatedUserId) {
    log.debug("Updating item {} by user {}", itemId, authenticatedUserId);

    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new ItemNotFoundException(itemId));
    validateOwnership(item, authenticatedUserId);
    validateItemIsPending(item);

    // Capture old times BEFORE update (for event publishing)
    Instant oldStartTime = item.getStartTime();
    Instant oldEndTime = item.getEndTime();

    updateItemFields(request, item);
    item = itemRepository.save(item);

    // Check if times changed and publish event for cache invalidation
    boolean timesChanged = hasTimesChanged(oldStartTime, oldEndTime, item.getStartTime(), item.getEndTime());
    if (timesChanged) {
      publishAuctionTimesUpdatedEvent(item, oldStartTime, oldEndTime);
    }

    log.info("Item updated - ID: {}, seller: {}, timesChanged: {}", itemId, authenticatedUserId, timesChanged);

    return itemMapper.toItemResponse(item);
  }


  /**
   * Delete the specified item if the requester is the seller and the item is in PENDING status.
   *
   * @param itemId             the identifier of the item to delete
   * @param authenticatedUserId the identifier of the user requesting the deletion
   * @throws ItemNotFoundException if no item exists with the given id
   * @throws UnauthorizedException if the authenticated user does not own the item
   * @throws IllegalStateException if the item is not in PENDING status
   */
  @Override
  public void deleteItem(Long itemId, UUID authenticatedUserId) {
    log.debug("Deleting item {} by user {}", itemId, authenticatedUserId);

    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new ItemNotFoundException(itemId));
    validateOwnership(item, authenticatedUserId);
    validateItemIsPending(item);

    itemRepository.delete(item);

    log.info("Item deleted - ID: {}, seller: {}", itemId, authenticatedUserId);
  }

  /**
   * Retrieve an item by its identifier and return its response DTO.
   *
   * @return the matching ItemResponse
   * @throws ItemNotFoundException if no item with the given id exists
   */

  @Override
  @Transactional(readOnly = true)
  public ItemResponse getItemById(Long itemId) {
    log.debug("Fetching item by ID: {}", itemId);
    return itemRepository.findById(itemId)
        .map(itemMapper::toItemResponse)
        .orElseThrow(() -> new ItemNotFoundException(itemId));
  }

  /**
   * Retrieve a paginated list of all items.
   *
   * @param pageable paging and sorting parameters
   * @return a page of ItemResponse objects representing all items according to the provided pageable
   */
  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getAllItems(Pageable pageable) {
    log.debug("Fetching all items - page: {}, size: {}", pageable.getPageNumber(),
        pageable.getPageSize());
    return itemRepository.findAll(pageable).map(itemMapper::toItemResponse);
  }

  /**
   * Retrieve a page of items filtered by the given status.
   *
   * @param status   the item status to filter by
   * @param pageable pagination and sorting information for the result page
   * @return a page of ItemResponse objects matching the specified status
   */
  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getItemsByStatus(ItemStatus status, Pageable pageable) {
    log.debug("Fetching items by status: {}", status);
    return itemRepository.findByStatus(status, pageable).map(itemMapper::toItemResponse);
  }

  /**
   * Retrieve a paginated list of items owned by the specified seller.
   *
   * @param sellerId the UUID of the seller whose items to fetch
   * @param pageable pagination and sorting parameters for the result set
   * @return a page of ItemResponse objects representing the seller's items
   */
  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getItemsBySeller(UUID sellerId, Pageable pageable) {
    log.debug("Fetching items by seller: {}", sellerId);
    return itemRepository.findBySellerId(sellerId, pageable).map(itemMapper::toItemResponse);
  }

  /**
   * Retrieve paginated items owned by a specific seller filtered by the provided status.
   *
   * @param sellerId the UUID of the seller whose items should be returned
   * @param status   the ItemStatus to filter the seller's items by
   * @param pageable pagination and sorting information for the result set
   * @return a page of ItemResponse objects for the seller that match the given status
   */
  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getItemsBySellerAndStatus(UUID sellerId, ItemStatus status,
      Pageable pageable) {
    log.debug("Fetching items by seller: {} and status: {}", sellerId, status);
    return itemRepository.findBySellerIdAndStatus(sellerId, status, pageable)
        .map(itemMapper::toItemResponse);
  }

  /**
   * Retrieve active auction items ordered by nearest end time.
   *
   * @param pageable pagination parameters for the result set
   * @return a page of ItemResponse objects with status ACTIVE ordered by ascending end time
   */
  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getActiveAuctionsEndingSoon(Pageable pageable) {
    log.debug("Fetching active auctions ending soon");
    return itemRepository.findByStatusOrderByEndTimeAsc(ItemStatus.ACTIVE, pageable)
        .map(itemMapper::toItemResponse);
  }

  // ==================== PRIVATE HELPER METHODS ====================


  /**
   * Ensures the provided user is the seller/owner of the given item.
   *
   * @param item   the item whose ownership is being validated
   * @param userId the ID of the user attempting the operation
   * @throws UnauthorizedException if `userId` does not equal the item's seller ID
   */
  private void validateOwnership(Item item, UUID userId) {
    if (!item.getSellerId().equals(userId)) {
      log.warn("Unauthorized access attempt - itemId: {}, actualSeller: {}, attemptedBy: {}",
          item.getId(), item.getSellerId(), userId);
      throw new UnauthorizedException("User does not own this item");
    }
  }

  /**
   * Ensure the provided item's status is PENDING.
   *
   * @param item the item whose status will be validated
   * @throws IllegalStateException if the item's status is not PENDING
   */
  private void validateItemIsPending(Item item) {
    if (item.getStatus() != ItemStatus.PENDING) {
      log.warn("Cannot modify item - itemId: {}, currentStatus: {}, expectedStatus: PENDING",
          item.getId(), item.getStatus());
      throw new IllegalStateException("Item is not in PENDING status");
    }
  }

  /**
   * Validate the provided category IDs and fetch the corresponding Category entities.
   *
   * @param categoryIds the set of category IDs to validate; may be null or empty
   * @return a set of matching Category entities; an empty set if {@code categoryIds} is null or empty
   * @throws IllegalArgumentException if one or more requested category IDs do not exist
   */
  private Set<Category> validateAndFetchCategories(Set<Integer> categoryIds) {
    if (categoryIds == null || categoryIds.isEmpty()) {
      return Set.of();
    }

    Set<Category> categories = categoryRepository.findByIdIn(categoryIds);

    // Validate all requested IDs were found
    if (categories.size() != categoryIds.size()) {
      log.warn("Invalid category IDs - requested: {}, found: {}, categoryIds: {}",
          categoryIds.size(), categories.size(), categoryIds);
      throw new IllegalArgumentException(
          "One or more category IDs are invalid. Requested: " + categoryIds.size() +
              ", Found: " + categories.size()
      );
    }

    return categories;
  }

  /**
   * Apply non-null fields from an UpdateItemRequest to the given Item entity.
   *
   * <p>Only fields present (non-null) in the request are copied. If the starting price is updated,
   * the method synchronizes the item's current price to the new starting price only when the current
   * price still equals the previous starting price (indicating no bids); otherwise the current price
   * is preserved.
   *
   * @param request the update request containing optional fields to apply
   * @param item the item entity to modify in-place
   * @throws FreezeViolationException if attempting to modify startTime or endTime within the freeze period
   */
  private void updateItemFields(UpdateItemRequest request, Item item) {
    // Check freeze period BEFORE modifying auction times
    if (request.startTime() != null || request.endTime() != null) {
      validateNotInFreezePeriod(item);
    }

    if (request.title() != null) {
      item.setTitle(request.title());
    }
    if (request.description() != null) {
      item.setDescription(request.description());
    }
    if (request.startingPrice() != null) {
      BigDecimal oldStartingPrice = item.getStartingPrice();
      item.setStartingPrice(request.startingPrice());

      // Only update the currentPrice if no bids have been placed yet
      // (currentPrice still equals the old startingPrice)
      // This prevents accidental erasure of bid data due to race conditions or bugs
      if (item.getCurrentPrice().compareTo(oldStartingPrice) == 0) {
        item.setCurrentPrice(request.startingPrice());
        log.debug("Updated currentPrice to match new startingPrice: {}", request.startingPrice());
      } else {
        log.warn(
            "Preserving currentPrice {} despite startingPrice change from {} to {} - bids may exist",
            item.getCurrentPrice(), oldStartingPrice, request.startingPrice());
      }
    }
    if (request.categoryIds() != null) {
      Set<Category> categories = validateAndFetchCategories(request.categoryIds());
      item.setCategories(categories);
    }
    if (request.imageUrl() != null) {
      item.setImageUrl(request.imageUrl());
    }
    if (request.startTime() != null) {
      item.setStartTime(request.startTime());
    }
    if (request.endTime() != null) {
      item.setEndTime(request.endTime());
    }
  }

  /**
   * Validates that the item is not within the freeze period for time modifications.
   *
   * <p>The freeze period begins 24 hours before the auction's scheduled start time.
   * Once inside this window, startTime and endTime cannot be modified to ensure fairness
   * and prevent sellers from changing rules after bidders have made decisions.
   *
   * @param item the item whose freeze period status will be checked
   * @throws FreezeViolationException if the current time is within 24 hours of the auction start
   */
  private void validateNotInFreezePeriod(Item item) {
    // Guard against null startTime (defensive programming)
    Instant startTime = item.getStartTime();
    if (startTime == null) {
      log.debug("Freeze period check skipped - itemId: {}, startTime is null", item.getId());
      return;  // No freeze period if auction has no start time yet
    }

    Instant now = Instant.now();
    Instant freezeStartsAt = startTime.minus(FREEZE_PERIOD);

    // Check if we're currently within the freeze period (boundary: >= freezeStartsAt)
    // Using !isBefore ensures we catch the exact boundary instant
    if (!now.isBefore(freezeStartsAt)) {
      log.warn("Freeze period violation - itemId: {}, startTime: {}, freezeStartsAt: {}, now: {}",
          item.getId(), startTime, freezeStartsAt, now);
      throw new FreezeViolationException(
          String.format(
              "Cannot modify auction times for item %d. Auction is within 24-hour freeze period. "
                  + "Start time: %s, Freeze began: %s. "
                  + "Please contact support if you need to make changes.",
              item.getId(), startTime, freezeStartsAt));
    }

    log.debug("Freeze period check passed - itemId: {}, freezeStartsAt: {}, now: {}",
        item.getId(), freezeStartsAt, now);
  }

  /**
   * Check if auction times (startTime or endTime) have changed.
   *
   * <p>Uses {@link java.util.Objects#equals} to safely handle null values.
   * If either old or new time is null, they are considered different unless both are null.
   *
   * @param oldStartTime the start time before update
   * @param oldEndTime the end time before update
   * @param newStartTime the start time after update
   * @param newEndTime the end time after update
   * @return true if either startTime or endTime changed, false otherwise
   */
  private boolean hasTimesChanged(Instant oldStartTime, Instant oldEndTime,
                                   Instant newStartTime, Instant newEndTime) {
    // Use Objects.equals to safely handle null values
    boolean startTimeChanged = !Objects.equals(oldStartTime, newStartTime);
    boolean endTimeChanged = !Objects.equals(oldEndTime, newEndTime);
    return startTimeChanged || endTimeChanged;
  }

  /**
   * Publish AuctionTimesUpdatedEvent to notify downstream services of time changes.
   *
   * <p>Primary consumer: Bidding Service invalidates cached metadata to prevent
   * stale time-based validation (e.g., rejecting valid bids after extension).
   *
   * @param item the updated item with new times
   * @param oldStartTime the previous start time
   * @param oldEndTime the previous end time
   */
  private void publishAuctionTimesUpdatedEvent(Item item, Instant oldStartTime, Instant oldEndTime) {
    AuctionTimesUpdatedEvent event = AuctionTimesUpdatedEvent.create(
        item.getId(),
        oldStartTime,
        item.getStartTime(),
        oldEndTime,
        item.getEndTime(),
        item.getStatus().name()  // Convert ItemStatus enum to String
    );

    eventPublisher.publish(event);

    log.info("Published AuctionTimesUpdatedEvent - itemId: {}, oldStart: {}, newStart: {}, " +
            "oldEnd: {}, newEnd: {}, eventId: {}",
        event.data().itemId(), oldStartTime, item.getStartTime(),
        oldEndTime, item.getEndTime(), event.eventId());
  }
}