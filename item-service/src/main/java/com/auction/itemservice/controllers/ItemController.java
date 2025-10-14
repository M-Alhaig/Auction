package com.auction.itemservice.controllers;

import com.auction.itemservice.dto.CreateItemRequest;
import com.auction.itemservice.dto.ItemResponse;
import com.auction.itemservice.dto.UpdateItemRequest;
import com.auction.itemservice.models.ItemStatus;
import com.auction.itemservice.services.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@Slf4j
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

  private final ItemService itemService;

  /**
   * Create a new auction item for the specified seller.
   *
   * @param request  the validated item creation request
   * @param sellerId the authenticated seller's UUID (currently supplied via `X-Auth-Id` header)
   * @return the created ItemResponse
   */
  @PostMapping
  public ResponseEntity<ItemResponse> createItem(
      @Valid @RequestBody CreateItemRequest request,
      @RequestHeader("X-Auth-Id") UUID sellerId
  ) {
    log.info("POST /api/items - Creating item for seller: {}", sellerId);

    ItemResponse response = itemService.createItem(request, sellerId);

    log.info("POST /api/items - Item created: {}", response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Update an existing auction item with the provided partial fields (applies to items in PENDING status).
   *
   * @param id      the item identifier
   * @param request the partial update request containing fields to change
   * @param userId  the UUID of the authenticated user performing the update
   * @return the updated ItemResponse
   */
  @PatchMapping("/{id}")
  public ResponseEntity<ItemResponse> updateItem(
      @PathVariable Long id,
      @Valid @RequestBody UpdateItemRequest request,
      @RequestHeader("X-Auth-Id") UUID userId
  ) {
    log.info("PATCH /api/items/{} - Updating by user: {}", id, userId);

    ItemResponse response = itemService.updateItem(id, request, userId);
    log.info("PATCH /api/items/{} - Item updated successfully", id);

    return ResponseEntity.ok(response);
  }

  /**
   * Delete the specified auction item if it is in PENDING status and the requesting user is authorized.
   *
   * @param id the item identifier
   * @param userId the authenticated user's UUID
   * @return HTTP 204 No Content when the item is successfully deleted
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteItem(
      @PathVariable Long id,
      @RequestHeader("X-Auth-Id") UUID userId
  ) {
    log.info("DELETE /api/items/{} - Deleting by user: {}", id, userId);
    itemService.deleteItem(id, userId);
    log.info("DELETE /api/items/{} - Item deleted successfully", id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Retrieve an item by its identifier.
   *
   * @param id the item's database identifier
   * @return the requested item's representation
   */
  @GetMapping("/{id}")
  public ResponseEntity<ItemResponse> getItemById(@PathVariable Long id) {
    log.debug("GET /api/items/{}", id);
    ItemResponse response = itemService.getItemById(id);
    return ResponseEntity.ok(response);
  }

  /**
   * Retrieve a paginated list of all items.
   *
   * @param pageable pagination and sorting information; defaults to page 0 and size 20 when not provided
   * @return a page of ItemResponse objects for the requested page
   */
  @GetMapping
  public ResponseEntity<Page<ItemResponse>> getAllItems(
      @PageableDefault(page = 0, size = 20) Pageable pageable
  ) {
    log.debug("GET /api/items - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
    Page<ItemResponse> response = itemService.getAllItems(pageable);
    return ResponseEntity.ok(response);
  }

  /**
   * Retrieve a page of items filtered by the given status.
   *
   * @param status   the item status to filter by
   * @param pageable pagination and sorting information; defaults to page 0 and size 20
   * @return         a page of ItemResponse objects matching the requested status
   */
  @GetMapping("/status/{status}")
  public ResponseEntity<Page<ItemResponse>> getItemsByStatus(
      @PathVariable ItemStatus status,
      @PageableDefault(page = 0, size = 20) Pageable pageable
  ) {
    log.debug("GET /api/items/status/{} - page: {}, size: {}", status, pageable.getPageNumber(), pageable.getPageSize());
    Page<ItemResponse> response = itemService.getItemsByStatus(status, pageable);
    return ResponseEntity.ok(response);
  }

  /**
   * Retrieve a page of items belonging to the specified seller.
   *
   * @param sellerId the UUID of the seller whose items to retrieve
   * @param pageable pagination and sorting information (defaults to page 0, size 20 when not provided)
   * @return a Page of ItemResponse objects for the given seller and requested page
   */
  @GetMapping("/seller/{sellerId}")
  public ResponseEntity<Page<ItemResponse>> getItemsBySeller(
      @PathVariable UUID sellerId,
      @PageableDefault(page = 0, size = 20) Pageable pageable
  ) {
    log.debug("GET /api/items/seller/{} - page: {}, size: {}", sellerId, pageable.getPageNumber(), pageable.getPageSize());
    Page<ItemResponse> response = itemService.getItemsBySeller(sellerId, pageable);
    return ResponseEntity.ok(response);
  }

  /**
   * Retrieve a paginated list of items belonging to a specific seller filtered by item status.
   *
   * @param sellerId the UUID of the seller whose items to retrieve
   * @param status the ItemStatus to filter items by
   * @param pageable pagination and sorting information (defaults to page 0, size 20)
   * @return a Page of ItemResponse containing items that match the seller and status
   */
  @GetMapping("/seller/{sellerId}/status/{status}")
  public ResponseEntity<Page<ItemResponse>> getItemsBySellerAndStatus(
      @PathVariable UUID sellerId,
      @PathVariable ItemStatus status,
      @PageableDefault(page = 0, size = 20) Pageable pageable
  ) {
    log.debug("GET /api/items/seller/{}/status/{} - page: {}, size: {}", sellerId, status, pageable.getPageNumber(),
        pageable.getPageSize());
    Page<ItemResponse> response = itemService.getItemsBySellerAndStatus(sellerId, status, pageable);
    return ResponseEntity.ok(response);
  }

  /**
   * Retrieve active auctions that are ending soon, using the provided pagination.
   *
   * @param pageable pagination information (defaults to page 0, size 20)
   * @return a page of ItemResponse objects representing active auctions ending soon
   */
  @GetMapping("/active/ending-soon")
  public ResponseEntity<Page<ItemResponse>> getActiveAuctionsEndingSoon(
      @PageableDefault(page = 0, size = 20) Pageable pageable
  ) {
    log.debug("GET /api/items/active/ending-soon - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
    Page<ItemResponse> response = itemService.getActiveAuctionsEndingSoon(pageable);
    return ResponseEntity.ok(response);
  }
}