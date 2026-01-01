package com.auction.notificationservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WebSocket payload for personal outbid alerts sent to /user/{userId}/queue/alerts.
 *
 * <p>Sent only to the user who was outbid. Does NOT reveal the new bidder's identity
 * to prevent strategic bidding behavior.
 */
public record OutbidAlertPayload(
    Long itemId,
    BigDecimal newHighestBid,
    String title,
    String message,
    Instant timestamp
) {

  /**
   * Factory method to create payload from UserOutbidEvent data.
   *
   * @param itemId    the auction item ID
   * @param newAmount the new highest bid amount
   * @return an OutbidAlertPayload with formatted title and message
   */
  public static OutbidAlertPayload fromEvent(Long itemId, BigDecimal newAmount) {
    String title = "You've been outbid!";
    String message = String.format(
        "Someone placed a higher bid of $%s on item #%d. Place a new bid to stay in the auction!",
        newAmount, itemId);
    return new OutbidAlertPayload(itemId, newAmount, title, message, Instant.now());
  }
}
