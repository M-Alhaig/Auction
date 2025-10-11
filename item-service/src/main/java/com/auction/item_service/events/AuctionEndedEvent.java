package com.auction.item_service.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when an auction transitions from ACTIVE to ENDED status. Consumed by: Bidding
 * Service (stop accepting bids), Notification Service (notify winner/seller), Payment Service
 * (initiate payment), Analytics Service (record final stats).
 * <p>
 * Event Envelope Pattern: - eventId: UUID for idempotency (deduplication) - eventType:
 * "AuctionEndedEvent" for routing/filtering - timestamp: ISO-8601 timestamp when event was
 * published - Payload fields: auction results
 */
public record AuctionEndedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime timestamp,

    // Auction data
    Long itemId,
    UUID sellerId,
    String title,
    LocalDateTime endTime,
    BigDecimal finalPrice,
    UUID winnerId  // Nullable - will be null if no bids were placed
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
  public static AuctionEndedEvent of(
      Long itemId,
      UUID sellerId,
      String title,
      LocalDateTime endTime,
      BigDecimal finalPrice,
      UUID winnerId
  ) {
    return new AuctionEndedEvent(
        UUID.randomUUID(),              // Auto-generate eventId
        "AuctionEndedEvent",            // Event type for routing
        LocalDateTime.now(),            // Current timestamp
        itemId,
        sellerId,
        title,
        endTime,
        finalPrice,
        winnerId
    );
  }
}
