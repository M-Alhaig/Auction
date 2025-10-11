package com.auction.itemservice.repositories;

import com.auction.itemservice.models.Item;
import com.auction.itemservice.models.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, Long> {

  /**
   * Find all auction items created by a specific seller with pagination support. Used for seller
   * dashboard to view their own listings.
   *
   * @param sellerId the UUID of the seller
   * @param pageable pagination and sorting parameters
   * @return paginated list of items belonging to the seller
   */
  Page<Item> findBySellerId(UUID sellerId, Pageable pageable);

  /**
   * Find all items with a specific status (PENDING, ACTIVE, ENDED) with pagination. Used for
   * displaying active auctions to users or filtering items by status.
   *
   * @param status   the item status to filter by
   * @param pageable pagination and sorting parameters
   * @return paginated list of items with the specified status
   */
  Page<Item> findByStatus(ItemStatus status, Pageable pageable);

  /**
   * Find items by status, ordered by end time in ascending order (ending soonest first). Useful for
   * showing users which auctions are about to close.
   *
   * @param status   the item status to filter by
   * @param pageable pagination and sorting parameters
   * @return paginated list of items sorted by end time (earliest first)
   */
  Page<Item> findByStatusOrderByEndTimeAsc(ItemStatus status, Pageable pageable);

  /**
   * Find all items with a specific status that end before the given time. Critical for the auction
   * scheduler to identify auctions that need to be closed. Returns List (not Page) since scheduler
   * must process ALL matching records.
   *
   * @param status  the item status (typically ACTIVE)
   * @param endTime the cutoff time to check against
   * @return list of all items matching the criteria (no pagination)
   */
  List<Item> findByStatusAndEndTimeBefore(ItemStatus status, LocalDateTime endTime);

  /**
   * Find all items with a specific status that should start at or before the given time. Used by
   * scheduler to transition PENDING auctions to ACTIVE status. Returns List (not Page) since
   * scheduler must process ALL matching records.
   *
   * @param status    the item status (typically PENDING)
   * @param startTime the cutoff time to check against (inclusive)
   * @return list of all items ready to start (no pagination)
   */
  List<Item> findByStatusAndStartTimeLessThanEqual(ItemStatus status, LocalDateTime startTime);

  /**
   * Find items by both seller and status with pagination support. Allows sellers to filter their
   * own items (e.g., "show me my active auctions").
   *
   * @param sellerId the UUID of the seller
   * @param status   the item status to filter by
   * @param pageable pagination and sorting parameters
   * @return paginated list of items matching both criteria
   */
  Page<Item> findBySellerIdAndStatus(UUID sellerId, ItemStatus status, Pageable pageable);

  /**
   * Check if an item exists and is currently ACTIVE. Used by bidding-service to validate bids
   * before processing. Returns boolean for efficiency (no need to load the entire entity).
   *
   * @param itemId the ID of the item to check
   * @return true if an item exists and status is ACTIVE, false otherwise
   */
  @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i WHERE i.id = :itemId AND i.status = 'ACTIVE'")
  boolean isItemActiveById(@Param("itemId") Long itemId);

  /**
   * Check if an item exists and is currently PENDING. Used by item-service to validate bids before
   * updating or deleting. Returns boolean for efficiency (no need to load the entire entity).
   *
   * @param itemId the ID of the item to check
   * @return true if an item exists and status is PENDING, false otherwise
   */
  @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i WHERE i.id = :itemId AND i.status = 'PENDING'")
  boolean isItemPendingById(@Param("itemId") Long itemId);


}