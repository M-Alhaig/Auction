package com.auction.itemservice.repositories;

import com.auction.itemservice.models.Item;
import com.auction.itemservice.models.ItemStatus;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
 * Retrieve a page of items with the specified status.
 *
 * @param status   the item status to filter by (e.g., PENDING, ACTIVE, ENDED)
 * @param pageable pagination and sorting parameters
 * @return a page of items that match the given status
 */
  Page<Item> findByStatus(ItemStatus status, Pageable pageable);

  /**
 * Retrieve a page of items with the given status ordered by end time ascending.
 *
 * @param status   the item status to filter by
 * @param pageable pagination and sorting parameters
 * @return a page of items with the specified status ordered by earliest end time first
 */
  Page<Item> findByStatusOrderByEndTimeAsc(ItemStatus status, Pageable pageable);

  /**
 * Retrieve all items with the specified status that end before the given cutoff time.
 *
 * @param status  the item status to filter by
 * @param endTime cutoff; items with an end time strictly before this value are returned
 * @return a list of items matching the status whose end time is before the cutoff
 */
  List<Item> findByStatusAndEndTimeBefore(ItemStatus status, Instant endTime);

  /**
 * Retrieve all items with the given status whose start time is less than or equal to the specified cutoff.
 *
 * Typically used by schedulers to find items (e.g., PENDING) that should transition to ACTIVE; returns all matching items so the scheduler can process every record.
 *
 * @param status    the item status to filter by (commonly PENDING)
 * @param startTime the inclusive cutoff start time
 * @return a list of items matching the status with startTime <= the specified cutoff
 */
  List<Item> findByStatusAndStartTimeLessThanEqual(ItemStatus status, Instant startTime);

  /**
 * Retrieve a paginated list of items for a given seller filtered by status.
 *
 * @param sellerId the UUID of the seller whose items to query
 * @param status   the item status to filter by
 * @param pageable pagination and sorting parameters
 * @return a page of items that belong to the specified seller and have the specified status
 */
  Page<Item> findBySellerIdAndStatus(UUID sellerId, ItemStatus status, Pageable pageable);

  /**
   * Determine whether the item with the given id exists and has status ACTIVE.
   *
   * @param itemId the id of the item to check
   * @return `true` if an item with the given id exists and has status `ACTIVE`, `false` otherwise
   */
  @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i WHERE i.id = :itemId AND i.status = 'ACTIVE'")
  boolean isItemActiveById(@Param("itemId") Long itemId);

  /**
   * Determines whether an item with the given id exists and has status PENDING.
   *
   * @param itemId the item's primary key
   * @return true if an item with the given id has status PENDING, false otherwise
   */
  @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i WHERE i.id = :itemId AND i.status = 'PENDING'")
  boolean isItemPendingById(@Param("itemId") Long itemId);


}