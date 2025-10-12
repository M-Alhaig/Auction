package com.auction.itemservice.services;

import com.auction.itemservice.models.Item;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for managing auction item lifecycle. Handles system-driven operations:
 * scheduling, status transitions, price updates with concurrency control, and event publishing.
 * Called by schedulers, RabbitMQ event consumers, and other services.
 */
public interface ItemLifecycleService {

  // ==================== STATUS TRANSITIONS ====================

  /**
 * Start the auction for the specified item, transitioning its status from PENDING to ACTIVE.
 *
 * <p>When the transition succeeds, an AuctionStartedEvent is published to the message queue.
 *
 * @param itemId the identifier of the item to start
 * @throws com.auction.itemservice.exceptions.ItemNotFoundException if the item does not exist
 * @throws IllegalStateException if the item is not currently in PENDING status
 */
  void startAuction(Long itemId);

  /**
 * End an auction for the specified item when its end time is reached.
 *
 * Publishes an AuctionEndedEvent to the message queue including winner information.
 *
 * @param itemId the ID of the item to end
 * @throws com.auction.itemservice.exceptions.ItemNotFoundException if the item does not exist
 * @throws IllegalStateException                                     if the item is not in ACTIVE status
 */
  void endAuction(Long itemId);

  // ==================== BATCH OPERATIONS (SCHEDULER) ====================

  /**
 * Start all pending auctions whose start time has been reached.
 *
 * Invoked by a scheduled job (runs every minute) and processes matching items in a single batch.
 *
 * @return the number of auctions transitioned to ACTIVE
 */
  int batchStartPendingAuctions();

  /**
   * Find and end all ACTIVE auctions whose end time has been reached. Called by scheduled job every
   * minute. Processes all matching items in a single batch for efficiency.
   *
   * @return number of auctions ended
   */
  int batchEndExpiredAuctions();

  // ==================== PRICE UPDATES (CONCURRENCY CONTROL) ====================

  /**
 * Update an item's current price using a distributed lock to ensure concurrent bids are serialized.
 *
 * Updates the stored current price to {@code newPrice} only if {@code newPrice} is greater than the
 * item's existing current price. Intended to be invoked when a new bid is processed.
 *
 * @param itemId   identifier of the item to update
 * @param newPrice the bid price to apply as the new current price
 * @throws com.auction.itemservice.exceptions.ItemNotFoundException  if the item does not exist
 * @throws com.auction.itemservice.exceptions.ConcurrentBidException if a distributed lock cannot be acquired (retry suggested)
 * @throws IllegalArgumentException                                   if {@code newPrice} is less than or equal to the current price
 */
  void updateCurrentPriceWithLock(Long itemId, BigDecimal newPrice);

  // ==================== VALIDATION FOR BIDDING SERVICE ====================

  /**
 * Determine whether the specified item exists and is in ACTIVE status.
 *
 * Intended for quick validation by the bidding service without loading the full entity.
 *
 * @param itemId the identifier of the item to check
 * @return `true` if the item exists and is in ACTIVE status, `false` otherwise
 */
  boolean isItemActive(Long itemId);

  // ==================== INTERNAL QUERY METHODS ====================

  /**
 * Locate all items in PENDING status whose configured start time is at or before the current time.
 *
 * @return list of items eligible to be started; returned unpaginated
 */
  List<Item> findPendingItemsToStart();

  /**
 * Locate ACTIVE items whose auction end time is at or before the current time.
 *
 * @return a non-paginated list of items ready to be transitioned to the ended status
 */
  List<Item> findActiveItemsToEnd();
}