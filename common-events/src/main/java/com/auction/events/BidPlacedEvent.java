package com.auction.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a bid is successfully placed on an auction item.
 *
 * <p>Publisher: Bidding Service
 *
 * <p>Consumers:
 * - Item Service: Updates currentPrice and winnerId
 * - Notification Service: Pushes real-time updates to WebSocket clients
 *
 * <p>Event Envelope Pattern:
 * - eventId: Unique identifier for idempotency (prevents duplicate processing)
 * - eventType: "BidPlacedEvent" for routing and filtering
 * - timestamp: When the event was published
 * - data: The actual bid information
 *
 * <p>Routing Key Pattern: "bidding.bid-placed"
 */
public record BidPlacedEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    BidPlacedData data
) {

  /**
   * Create a BidPlacedEvent containing the provided bid details.
   *
   * @param itemId      the identifier of the auction item
   * @param bidderId    the identifier of the bidder placing the bid
   * @param bidAmount   the monetary amount of the bid
   * @param bidTimestamp the time when the bid was placed (may differ from the event publish timestamp), stored in UTC
   * @return            a BidPlacedEvent whose eventId is generated and whose timestamp is set to the current UTC time; its data payload contains the provided bid details
   */
  public static BidPlacedEvent create(
      Long itemId,
      UUID bidderId,
      BigDecimal bidAmount,
      Instant bidTimestamp
  ) {
    return new BidPlacedEvent(
        UUID.randomUUID().toString(),
        "BidPlacedEvent",
        Instant.now(),
        new BidPlacedData(itemId, bidderId, bidAmount, bidTimestamp)
    );
  }

  /**
   * Payload data for BidPlacedEvent.
   */
  public record BidPlacedData(
      Long itemId,
      UUID bidderId,
      BigDecimal bidAmount,
      Instant bidTimestamp
  ) {
  }
}
