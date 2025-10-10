package com.auction.item_service.controllers;

import com.auction.item_service.dto.CreateItemRequest;
import com.auction.item_service.dto.ItemResponse;
import com.auction.item_service.dto.UpdateItemRequest;
import com.auction.item_service.models.ItemStatus;
import com.auction.item_service.services.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /**
     * Create a new auction item.
     *
     * TODO: Replace @RequestHeader with @AuthenticationPrincipal once Spring Security is configured.
     *       Current approach uses X-Auth-Id header for testing only (NOT secure for production).
     *       Production: @AuthenticationPrincipal UUID sellerId
     *
     * @param request the item creation request
     * @param sellerId the authenticated seller ID (from header for testing, JWT in production)
     * @return 201 Created with the created item
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
     * Update an existing auction item (PENDING items only).
     *
     * TODO: Replace @RequestHeader with @AuthenticationPrincipal once Spring Security is configured.
     *       Current approach uses X-Auth-Id header for testing only (NOT secure for production).
     *       Production: @AuthenticationPrincipal UUID userId
     *
     * @param id the item ID
     * @param request the partial update request
     * @param userId the authenticated user ID (from header for testing, JWT in production)
     * @return 200 OK with updated item
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
     * Delete an auction item (PENDING items only).
     *
     * TODO: Replace @RequestHeader with @AuthenticationPrincipal once Spring Security is configured.
     *       Current approach uses X-Auth-Id header for testing only (NOT secure for production).
     *       Production: @AuthenticationPrincipal UUID userId
     *
     * @param id the item ID
     * @param userId the authenticated user ID (from header for testing, JWT in production)
     * @return 204 No Content
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

    @GetMapping("/{id}")
    public ResponseEntity<ItemResponse> getItemById(@PathVariable Long id) {
        log.debug("GET /api/items/{}", id);
        ItemResponse response = itemService.getItemById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ItemResponse>> getAllItems(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("GET /api/items - page: {}, size: {}", page, size);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ItemResponse> response = itemService.getAllItems(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<ItemResponse>> getItemsByStatus(
            @PathVariable ItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
            ) {
        log.debug("GET /api/items/status/{} - page: {}, size: {}", status, page, size);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ItemResponse> response = itemService.getItemsByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Page<ItemResponse>> getItemsBySeller(
            @PathVariable UUID sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("GET /api/items/seller/{} - page: {}, size: {}", sellerId, page, size);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ItemResponse> response = itemService.getItemsBySeller(sellerId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/seller/{sellerId}/status/{status}")
    public ResponseEntity<Page<ItemResponse>> getItemsBySellerAndStatus(
            @PathVariable UUID sellerId,
            @PathVariable ItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("GET /api/items/seller/{}/status/{} - page: {}, size: {}", sellerId, status, page, size);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ItemResponse> response = itemService.getItemsBySellerAndStatus(sellerId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active/ending-soon")
    public ResponseEntity<Page<ItemResponse>> getActiveAuctionsEndingSoon(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("GET /api/items/active/ending-soon - page: {}, size: {}", page, size);
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ItemResponse> response = itemService.getActiveAuctionsEndingSoon(pageable);
        return ResponseEntity.ok(response);
    }
}
