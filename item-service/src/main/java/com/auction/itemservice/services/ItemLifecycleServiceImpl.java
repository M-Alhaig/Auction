package com.auction.itemservice.services;

import com.auction.itemservice.events.AuctionEndedEvent;
import com.auction.itemservice.events.AuctionStartedEvent;
import com.auction.itemservice.events.EventPublisher;
import com.auction.itemservice.exceptions.ItemNotFoundException;
import com.auction.itemservice.models.Item;
import com.auction.itemservice.models.ItemStatus;
import com.auction.itemservice.repositories.ItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of ItemLifecycleService for managing auction lifecycle. Handles system-driven
 * operations: status transitions, price updates, events.
 *
 * <p>IMPORTANT: Uses self-injection pattern to enable proper transaction proxying.
 * Batch operations call individual auction operations via the 'self' proxy to ensure
 * each auction start/end gets its own transaction boundary for proper error isolation.
 */
@Slf4j
@Service
@Transactional
public class ItemLifecycleServiceImpl implements ItemLifecycleService {

  private final ItemRepository itemRepository;
  private final EventPublisher eventPublisher;
  private final ItemLifecycleService self;
  // TODO: Inject RedisLockService when Redis is configured

  /**
   * Create an ItemLifecycleServiceImpl with required dependencies and a lazily injected self reference for proxy-aware internal calls.
   *
   * @param itemRepository the repository used to load and persist Item entities
   * @param eventPublisher the publisher used to emit domain events
   * @param self           a lazily injected self-reference (proxy) used for internal calls that require proxy-aware behavior (e.g., per-method transactional boundaries)
   */
  public ItemLifecycleServiceImpl(
      ItemRepository itemRepository,
      EventPublisher eventPublisher,
      @Lazy ItemLifecycleService self) {
    this.itemRepository = itemRepository;
    this.eventPublisher = eventPublisher;
    this.self = self;
  }

  /**
   * Transition the specified item from PENDING to ACTIVE and publish an AuctionStartedEvent.
   *
   * Changes the item's status to ACTIVE, persists the update, and emits an event so downstream
   * systems can react to the auction start.
   *
   * @param itemId the identifier of the item to start
   * @throws ItemNotFoundException if no item exists with the given id
   * @throws IllegalStateException if the item's current status is not PENDING
   */

  @Override
  public void startAuction(Long itemId) {
    log.debug("Starting auction for item: {}", itemId);

    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new ItemNotFoundException(itemId));

    if (item.getStatus() != ItemStatus.PENDING) {
      log.warn("Cannot start auction - itemId: {}, currentStatus: {}, expectedStatus: PENDING",
          itemId, item.getStatus());
      throw new IllegalStateException("Item is not in PENDING status");
    }

    item.setStatus(ItemStatus.ACTIVE);
    item = itemRepository.save(item);

    log.info("Auction started - itemId: {}, title: '{}', startTime: {}",
        itemId, item.getTitle(), item.getStartTime());

    publishAuctionStartedEvent(item);
  }

  /**
   * End the auction for the specified item.
   *
   * Sets the item's status to ENDED, persists the change, logs the final price and end time,
   * and publishes an AuctionEndedEvent.
   *
   * @param itemId the identifier of the item whose auction should be ended
   * @throws ItemNotFoundException if no item exists with the given id
   * @throws IllegalStateException if the item is not currently in ACTIVE status
   */
  @Override
  public void endAuction(Long itemId) {
    log.debug("Ending auction for item: {}", itemId);

    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new ItemNotFoundException(itemId));

    if (item.getStatus() != ItemStatus.ACTIVE) {
      log.warn("Cannot end auction - itemId: {}, currentStatus: {}, expectedStatus: ACTIVE",
          itemId, item.getStatus());
      throw new IllegalStateException("Item is not in ACTIVE status");
    }

    item.setStatus(ItemStatus.ENDED);
    item = itemRepository.save(item);

    log.info("Auction ended - itemId: {}, title: '{}', finalPrice: {}, endTime: {}",
        itemId, item.getTitle(), item.getCurrentPrice(), item.getEndTime());

    publishAuctionEndedEvent(item);
  }

  /**
   * Start all pending auctions whose configured start time is now or in the past.
   *
   * Processes each eligible item and attempts to transition it to the active state; failures
   * for individual items are logged and do not stop the batch from continuing.
   *
   * @return the number of auctions that were successfully started
   */

  @Override
  public int batchStartPendingAuctions() {
    List<Item> pendingItems = self.findPendingItemsToStart();

    if (pendingItems.isEmpty()) {
      log.debug("No pending auctions to start");
      return 0;
    }

    log.info("Starting batch auction start - found {} auctions ready to start",
        pendingItems.size());

    int successCount = 0;
    int failureCount = 0;

    for (Item item : pendingItems) {
      try {
        // Call via self-proxy to ensure proper transaction boundary for each auction
        self.startAuction(item.getId());
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.error("Failed to start auction - itemId: {}, error: {}",
            item.getId(), e.getMessage(), e);
      }
    }

    log.info("Batch auction start completed - total: {}, succeeded: {}, failed: {}",
        pendingItems.size(), successCount, failureCount);

    return successCount;
  }

  /**
   * Processes active auctions whose end time has passed and attempts to end each auction.
   *
   * Attempts to end every expired active auction, logs failures while continuing processing,
   * and records per-auction success and failure counts.
   *
   * @return the number of auctions successfully ended
   */
  @Override
  public int batchEndExpiredAuctions() {
    List<Item> activeItems = self.findActiveItemsToEnd();

    if (activeItems.isEmpty()) {
      log.debug("No active auctions to end");
      return 0;
    }

    log.info("Starting batch auction end - found {} auctions ready to end", activeItems.size());

    int successCount = 0;
    int failureCount = 0;

    for (Item item : activeItems) {
      try {
        // Call via self-proxy to ensure proper transaction boundary for each auction
        self.endAuction(item.getId());
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.error("Failed to end auction - itemId: {}, error: {}",
            item.getId(), e.getMessage(), e);
      }
    }

    log.info("Batch auction end completed - total: {}, succeeded: {}, failed: {}",
        activeItems.size(), successCount, failureCount);

    return successCount;
  }

  /**
   * Update an item's current price while preventing concurrent bid races using a distributed lock.
   *
   * <p>This method is a placeholder and is not implemented: it currently throws
   * {@link UnsupportedOperationException}. When implemented it will acquire a Redis-based
   * distributed lock for the item, validate that `newPrice` is greater than the current price,
   * persist the change, and release the lock to avoid race conditions from concurrent bids.
   *
   * @param itemId   the id of the item whose current price should be updated
   * @param newPrice the candidate new current price; intended to be greater than the item's current price
   * @throws UnsupportedOperationException always thrown in the current temporary implementation
   */

  @Override
  public void updateCurrentPriceWithLock(Long itemId, BigDecimal newPrice) {
    // TODO: Implement updateCurrentPriceWithLock with Redis distributed locking
    // CRITICAL: This method handles race conditions when multiple bids arrive simultaneously
    //
    // Implementation plan:
    // 1. Acquire Redis distributed lock: lock:item:{itemId}
    //    - Use RedisLockService.tryLock(lockKey, Duration.ofSeconds(5))
    //    - If lock cannot be acquired, throw ConcurrentBidException("Retry")
    //
    // 2. Inside try-finally block:
    //    a. Find item by ID (throw ItemNotFoundException if not found)
    //    b. Validate newPrice > currentPrice (throw IllegalArgumentException if not)
    //    c. Update item.setCurrentPrice(newPrice)
    //    d. Save to database
    //
    // 3. Finally: Release lock
    //    - redisLockService.unlock(lockKey)
    //
    // TEMPORARY IMPLEMENTATION (without Redis):
    // For now, implement without locking and document the race condition risk
    // Replace with Redis locking when infrastructure is ready

    throw new UnsupportedOperationException(
        "Not implemented yet - requires Redis distributed locking");
  }

  /**
   * Check whether the specified item is currently active.
   *
   * @param itemId the database identifier of the item to check
   * @return `true` if the item is active, `false` otherwise
   */

  @Override
  @Transactional(readOnly = true)
  public boolean isItemActive(Long itemId) {
    log.debug("Checking if item is active: {}", itemId);
    return itemRepository.isItemActiveById(itemId);
  }

  /**
   * Retrieve items with status PENDING whose start time is now or earlier.
   *
   * @return a list of Item entities in PENDING status with startTime <= now; an empty list if none are found
   */

  @Override
  @Transactional(readOnly = true)
  public List<Item> findPendingItemsToStart() {
    log.debug("Finding pending items ready to start");
    return itemRepository.findByStatusAndStartTimeLessThanEqual(ItemStatus.PENDING,
        LocalDateTime.now());
  }

  /**
   * Retrieve active items whose end time has passed.
   *
   * @return a list of items in `ACTIVE` status with `endTime` before the current time; empty list if none
   */
  @Override
  @Transactional(readOnly = true)
  public List<Item> findActiveItemsToEnd() {
    log.debug("Finding active items ready to end");
    return itemRepository.findByStatusAndEndTimeBefore(ItemStatus.ACTIVE, LocalDateTime.now());
  }

  // ==================== PRIVATE HELPER METHODS ====================

  /**
   * Publish an AuctionStartedEvent for the given item to notify downstream services.
   *
   * @param item the item whose auction has started; its id, seller, title, start time, and starting price are included in the event payload
   */
  private void publishAuctionStartedEvent(Item item) {
    AuctionStartedEvent event = AuctionStartedEvent.create(
        item.getId(),
        item.getSellerId(),
        item.getTitle(),
        item.getStartTime(),
        item.getStartingPrice()
    );

    eventPublisher.publish(event);

    log.debug("Published AuctionStartedEvent - itemId: {}, title: '{}', startingPrice: {}, eventId: {}",
        event.data().itemId(), event.data().title(), event.data().startingPrice(), event.eventId());
  }

  /**
   * Publish an AuctionEndedEvent for the given item to notify downstream services.
   *
   * The published event carries the item's id, seller id, title, end time, and final price.
   * The `winnerId` field is currently set to `null` until the bidding service is integrated.
   *
   * @param item the item whose auction has ended; its end time and final price will be included in the event
   */
  private void publishAuctionEndedEvent(Item item) {
    AuctionEndedEvent event = AuctionEndedEvent.create(
        item.getId(),
        item.getSellerId(),
        item.getTitle(),
        item.getEndTime(),
        item.getCurrentPrice(),
        null  // TODO: Query Bidding Service to get winnerId once integrated
    );

    eventPublisher.publish(event);

    log.debug("Published AuctionEndedEvent - itemId: {}, title: '{}', finalPrice: {}, winnerId: {}, eventId: {}",
        event.data().itemId(), event.data().title(), event.data().finalPrice(),
        event.data().winnerId(), event.eventId());
  }
}