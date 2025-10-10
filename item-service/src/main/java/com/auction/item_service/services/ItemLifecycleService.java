package com.auction.item_service.services;

import com.auction.item_service.models.Item;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for managing auction item lifecycle.
 * Handles system-driven operations: scheduling, status transitions,
 * price updates with concurrency control, and event publishing.
 * Called by schedulers, RabbitMQ event consumers, and other services.
 */
public interface ItemLifecycleService {

    // ==================== STATUS TRANSITIONS ====================

    /**
     * Transition a single item from PENDING to ACTIVE status.
     * Called when auction start time is reached.
     * Publishes AuctionStartedEvent to message queue.
     *
     * @param itemId the ID of the item to start
     * @throws com.auction.item_service.exceptions.ItemNotFoundException if item doesn't exist
     * @throws IllegalStateException if item is not in PENDING status
     */
    void startAuction(Long itemId);

    /**
     * Transition a single item from ACTIVE to Ended status.
     * Called when auction end time is reached.
     * Publishes AuctionEndedEvent to message queue with winner information.
     *
     * @param itemId the ID of the item to end
     * @throws com.auction.item_service.exceptions.ItemNotFoundException if item doesn't exist
     * @throws IllegalStateException if item is not in ACTIVE status
     */
    void endAuction(Long itemId);

    // ==================== BATCH OPERATIONS (SCHEDULER) ====================

    /**
     * Find and start all PENDING auctions whose start time has been reached.
     * Called by scheduled job every minute.
     * Processes all matching items in a single batch for efficiency.
     *
     * @return number of auctions started
     */
    int batchStartPendingAuctions();

    /**
     * Find and end all ACTIVE auctions whose end time has been reached.
     * Called by scheduled job every minute.
     * Processes all matching items in a single batch for efficiency.
     *
     * @return number of auctions ended
     */
    int batchEndExpiredAuctions();

    // ==================== PRICE UPDATES (CONCURRENCY CONTROL) ====================

    /**
     * Update the current price of an item with distributed locking.
     * Called when BidPlacedEvent is received from bidding-service.
     * Uses Redis distributed lock to prevent race conditions.
     * Only updates if newPrice > currentPrice.
     *
     * @param itemId the ID of the item
     * @param newPrice the new current price from the bid
     * @throws com.auction.item_service.exceptions.ItemNotFoundException if item doesn't exist
     * @throws com.auction.item_service.exceptions.ConcurrentBidException if lock cannot be acquired (retry suggested)
     * @throws IllegalArgumentException if newPrice <= currentPrice
     */
    void updateCurrentPriceWithLock(Long itemId, BigDecimal newPrice);

    // ==================== VALIDATION FOR BIDDING SERVICE ====================

    /**
     * Check if an item exists and is currently in ACTIVE status.
     * Used by bidding-service to validate bids before processing.
     * Efficient boolean check without loading full entity.
     *
     * @param itemId the ID of the item to check
     * @return true if item exists and status is ACTIVE, false otherwise
     */
    boolean isItemActive(Long itemId);

    // ==================== INTERNAL QUERY METHODS ====================

    /**
     * Find all PENDING items whose start time has been reached.
     * Used internally by batchStartPendingAuctions().
     *
     * @return list of items ready to start (not paginated)
     */
    List<Item> findPendingItemsToStart();

    /**
     * Find all ACTIVE items whose end time has been reached.
     * Used internally by batchEndExpiredAuctions().
     *
     * @return list of items ready to end (not paginated)
     */
    List<Item> findActiveItemsToEnd();
}
