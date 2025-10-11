package com.auction.item_service.services;

import com.auction.item_service.events.AuctionEndedEvent;
import com.auction.item_service.events.AuctionStartedEvent;
import com.auction.item_service.events.EventPublisher;
import com.auction.item_service.exceptions.ItemNotFoundException;
import com.auction.item_service.models.Item;
import com.auction.item_service.models.ItemStatus;
import com.auction.item_service.repositories.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of ItemLifecycleService for managing auction lifecycle. Handles system-driven
 * operations: status transitions, price updates, events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemLifecycleServiceImpl implements ItemLifecycleService {

  private final ItemRepository itemRepository;
  private final EventPublisher eventPublisher;
  // TODO: Inject RedisLockService when Redis is configured

  // ==================== STATUS TRANSITIONS ====================

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

  // ==================== BATCH OPERATIONS (SCHEDULER) ====================

  @Override
  public int batchStartPendingAuctions() {
    List<Item> pendingItems = findPendingItemsToStart();

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
        startAuction(item.getId());
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

  @Override
  public int batchEndExpiredAuctions() {
    List<Item> activeItems = findActiveItemsToEnd();

    if (activeItems.isEmpty()) {
      log.debug("No active auctions to end");
      return 0;
    }

    log.info("Starting batch auction end - found {} auctions ready to end", activeItems.size());

    int successCount = 0;
    int failureCount = 0;

    for (Item item : activeItems) {
      try {
        endAuction(item.getId());
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

  // ==================== PRICE UPDATES (CONCURRENCY CONTROL) ====================

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

  // ==================== VALIDATION FOR BIDDING SERVICE ====================

  @Override
  @Transactional(readOnly = true)
  public boolean isItemActive(Long itemId) {
    log.debug("Checking if item is active: {}", itemId);
    return itemRepository.isItemActiveById(itemId);
  }

  // ==================== INTERNAL QUERY METHODS ====================

  @Override
  @Transactional(readOnly = true)
  public List<Item> findPendingItemsToStart() {
    log.debug("Finding pending items ready to start");
    return itemRepository.findByStatusAndStartTimeLessThanEqual(ItemStatus.PENDING,
        LocalDateTime.now());
  }

  @Override
  @Transactional(readOnly = true)
  public List<Item> findActiveItemsToEnd() {
    log.debug("Finding active items ready to end");
    return itemRepository.findByStatusAndEndTimeBefore(ItemStatus.ACTIVE, LocalDateTime.now());
  }

  // ==================== PRIVATE HELPER METHODS ====================

  /**
   * Publish AuctionStartedEvent to message queue. Consumed by: Bidding Service (enable bidding),
   * Notification Service (notify subscribers).
   */
  private void publishAuctionStartedEvent(Item item) {
    AuctionStartedEvent event = AuctionStartedEvent.of(
        item.getId(),
        item.getSellerId(),
        item.getTitle(),
        item.getStartTime(),
        item.getStartingPrice()
    );

    eventPublisher.publish(event);

    log.debug("Published AuctionStartedEvent - itemId: {}, eventId: {}",
        item.getId(), event.eventId());
  }

  /**
   * Publish AuctionEndedEvent to message queue. Consumed by: Bidding Service (stop accepting bids),
   * Notification Service (notify winner/seller).
   * <p>
   * Note: winnerId is currently null since we don't have Bidding Service integrated yet. Once
   * integrated, we'll query Bidding Service API to get the highest bidder's ID.
   */
  private void publishAuctionEndedEvent(Item item) {
    AuctionEndedEvent event = AuctionEndedEvent.of(
        item.getId(),
        item.getSellerId(),
        item.getTitle(),
        item.getEndTime(),
        item.getCurrentPrice(),
        null  // TODO: Query Bidding Service to get winnerId once integrated
    );

    eventPublisher.publish(event);

    log.debug("Published AuctionEndedEvent - itemId: {}, finalPrice: {}, eventId: {}",
        item.getId(), item.getCurrentPrice(), event.eventId());
  }
}
