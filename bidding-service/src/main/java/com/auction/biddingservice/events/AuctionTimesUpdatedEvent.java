package com.auction.biddingservice.events;

import java.time.Instant;

/**
 * Event consumed when an auction's startTime or endTime is modified in Item Service.
 *
 * <p>Purpose: Invalidate cached auction metadata in Bidding Service to prevent
 * stale time-based validation (e.g., rejecting valid bids after auction extension).
 *
 * <p>Cache Invalidation Strategy:
 * When this event is received, delete Redis key "auction:metadata:{itemId}"
 * to force fresh data fetch on next bid attempt.
 *
 * <p>Routing Key: "item.auction-times-updated"
 */
public record AuctionTimesUpdatedEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    AuctionTimesUpdatedData data
) {

  /**
   * Payload data for AuctionTimesUpdatedEvent.
   *
   * <p>Includes both old and new values for auditing, though only itemId
   * is strictly required for cache invalidation.
   */
  public record AuctionTimesUpdatedData(
      Long itemId,
      Instant oldStartTime,
      Instant newStartTime,
      Instant oldEndTime,
      Instant newEndTime,
      String status  // ItemStatus as String for loose coupling
  ) {
  }
}
