package com.auction.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when an auction's startTime or endTime is modified.
 *
 * <p>Publisher: Item Service
 *
 * <p>Purpose: Invalidate cached auction metadata in Bidding Service to prevent
 * stale time-based validation (e.g., rejecting valid bids after auction extension).
 *
 * <p>Consumers:
 * - Bidding Service: Invalidate Redis cache key "auction:metadata:{itemId}"
 * - Notification Service (future): Notify bidders of schedule changes
 *
 * <p>Event Envelope Pattern:
 * - eventId: UUID for idempotency (deduplication)
 * - eventType: "AuctionTimesUpdatedEvent" for routing/filtering
 * - timestamp: When the event was published
 * - data: The old and new timing details
 *
 * <p>Routing Key Pattern: "item.auction-times-updated"
 *
 * <p>Business Context:
 * This event is published when sellers modify auction times OUTSIDE the freeze period.
 * Within 24 hours of start, time modifications are blocked by FreezeViolationException.
 */
public record AuctionTimesUpdatedEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    AuctionTimesUpdatedData data
) {

  /**
   * Create an AuctionTimesUpdatedEvent with generated metadata and the provided timing changes.
   *
   * @param itemId the auction item identifier
   * @param oldStartTime the previous start time (before update)
   * @param newStartTime the new start time (after update)
   * @param oldEndTime the previous end time (before update)
   * @param newEndTime the new end time (after update)
   * @param status the current item status as String (PENDING, ACTIVE, ENDED)
   * @return the constructed AuctionTimesUpdatedEvent with generated eventId, timestamp, and payload
   */
  public static AuctionTimesUpdatedEvent create(
      Long itemId,
      Instant oldStartTime,
      Instant newStartTime,
      Instant oldEndTime,
      Instant newEndTime,
      String status
  ) {
    return new AuctionTimesUpdatedEvent(
        UUID.randomUUID().toString(),      // Auto-generate eventId for idempotency
        "AuctionTimesUpdatedEvent",        // Event type for routing
        Instant.now(),                     // Current UTC timestamp
        new AuctionTimesUpdatedData(itemId, oldStartTime, newStartTime, oldEndTime, newEndTime, status)
    );
  }

  /**
   * Payload data for AuctionTimesUpdatedEvent.
   *
   * <p>Includes both old and new values to enable:
   * - Auditing/logging of what changed
   * - Conditional logic in consumers (e.g., only invalidate cache if endTime changed)
   * - Rollback scenarios (if needed)
   *
   * <p>Status is stored as String instead of enum to avoid coupling common-events
   * to service-specific domain models.
   */
  public record AuctionTimesUpdatedData(
      Long itemId,
      Instant oldStartTime,
      Instant newStartTime,
      Instant oldEndTime,
      Instant newEndTime,
      String status  // ItemStatus as String for loose coupling (PENDING, ACTIVE, ENDED)
  ) {
  }
}
