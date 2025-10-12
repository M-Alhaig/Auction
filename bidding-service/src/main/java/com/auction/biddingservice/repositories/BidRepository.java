package com.auction.biddingservice.repositories;

import com.auction.biddingservice.models.Bid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link Bid} entity operations.
 *
 * <p>Provides query methods for bid management, validation, and history tracking. Uses Spring Data
 * JPA derived queries and custom JPQL for efficient database access.
 *
 * <p>Key Use Cases:
 * <ul>
 *   <li>Bid validation: Check if bid amount exceeds current highest bid</li>
 *   <li>Winner determination: Find highest bid for an auction</li>
 *   <li>Bid history: Retrieve chronological bids for items or users</li>
 *   <li>User participation tracking: Find all items a user has bid on</li>
 * </ul>
 */
@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

  /**
   * Finds the highest bid for a specific auction item.
   *
   * @param itemId the item ID to query
   * @return the bid with the highest amount, or empty if no bids exist
   */
  Optional<Bid> findFirstByItemIdOrderByBidAmountDesc(Long itemId);

  /**
   * Finds all bids placed by a specific user, ordered by timestamp descending.
   *
   * @param bidderId the user's UUID
   * @return list of bids (may be empty, could be large for active bidders)
   */
  Page<Bid> findByBidderId(UUID bidderId, Pageable pageable);

  /**
   * Finds all bids for an auction item with pagination.
   *
   * <p>Sorting is controlled by the caller via {@code Pageable.sort()}. Common sorts:
   * <ul>
   *   <li>{@code Sort.by("timestamp").descending()} - chronological (newest first)</li>
   *   <li>{@code Sort.by("bidAmount").descending()} - highest to lowest</li>
   * </ul>
   *
   * @param itemId   the item ID to query
   * @param pageable pagination parameters (page number, size, sort)
   * @return page of bids with flexible sorting
   */
  Page<Bid> findByItemId(Long itemId, Pageable pageable);

  /**
   * Finds all bids placed by a specific user on a specific item with pagination.
   *
   * <p>Sorting is controlled by the caller via {@code Pageable.sort()}. Typical usage:
   * {@code Sort.by("timestamp").descending()} for chronological history.
   *
   * @param itemId   the item ID to query
   * @param bidderId the user's UUID
   * @param pageable pagination parameters (page number, size, sort)
   * @return page of bids (typically small, most users bid 1-3 times per item)
   */
  Page<Bid> findByItemIdAndBidderId(Long itemId, UUID bidderId, Pageable pageable);

  /**
   * Counts the total number of bids for a specific auction item.
   *
   * @param itemId the item ID to query
   * @return the bid count (0 if no bids exist)
   */
  long countByItemId(Long itemId);

  /**
   * Checks if any bid exists for an item with an amount greater than the specified value.
   *
   * <p>Efficient way to determine if a bid is outdated without fetching the highest bid entity.
   *
   * @param itemId the item ID to query
   * @param amount the bid amount to compare against
   * @return true if a higher bid exists, false otherwise
   */
  boolean existsByItemIdAndBidAmountGreaterThan(Long itemId, BigDecimal amount);

  /**
   * Finds all distinct item IDs that a user has bid on.
   *
   * <p>Useful for "My Active Auctions" features where we need to show which auctions a user is
   * participating in without loading all bid details.
   *
   * @param bidderId the user's UUID
   * @return list of item IDs (may be empty, typically 5-20 items for active users)
   */
  @Query("SELECT DISTINCT b.itemId FROM Bid b WHERE b.bidderId = :bidderId")
  List<Long> findDistinctItemIdsByBidderId(@Param("bidderId") UUID bidderId);
}
