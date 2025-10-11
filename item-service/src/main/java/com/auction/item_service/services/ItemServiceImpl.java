package com.auction.item_service.services;

import com.auction.item_service.dto.CreateItemRequest;
import com.auction.item_service.dto.ItemResponse;
import com.auction.item_service.dto.UpdateItemRequest;
import com.auction.item_service.exceptions.ItemNotFoundException;
import com.auction.item_service.exceptions.UnauthorizedException;
import com.auction.item_service.models.Category;
import com.auction.item_service.models.Item;
import com.auction.item_service.models.ItemStatus;
import com.auction.item_service.repositories.CategoryRepository;
import com.auction.item_service.repositories.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

  // ==================== CRUD OPERATIONS ====================

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

  @Override
  public ItemResponse updateItem(Long itemId, UpdateItemRequest request, UUID authenticatedUserId) {
    log.debug("Updating item {} by user {}", itemId, authenticatedUserId);

    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new ItemNotFoundException(itemId));
    validateOwnership(item, authenticatedUserId);
    validateItemIsPending(item);

    updateItemFields(request, item);
    item = itemRepository.save(item);

    log.info("Item updated - ID: {}, seller: {}", itemId, authenticatedUserId);

    return itemMapper.toItemResponse(item);
  }


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

  // ==================== QUERY OPERATIONS ====================

  @Override
  @Transactional(readOnly = true)
  public ItemResponse getItemById(Long itemId) {
    log.debug("Fetching item by ID: {}", itemId);
    return itemRepository.findById(itemId)
        .map(itemMapper::toItemResponse)
        .orElseThrow(() -> new ItemNotFoundException(itemId));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getAllItems(Pageable pageable) {
    log.debug("Fetching all items - page: {}, size: {}", pageable.getPageNumber(),
        pageable.getPageSize());
    return itemRepository.findAll(pageable).map(itemMapper::toItemResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getItemsByStatus(ItemStatus status, Pageable pageable) {
    log.debug("Fetching items by status: {}", status);
    return itemRepository.findByStatus(status, pageable).map(itemMapper::toItemResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getItemsBySeller(UUID sellerId, Pageable pageable) {
    log.debug("Fetching items by seller: {}", sellerId);
    return itemRepository.findBySellerId(sellerId, pageable).map(itemMapper::toItemResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getItemsBySellerAndStatus(UUID sellerId, ItemStatus status,
      Pageable pageable) {
    log.debug("Fetching items by seller: {} and status: {}", sellerId, status);
    return itemRepository.findBySellerIdAndStatus(sellerId, status, pageable)
        .map(itemMapper::toItemResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ItemResponse> getActiveAuctionsEndingSoon(Pageable pageable) {
    log.debug("Fetching active auctions ending soon");
    return itemRepository.findByStatusOrderByEndTimeAsc(ItemStatus.ACTIVE, pageable)
        .map(itemMapper::toItemResponse);
  }

  // ==================== PRIVATE HELPER METHODS ====================


  // - validateOwnership(Item item, UUID userId)
  private void validateOwnership(Item item, UUID userId) {
    if (!item.getSellerId().equals(userId)) {
      log.warn("Unauthorized access attempt - itemId: {}, actualSeller: {}, attemptedBy: {}",
          item.getId(), item.getSellerId(), userId);
      throw new UnauthorizedException("User does not own this item");
    }
  }

  // - validateItemIsPending(Item item)
  private void validateItemIsPending(Item item) {
    if (item.getStatus() != ItemStatus.PENDING) {
      log.warn("Cannot modify item - itemId: {}, currentStatus: {}, expectedStatus: PENDING",
          item.getId(), item.getStatus());
      throw new IllegalStateException("Item is not in PENDING status");
    }
  }

  // - validateAndFetchCategories(Set<Integer> categoryIds)
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

  private void updateItemFields(UpdateItemRequest request, Item item) {
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
}
