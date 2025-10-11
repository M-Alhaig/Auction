package com.auction.biddingservice.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a user is outbid on an auction item.
 * <p>
 * Consumers: - Notification Service: Sends push notifications / WebSocket alerts to the outbid user
 * - Lambda / SES: Sends email notification to the outbid user
 * <p>
 * Event Envelope Pattern: - eventId: Unique identifier for idempotency - eventType:
 * "UserOutbidEvent" for routing and filtering - timestamp: When the event was published - data:
 * Information about who was outbid and by how much
 * <p>
 * Routing Key Pattern: "bidding.user-outbid"
 * <p>
 * Privacy Note: - oldBidderId is included so consumers can notify the specific user - newBidderId
 * may be hidden in client-facing notifications to prevent bid sniping strategies
 */
public record UserOutbidEvent(
    String eventId,
    String eventType,
    LocalDateTime timestamp,
    UserOutbidData data
) {

  /**
   * Factory method to create a new UserOutbidEvent with auto-generated metadata.
   */
  public static UserOutbidEvent create(Long itemId, UUID oldBidderId, UUID newBidderId,
      BigDecimal newAmount) {
    return new UserOutbidEvent(
        UUID.randomUUID().toString(),
        "UserOutbidEvent",
        LocalDateTime.now(),
        new UserOutbidData(itemId, oldBidderId, newBidderId, newAmount)
    );
  }
/**
 * Payload data for UserOutbidEvent.
 */
public record UserOutbidData(
    Long itemId,
    UUID oldBidderId,    // User who was outbid
    UUID newBidderId,    // User who placed the new higher bid
    BigDecimal newAmount // The new highest bid amount
) {

}
}

