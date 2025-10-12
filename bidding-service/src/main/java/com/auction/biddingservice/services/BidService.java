package com.auction.biddingservice.services;

import com.auction.biddingservice.dto.BidResponse;
import com.auction.biddingservice.dto.PlaceBidRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for bid management and validation.
 *
 * <p>Core Responsibilities:
 * <ul>
 *   <li>Place bids with distributed locking (prevents race conditions)</li>
 *   <li>Validate bid business rules (amount, auction status, ownership)</li>
 *   <li>Publish events to message queue (BidPlacedEvent, UserOutbidEvent)</li>
 *   <li>Query bid history and statistics</li>
 * </ul>
 *
 * <p>Concurrency Control: Uses Redis distributed locks to ensure atomic bid placement when multiple
 * users bid on the same item simultaneously.
 *
 * <p>Event-Driven Architecture: Publishes domain events that Item Service and Notification Service
 * consume to update auction state and notify users.
 */
public interface BidService {

  /**
   * Places a new bid on an auction item with distributed locking.
   *
   * <p>Process Flow:
   * <ol>
   *   <li>Acquire Redis lock: "lock:item:{itemId}"</li>
   *   <li>Validate bid (auction active, amount > current highest, not own auction)</li>
   *   <li>Save bid to database</li>
   *   <li>Publish BidPlacedEvent (Item Service updates currentPrice)</li>
   *   <li>Publish UserOutbidEvent if someone was outbid</li>
   *   <li>Release Redis lock</li>
   * </ol>
   *
   * <p>Concurrency: Uses Redis SETNX with 5-second TTL to prevent race conditions. If lock cannot
   * be acquired, throws BidLockException (client should retry after ~100ms).
   *
   * @param request  the bid details (itemId, bidAmount)
   * @param bidderId the authenticated user's UUID (from JWT or X-Auth-Id header)
   * @return the created bid with isCurrentHighest = true
   * @throws com.auction.biddingservice.exceptions.InvalidBidException if bid violates business rules
   * @throws com.auction.biddingservice.exceptions.BidLockException if Redis lock cannot be acquired
   * @throws com.auction.biddingservice.exceptions.AuctionNotFoundException if item doesn't exist
   */
  BidResponse placeBid(PlaceBidRequest request, UUID bidderId);

  /**
   * Retrieves paginated bid history for an auction item, ordered by timestamp descending.
   *
   * @param itemId   the item ID to query
   * @param pageable pagination parameters (page, size, sort)
   * @return page of bids with isCurrentHighest flag set appropriately
   */
  Page<BidResponse> getBidHistory(Long itemId, Pageable pageable);

  /**
   * Retrieves the current highest bid for an auction item.
   *
   * @param itemId the item ID to query
   * @return the highest bid, or null if no bids exist
   */
  BidResponse getHighestBid(Long itemId);

  /**
   * Retrieves paginated bid history for a specific user.
   *
   * <p>Returns simple chronological history without expensive {@code isCurrentHighest} checks.
   * All bids are marked as historical ({@code isCurrentHighest=false}) for performance.
   *
   * @param bidderId the user's UUID
   * @param pageable pagination parameters (page, size, sort)
   * @return page of user's bids with flexible sorting
   */
  Page<BidResponse> getUserBids(UUID bidderId, Pageable pageable);

  /**
   * Retrieves paginated bids placed by a specific user on a specific item.
   *
   * <p>Sets {@code isCurrentHighest} flag accurately for this specific item.
   *
   * @param itemId   the item ID to query
   * @param bidderId the user's UUID
   * @param pageable pagination parameters (page, size, sort)
   * @return page of bids (typically small, most users bid 1-3 times per item)
   */
  Page<BidResponse> getUserBidsForItem(Long itemId, UUID bidderId, Pageable pageable);

  /**
   * Counts the total number of bids for an auction item.
   *
   * @param itemId the item ID to query
   * @return the bid count (0 if no bids exist)
   */
  long countBids(Long itemId);

  /**
   * Retrieves all distinct item IDs that a user has bid on.
   *
   * <p>Useful for "My Active Auctions" UI feature.
   *
   * @param bidderId the user's UUID
   * @return list of item IDs (may be empty)
   */
  List<Long> getItemsUserHasBidOn(UUID bidderId);
}
