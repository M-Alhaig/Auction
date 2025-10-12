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
   * Create a new auction item. Sets initial status to PENDING and currentPrice = startingPrice.
   *
   * @param request the item creation request with all required fields
   * @return the created item as ItemResponse with generated ID
   * @throws IllegalArgumentException if category IDs are invalid or don't exist
   */
  ItemResponse createItem(CreateItemRequest request, UUID sellerId);

  /**
   * Update an existing auction item. Only items with PENDING status can be updated. Supports
   * partial updates (null fields are ignored).
   *
   * @param itemId              the ID of the item to update
   * @param request             the update request with new values (null fields ignored)
   * @param authenticatedUserId the ID of the authenticated user from JWT
   * @return the updated item as ItemResponse
   * @throws com.auction.itemservice.exceptions.ItemNotFoundException if the item doesn't exist
   * @throws com.auction.itemservice.exceptions.UnauthorizedException if the user doesn't own the
   *                                                                   item
   * @throws IllegalStateException                                     if item status is not
   *                                                                   PENDING
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
   * Get items filtered by status with pagination. Useful for showing "Active Auctions", "Sold
   * Items", etc.
   *
   * @param status   the item status to filter by (PENDING, ACTIVE, ENDED)
   * @param pageable pagination and sorting parameters
   * @return page of items with the specified status
   */
  Page<ItemResponse> getItemsByStatus(ItemStatus status, Pageable pageable);

  /**
   * Get all items created by a specific seller with pagination. Used for a seller dashboard to view
   * their listings.
   *
   * @param sellerId the UUID of the seller
   * @param pageable pagination and sorting parameters
   * @return page of items belonging to the seller
   */
  Page<ItemResponse> getItemsBySeller(UUID sellerId, Pageable pageable);

  /**
   * Get items filtered by both seller and status with pagination. Allows sellers to filter their
   * own items (e.g., "show my active auctions").
   *
   * @param sellerId the UUID of the seller
   * @param status   the item status to filter by
   * @param pageable pagination and sorting parameters
   * @return page of items matching both criteria
   */
  Page<ItemResponse> getItemsBySellerAndStatus(UUID sellerId, ItemStatus status, Pageable pageable);

  /**
   * Get active auctions ordered by end time (ending soonest first). Used for homepage "Ending Soon"
   * section.
   *
   * @param pageable pagination and sorting parameters
   * @return page of active items sorted by end time ascending
   */
  Page<ItemResponse> getActiveAuctionsEndingSoon(Pageable pageable);
}
