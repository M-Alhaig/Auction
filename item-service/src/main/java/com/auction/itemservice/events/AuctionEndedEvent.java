package com.auction.itemservice.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when an auction transitions from ACTIVE to ENDED status.
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
    LocalDateTime timestamp,
    AuctionEndedData data
) {

  /**
   * Factory method to create event with auto-generated metadata.
   *
   * @param itemId     the auction item ID
   * @param sellerId   the seller's user ID
   * @param title      the auction title
   * @param endTime    when the auction ended
   * @param finalPrice the final winning bid (or startingPrice if no bids)
   * @param winnerId   the winning bidder's ID (null if no bids)
   * @return the constructed event ready for publishing
   */
  public static AuctionEndedEvent create(
      Long itemId,
      UUID sellerId,
      String title,
      LocalDateTime endTime,
      BigDecimal finalPrice,
      UUID winnerId
  ) {
    return new AuctionEndedEvent(
        UUID.randomUUID().toString(),   // Auto-generate eventId
        "AuctionEndedEvent",            // Event type for routing
        LocalDateTime.now(),            // Current timestamp
        new AuctionEndedData(itemId, sellerId, title, endTime, finalPrice, winnerId)
    );
  }
}

/**
 * Payload data for AuctionEndedEvent.
 */
record AuctionEndedData(
    Long itemId,
    UUID sellerId,
    String title,
    LocalDateTime endTime,
    BigDecimal finalPrice,
    UUID winnerId  // Nullable - will be null if no bids were placed
) {
}
