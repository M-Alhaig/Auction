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
   * Factory method to create event with auto-generated metadata.
   *
   * @param itemId        the auction item ID
   * @param sellerId      the seller's user ID
   * @param title         the auction title
   * @param startTime     when the auction started
   * @param startingPrice the initial bid price
   * @return the constructed event ready for publishing
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
