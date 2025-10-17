package com.auction.itemservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when an auction transitions from PENDING to ACTIVE status.
 *
 * <p>Consumers:
 * - Bidding Service: Enable bidding on the item
 * - Notification Service: Notify subscribers that auction has started
 *
 * <p>Event Envelope Pattern:
 * - eventId: UUID for idempotency (deduplication)
 * - eventType: "AuctionStartedEvent" for routing/filtering
 * - timestamp: When the event was published
 * - data: The auction details payload
 *
 * <p>Routing Key Pattern: "item.auction-started"
 */
public record AuctionStartedEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    AuctionStartedData data
) {

  /**
   * Create an AuctionStartedEvent populated with generated metadata and the provided auction details.
   *
   * @param itemId        the auction item identifier
   * @param sellerId      the seller's UUID
   * @param title         the auction title
   * @param startTime     the auction start time (UTC)
   * @param endTime       the auction end time (UTC) - required for dynamic cache TTL in Bidding Service
   * @param startingPrice the initial bid price for the auction
   * @return the constructed AuctionStartedEvent with generated eventId, eventType "AuctionStartedEvent", current UTC timestamp, and payload data
   */
  public static AuctionStartedEvent create(
      Long itemId,
      UUID sellerId,
      String title,
      Instant startTime,
      Instant endTime,
      BigDecimal startingPrice
  ) {
    return new AuctionStartedEvent(
        UUID.randomUUID().toString(),   // Auto-generate eventId
        "AuctionStartedEvent",          // Event type for routing
        Instant.now(),                  // Current UTC timestamp
        new AuctionStartedData(itemId, sellerId, title, startTime, endTime, startingPrice)
    );
  }

  /**
   * Payload data for AuctionStartedEvent.
   *
   * <p>endTime is included to enable Bidding Service to calculate dynamic cache TTL:
   * TTL = Duration.between(now, endTime), ensuring cache expires exactly when auction ends.
   */
  public record AuctionStartedData(
      Long itemId,
      UUID sellerId,
      String title,
      Instant startTime,
      Instant endTime,
      BigDecimal startingPrice
  ) {
  }
}