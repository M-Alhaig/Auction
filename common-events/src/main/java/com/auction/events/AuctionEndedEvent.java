package com.auction.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when an auction transitions from ACTIVE to ENDED status.
 *
 * <p>Publisher: Item Service
 *
 * <p>Consumers:
 * - Bidding Service: Stop accepting bids for this item
 * - Notification Service: Notify winner and seller
 * - Payment Service: Initiate payment processing (future)
 * - Analytics Service: Record final auction stats (future)
 *
 * <p>Event Envelope Pattern:
 * - eventId: UUID for idempotency (deduplication)
 * - eventType: "AuctionEndedEvent" for routing/filtering
 * - timestamp: When the event was published
 * - data: The auction results payload
 *
 * <p>Routing Key Pattern: "item.auction-ended"
 */
public record AuctionEndedEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    AuctionEndedData data
) {

  /**
   * Factory method to create event with auto-generated metadata.
   *
   * @param itemId     the auction item ID
   * @param sellerId   the seller's user ID
   * @param title      the auction title
   * @param endTime    when the auction ended (UTC)
   * @param finalPrice the final winning bid (or startingPrice if no bids)
   * @param winnerId   the winning bidder's ID (null if no bids)
   * @return the constructed event ready for publishing with UTC timestamps
   */
  public static AuctionEndedEvent create(
      Long itemId,
      UUID sellerId,
      String title,
      Instant endTime,
      BigDecimal finalPrice,
      UUID winnerId
  ) {
    return new AuctionEndedEvent(
        UUID.randomUUID().toString(),   // Auto-generate eventId
        "AuctionEndedEvent",            // Event type for routing
        Instant.now(),                  // Current UTC timestamp
        new AuctionEndedData(itemId, sellerId, title, endTime, finalPrice, winnerId)
    );
  }

  /**
   * Payload data for AuctionEndedEvent.
   */
  public record AuctionEndedData(
      Long itemId,
      UUID sellerId,
      String title,
      Instant endTime,
      BigDecimal finalPrice,
      UUID winnerId  // Nullable - will be null if no bids were placed
  ) {
  }
}
