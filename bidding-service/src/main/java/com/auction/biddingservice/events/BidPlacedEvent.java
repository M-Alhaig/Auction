package com.auction.biddingservice.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a bid is successfully placed on an auction item.
 * <p>
 * Consumers: - Item Service: Updates currentPrice for the item - Notification Service: Pushes
 * real-time updates to WebSocket clients
 * <p>
 * Event Envelope Pattern: - eventId: Unique identifier for idempotency (prevents duplicate
 * processing) - eventType: "BidPlacedEvent" for routing and filtering - timestamp: When the event
 * was published - data: The actual bid information
 * <p>
 * Routing Key Pattern: "bidding.bid-placed"
 */
public record BidPlacedEvent(
    String eventId,
    String eventType,
    LocalDateTime timestamp,
    BidPlacedData data
) {

  /**
   * Factory method to create a new BidPlacedEvent with auto-generated metadata.
   */
  public static BidPlacedEvent create(Long itemId, UUID bidderId, BigDecimal bidAmount,
      LocalDateTime bidTimestamp) {
    return new BidPlacedEvent(
        UUID.randomUUID().toString(),
        "BidPlacedEvent",
        LocalDateTime.now(),
        new BidPlacedData(itemId, bidderId, bidAmount, bidTimestamp)
    );
  }
}

/**
 * Payload data for BidPlacedEvent.
 */
record BidPlacedData(
    Long itemId,
    UUID bidderId,
    BigDecimal bidAmount,
    LocalDateTime bidTimestamp
) {

}
