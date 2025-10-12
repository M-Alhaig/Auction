package com.auction.itemservice.services;

import com.auction.itemservice.dto.CreateItemRequest;
import com.auction.itemservice.dto.ItemResponse;
import com.auction.itemservice.dto.UpdateItemRequest;
import com.auction.itemservice.models.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for managing auction items. Handles user-facing CRUD operations and queries.
 */
public interface ItemService {

  // ==================== CRUD OPERATIONS ====================

  /**
 * Create a new auction item and initialize its lifecycle fields.
 *
 * <p>The created item will have status set to PENDING and currentPrice set to startingPrice.</p>
 *
 * @param request  the item creation request containing title, description, startingPrice, category IDs, end time, and other item data
 * @param sellerId the UUID of the seller creating the item
 * @return the created item as an ItemResponse with a generated identifier
 * @throws IllegalArgumentException if any provided category ID is invalid or does not exist
 */
  ItemResponse createItem(CreateItemRequest request, UUID sellerId);

  /**
 * Update an existing auction item by applying only the provided fields.
 *
 * Only items with status PENDING can be updated; null fields in the request are ignored.
 *
 * @param itemId              the ID of the item to update
 * @param request             the update request containing fields to change (null fields ignored)
 * @param authenticatedUserId the ID of the authenticated user performing the update
 * @return the updated item as an ItemResponse
 * @throws com.auction.itemservice.exceptions.ItemNotFoundException if the item doesn't exist
 * @throws com.auction.itemservice.exceptions.UnauthorizedException if the authenticated user does not own the item
 * @throws IllegalStateException                                     if the item status is not PENDING
 */
  ItemResponse updateItem(Long itemId, UpdateItemRequest request, UUID authenticatedUserId);

  /**
   * Delete an auction item. Only items with PENDING status can be deleted.
   *
   * @param itemId              the ID of the item to delete
   * @param authenticatedUserId the ID of the authenticated user from JWT
   * @throws com.auction.itemservice.exceptions.ItemNotFoundException if the item doesn't exist
   * @throws com.auction.itemservice.exceptions.UnauthorizedException if the user doesn't own the
   *                                                                   item
   * @throws IllegalStateException                                     if item status is not
   *                                                                   PENDING
   */
  void deleteItem(Long itemId, UUID authenticatedUserId);

  // ==================== QUERY OPERATIONS ====================

  /**
   * Get a single item by its ID.
   *
   * @param itemId the item ID
   * @return the item as ItemResponse
   * @throws com.auction.itemservice.exceptions.ItemNotFoundException if the item doesn't exist
   */
  ItemResponse getItemById(Long itemId);

  /**
   * Get all items with pagination support.
   *
   * @param pageable pagination and sorting parameters (page, size, sort)
   * @return page of items with metadata (totalPages, totalElements, etc.)
   */
  Page<ItemResponse> getAllItems(Pageable pageable);

  /**
 * Retrieve items that match the given status, returned in a paginated form.
 *
 * @param status   the item status to filter by (e.g., PENDING, ACTIVE, ENDED)
 * @param pageable pagination and sorting parameters
 * @return a page of ItemResponse objects with the specified status
 */
  Page<ItemResponse> getItemsByStatus(ItemStatus status, Pageable pageable);

  /**
 * Retrieve items created by the specified seller with pagination.
 *
 * @param sellerId the UUID of the seller whose items to retrieve
 * @param pageable pagination and sorting parameters
 * @return a page of ItemResponse objects for items created by the specified seller
 */
  Page<ItemResponse> getItemsBySeller(UUID sellerId, Pageable pageable);

  /**
 * Retrieve items created by the given seller that have the specified status, using pagination.
 *
 * @param sellerId the UUID of the seller whose items to retrieve
 * @param status   the item status to filter by
 * @param pageable pagination and sorting parameters
 * @return a page of ItemResponse objects for items matching the seller and status, including pagination metadata
 */
  Page<ItemResponse> getItemsBySellerAndStatus(UUID sellerId, ItemStatus status, Pageable pageable);

  /**
 * Retrieve active auctions ordered by ascending end time for the "Ending Soon" view.
 *
 * @param pageable pagination and sorting parameters
 * @return a page of active items (`ItemResponse`) sorted by end time ascending
 */
  Page<ItemResponse> getActiveAuctionsEndingSoon(Pageable pageable);
}