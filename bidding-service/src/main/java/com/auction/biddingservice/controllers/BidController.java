package com.auction.biddingservice.controllers;

import com.auction.biddingservice.dto.BidResponse;
import com.auction.biddingservice.dto.PlaceBidRequest;
import com.auction.biddingservice.services.BidService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for bid management operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/bids - Place a new bid</li>
 *   <li>GET /api/bids/items/{itemId} - Get paginated bid history for an item</li>
 *   <li>GET /api/bids/items/{itemId}/highest - Get current highest bid</li>
 *   <li>GET /api/bids/users/{bidderId} - Get paginated bid history for a user</li>
 *   <li>GET /api/bids/items/{itemId}/users/{bidderId} - Get user's bids on specific item</li>
 *   <li>GET /api/bids/items/{itemId}/count - Count total bids for an item</li>
 *   <li>GET /api/bids/users/{bidderId}/items - Get distinct items user has bid on</li>
 * </ul>
 *
 * <p>Authentication: Currently uses X-Auth-Id header for authenticated user ID. Will be replaced
 * with JWT token extraction via @AuthenticationPrincipal once User Service is integrated.
 *
 * <p>Error Handling: All exceptions are handled by {@link com.auction.biddingservice.exceptions.GlobalExceptionHandler}
 */
@Slf4j
@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
public class BidController {

  private final BidService bidService;

  /**
   * Place a new bid on an auction item.
   *
   * <p>Concurrency: Uses Redis distributed locking to prevent race conditions when multiple users
   * bid simultaneously.
   *
   * @param request the bid details (itemId, bidAmount)
   * @param authId  the authenticated user's ID from X-Auth-Id header (temporary until JWT)
   * @return the created bid with isCurrentHighest=true
   */
  @PostMapping
  public ResponseEntity<BidResponse> placeBid(
      @Valid @RequestBody PlaceBidRequest request,
      @RequestHeader("X-Auth-Id") String authId) {

    UUID bidderId = UUID.fromString(authId);
    log.info("POST /api/bids - bidderId: {}, itemId: {}, amount: {}",
        bidderId, request.itemId(), request.bidAmount());

    BidResponse response = bidService.placeBid(request, bidderId);

    log.info("Bid placed successfully - bidId: {}, itemId: {}, bidderId: {}, amount: {}",
        response.id(), response.itemId(), response.bidderId(), response.bidAmount());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Get paginated bid history for a specific auction item.
   *
   * <p>Default sort: timestamp descending (newest first). Caller can override via query params.
   *
   * @param itemId   the item ID to query
   * @param pageable pagination parameters (page, size, sort)
   * @return page of bids with isCurrentHighest flag
   */
  @GetMapping("/items/{itemId}")
  public ResponseEntity<Page<BidResponse>> getBidHistory(
      @PathVariable Long itemId,
      @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
      Pageable pageable) {

    log.debug("GET /api/bids/items/{} - page: {}, size: {}",
        itemId, pageable.getPageNumber(), pageable.getPageSize());

    Page<BidResponse> response = bidService.getBidHistory(itemId, pageable);

    log.debug("Returning {} bids for itemId: {}", response.getNumberOfElements(), itemId);

    return ResponseEntity.ok(response);
  }

  /**
   * Get the current highest bid for an auction item.
   *
   * @param itemId the item ID to query
   * @return the highest bid, or 404 if no bids exist
   */
  @GetMapping("/items/{itemId}/highest")
  public ResponseEntity<BidResponse> getHighestBid(@PathVariable Long itemId) {

    log.debug("GET /api/bids/items/{}/highest", itemId);

    BidResponse response = bidService.getHighestBid(itemId);

    if (response == null) {
      log.debug("No bids found for itemId: {}", itemId);
      return ResponseEntity.notFound().build();
    }

    log.debug("Returning highest bid for itemId: {} - amount: {}", itemId, response.bidAmount());

    return ResponseEntity.ok(response);
  }

  /**
   * Get paginated bid history for a specific user across all items.
   *
   * <p>Returns simple chronological history without expensive isCurrentHighest checks. All bids are
   * marked as historical (isCurrentHighest=false) for performance.
   *
   * <p>Default sort: timestamp descending (newest first).
   *
   * @param bidderId the user's UUID
   * @param pageable pagination parameters (page, size, sort)
   * @return page of user's bids
   */
  @GetMapping("/users/{bidderId}")
  public ResponseEntity<Page<BidResponse>> getUserBids(
      @PathVariable UUID bidderId,
      @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
      Pageable pageable) {

    log.debug("GET /api/bids/users/{} - page: {}, size: {}",
        bidderId, pageable.getPageNumber(), pageable.getPageSize());

    Page<BidResponse> response = bidService.getUserBids(bidderId, pageable);

    log.debug("Returning {} bids for bidderId: {}", response.getNumberOfElements(), bidderId);

    return ResponseEntity.ok(response);
  }

  /**
   * Get paginated bids placed by a specific user on a specific item.
   *
   * <p>Sets isCurrentHighest flag accurately for this specific item.
   *
   * <p>Default sort: timestamp descending (newest first).
   *
   * @param itemId   the item ID to query
   * @param bidderId the user's UUID
   * @param pageable pagination parameters (page, size, sort)
   * @return page of user's bids on this item
   */
  @GetMapping("/items/{itemId}/users/{bidderId}")
  public ResponseEntity<Page<BidResponse>> getUserBidsForItem(
      @PathVariable Long itemId,
      @PathVariable UUID bidderId,
      @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
      Pageable pageable) {

    log.debug("GET /api/bids/items/{}/users/{} - page: {}, size: {}",
        itemId, bidderId, pageable.getPageNumber(), pageable.getPageSize());

    Page<BidResponse> response = bidService.getUserBidsForItem(itemId, bidderId, pageable);

    log.debug("Returning {} bids for itemId: {}, bidderId: {}",
        response.getNumberOfElements(), itemId, bidderId);

    return ResponseEntity.ok(response);
  }

  /**
   * Count total bids for a specific auction item.
   *
   * <p>Useful for displaying bid activity metrics without loading full bid history.
   *
   * @param itemId the item ID to query
   * @return the bid count
   */
  @GetMapping("/items/{itemId}/count")
  public ResponseEntity<Long> countBids(@PathVariable Long itemId) {

    log.debug("GET /api/bids/items/{}/count", itemId);

    long count = bidService.countBids(itemId);

    log.debug("Returning bid count for itemId: {} - count: {}", itemId, count);

    return ResponseEntity.ok(count);
  }

  /**
   * Get all distinct item IDs that a user has bid on.
   *
   * <p>Useful for "My Active Auctions" dashboard feature.
   *
   * @param bidderId the user's UUID
   * @return list of item IDs (may be empty)
   */
  @GetMapping("/users/{bidderId}/items")
  public ResponseEntity<List<Long>> getItemsUserHasBidOn(@PathVariable UUID bidderId) {

    log.debug("GET /api/bids/users/{}/items", bidderId);

    List<Long> itemIds = bidService.getItemsUserHasBidOn(bidderId);

    log.debug("Returning {} items for bidderId: {}", itemIds.size(), bidderId);

    return ResponseEntity.ok(itemIds);
  }
}
