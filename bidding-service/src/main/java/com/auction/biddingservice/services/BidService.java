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
 * Place a new bid on an auction item, making it the current highest bid when successful.
 *
 * <p>Uses distributed locking and publishes domain events so other services can react to state changes.
 *
 * @param request  the bid details, including the target itemId and bidAmount
 * @param bidderId the UUID of the authenticated bidder
 * @return the created BidResponse with `isCurrentHighest` set to `true`
 * @throws com.auction.biddingservice.exceptions.InvalidBidException if the bid violates business rules (e.g., auction inactive, amount not higher than current, bidder is item owner)
 * @throws com.auction.biddingservice.exceptions.BidLockException if a distributed lock cannot be acquired to perform the placement atomically
 * @throws com.auction.biddingservice.exceptions.AuctionNotFoundException if the specified item does not exist
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
 * Retrieves a paginated list of bids a specific user placed on a specific item.
 *
 * <p>Each returned BidResponse has its {@code isCurrentHighest} flag set accurately for this item.
 *
 * @param itemId   the item ID to query
 * @param bidderId the user's UUID
 * @param pageable pagination parameters
 * @return a page of BidResponse objects for the given user and item with {@code isCurrentHighest} set correctly
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
 * Retrieves the distinct item IDs a user has placed bids on.
 *
 * @param bidderId the UUID of the bidder
 * @return a list of distinct item IDs the user has bid on; may be empty
 */
  List<Long> getItemsUserHasBidOn(UUID bidderId);
}