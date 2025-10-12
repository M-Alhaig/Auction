package com.auction.itemservice.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    LocalDateTime timestamp,
    AuctionStartedData data
) {

  /**
   * Create an AuctionStartedEvent populated with generated metadata and the provided auction details.
   *
   * @param itemId        the auction item identifier
   * @param sellerId      the seller's UUID
   * @param title         the auction title
   * @param startTime     the auction start time
   * @param startingPrice the initial bid price for the auction
   * @return the constructed AuctionStartedEvent with generated eventId, eventType "AuctionStartedEvent", current timestamp, and payload data
   */
  public static AuctionStartedEvent create(
      Long itemId,
      UUID sellerId,
      String title,
      LocalDateTime startTime,
      BigDecimal startingPrice
  ) {
    return new AuctionStartedEvent(
        UUID.randomUUID().toString(),   // Auto-generate eventId
        "AuctionStartedEvent",          // Event type for routing
        LocalDateTime.now(),            // Current timestamp
        new AuctionStartedData(itemId, sellerId, title, startTime, startingPrice)
    );
  }

  /**
   * Payload data for AuctionStartedEvent.
   */
  public record AuctionStartedData(
      Long itemId,
      UUID sellerId,
      String title,
      LocalDateTime startTime,
      BigDecimal startingPrice
  ) {
  }
}