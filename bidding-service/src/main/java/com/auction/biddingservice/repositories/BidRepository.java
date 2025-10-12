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
 * Retrieve bids placed by a user, ordered by timestamp descending.
 *
 * @param bidderId the UUID of the bidder to filter bids by
 * @param pageable paging and sorting parameters for the result set
 * @return a Page containing the bidder's bids ordered newest first; may be empty
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
 * Retrieve bids placed by a specific user on a specific item, returned as a paginated result.
 *
 * <p>Sorting is determined by the provided {@code Pageable} (for example,
 * {@code Pageable.ofSize(20).withSort(Sort.by("timestamp").descending())}).
 *
 * @param itemId   the item ID to query
 * @param bidderId the bidder's UUID
 * @param pageable pagination parameters (page number, size, sort)
 * @return a page of bids placed by the specified bidder on the specified item
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
 * Determine whether any bid for the given item has an amount greater than the specified value.
 *
 * @param itemId the ID of the item to check
 * @param amount the amount to compare against
 * @return `true` if a bid exists with an amount greater than `amount`, `false` otherwise
 */
  boolean existsByItemIdAndBidAmountGreaterThan(Long itemId, BigDecimal amount);

  /**
   * Retrieve distinct item IDs that the specified user has placed bids on.
   *
   * @param bidderId the UUID of the bidder
   * @return a list of distinct item IDs the bidder has bid on; empty if none
   */
  @Query("SELECT DISTINCT b.itemId FROM Bid b WHERE b.bidderId = :bidderId")
  List<Long> findDistinctItemIdsByBidderId(@Param("bidderId") UUID bidderId);
}