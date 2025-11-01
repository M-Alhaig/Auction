package com.auction.itemservice.controllers;

import com.auction.itemservice.dto.ItemResponse;
import com.auction.itemservice.services.ItemLifecycleService;
import com.auction.itemservice.services.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for manual auction lifecycle management endpoints.
 *
 * <p><strong>IMPORTANT:</strong> This entire controller is disabled in production environments.
 * The class-level {@code @Profile("!production")} annotation ensures that this bean is NOT created
 * when the active profile is "production", which means ALL endpoints in this controller will be
 * unavailable (returning 404) in production.
 *
 * <p><strong>Availability:</strong>
 * <ul>
 *   <li>✅ Available: dev, test, staging, or no profile (default)</li>
 *   <li>❌ Disabled: production profile</li>
 * </ul>
 *
 * <p><strong>Purpose:</strong> Manual auction lifecycle control for testing and development.
 * In production, the scheduler manages all auction lifecycle transitions automatically
 * based on startTime and endTime.
 *
 * @see ItemLifecycleService
 */
@Profile("!production")
@Slf4j
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ManualLifecycleController {

  private final ItemService itemService;
  private final ItemLifecycleService itemLifecycleService;

  /**
   * Manually start an auction (transition from PENDING to ACTIVE status).
   *
   * <p><strong>Purpose:</strong> For testing and manual auction management.
   * In production, auctions are started automatically by the scheduler when startTime is reached.
   *
   * <p><strong>Side Effects:</strong>
   * <ul>
   *   <li>Changes item status from PENDING to ACTIVE</li>
   *   <li>Publishes AuctionStartedEvent to RabbitMQ</li>
   *   <li>Bidding Service caches auction metadata (startingPrice + endTime)</li>
   * </ul>
   *
   * <p><strong>Availability:</strong> This endpoint is disabled in production environments
   * via class-level {@code @Profile("!production")} - the entire controller bean is not created.
   *
   * @param id the item identifier
   * @param userId the authenticated seller's UUID
   * @return the updated ItemResponse with ACTIVE status
   * @throws com.auction.itemservice.exceptions.ItemNotFoundException if item not found
   * @throws IllegalStateException if item is not in PENDING status
   * @throws com.auction.itemservice.exceptions.UnauthorizedException if user is not the seller
   */
  @PatchMapping("/{id}/start")
  public ResponseEntity<ItemResponse> startAuction(
      @PathVariable Long id,
      @RequestHeader("X-Auth-Id") UUID userId
  ) {
    log.info("PATCH /api/items/{}/start - Starting auction by user: {}", id, userId);

    itemLifecycleService.startAuction(id);
    ItemResponse response = itemService.getItemById(id);

    log.info("PATCH /api/items/{}/start - Auction started successfully", id);
    return ResponseEntity.ok(response);
  }

  /**
   * Manually end an auction (transition from ACTIVE to ENDED status).
   *
   * <p><strong>Purpose:</strong> For testing and manual auction management.
   * In production, auctions are ended automatically by the scheduler when endTime is reached.
   *
   * <p><strong>Side Effects:</strong>
   * <ul>
   *   <li>Changes item status from ACTIVE to ENDED</li>
   *   <li>Publishes AuctionEndedEvent to RabbitMQ</li>
   *   <li>Bidding Service marks auction as ended (rejects future bids)</li>
   * </ul>
   *
   * <p><strong>Availability:</strong> This endpoint is disabled in production environments
   * via class-level {@code @Profile("!production")} - the entire controller bean is not created.
   *
   * @param id the item identifier
   * @param userId the authenticated seller's UUID
   * @return the updated ItemResponse with ENDED status and final price
   * @throws com.auction.itemservice.exceptions.ItemNotFoundException if item not found
   * @throws IllegalStateException if item is not in ACTIVE status
   * @throws com.auction.itemservice.exceptions.UnauthorizedException if user is not the seller
   */
  @PatchMapping("/{id}/end")
  public ResponseEntity<ItemResponse> endAuction(
      @PathVariable Long id,
      @RequestHeader("X-Auth-Id") UUID userId
  ) {
    log.info("PATCH /api/items/{}/end - Ending auction by user: {}", id, userId);

    itemLifecycleService.endAuction(id);
    ItemResponse response = itemService.getItemById(id);

    log.info("PATCH /api/items/{}/end - Auction ended successfully, final price: {}", id, response.currentPrice());
    return ResponseEntity.ok(response);
  }
}
