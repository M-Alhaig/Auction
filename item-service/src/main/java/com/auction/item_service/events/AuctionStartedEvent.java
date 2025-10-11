package com.auction.item_service.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when an auction transitions from PENDING to ACTIVE status. Consumed by: Bidding
 * Service (enable bidding), Notification Service (notify subscribers).
 * <p>
 * Event Envelope Pattern: - eventId: UUID for idempotency (deduplication) - eventType:
 * "AuctionStartedEvent" for routing/filtering - timestamp: ISO-8601 timestamp when event was
 * published - Payload fields: auction details
 */
public record AuctionStartedEvent(
    UUID eventId,
    String eventType,
    LocalDateTime timestamp,

    // Auction data
    Long itemId,
    UUID sellerId,
    String title,
    LocalDateTime startTime,
    BigDecimal startingPrice
) {

  /**
   * Factory method to create events with auto-generated metadata.
   *
   * @param itemId        the auction item ID
   * @param sellerId      the seller's user ID
   * @param title         the auction title
   * @param startTime     when the auction started
   * @param startingPrice the initial bid price
   * @return the constructed event ready for publishing
   */
  public static AuctionStartedEvent of(
      Long itemId,
      UUID sellerId,
      String title,
      LocalDateTime startTime,
      BigDecimal startingPrice
  ) {
    return new AuctionStartedEvent(
        UUID.randomUUID(),              // Auto-generate eventId
        "AuctionStartedEvent",          // Event type for routing
        LocalDateTime.now(),            // Current timestamp
        itemId,
        sellerId,
        title,
        startTime,
        startingPrice
    );
  }
}
