package com.auction.notificationservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload for broadcasting new bid updates to /topic/items/{itemId}.
 *
 * <p>Sent to all clients watching a specific auction item when a new bid is placed.
 *
 * <p><strong>Privacy Note:</strong> bidderId is included for display purposes.
 * In production, you may want to mask it to prevent bid sniping strategies.
 */
public record BidUpdatePayload(
    Long itemId,
    UUID bidderId,
    BigDecimal bidAmount,
    Instant bidTimestamp,
    String message
) {

  /**
   * Factory method to create payload from BidPlacedEvent data.
   *
   * @param itemId       the auction item ID
   * @param bidderId     the bidder's UUID
   * @param bidAmount    the bid amount
   * @param bidTimestamp when the bid was placed
   * @return a BidUpdatePayload with formatted message
   */
  public static BidUpdatePayload fromEvent(
      Long itemId,
      UUID bidderId,
      BigDecimal bidAmount,
      Instant bidTimestamp) {
    String message = String.format("New highest bid: $%s", bidAmount);
    return new BidUpdatePayload(itemId, bidderId, bidAmount, bidTimestamp, message);
  }
}
