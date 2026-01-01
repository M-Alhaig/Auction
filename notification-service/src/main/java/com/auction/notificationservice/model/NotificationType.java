package com.auction.notificationservice.model;

/**
 * Enumeration of notification types for the auction platform.
 *
 * <p>Used to categorize notifications for display and filtering purposes.
 * The enum is stored as a string in the database (VARCHAR) for readability.
 */
public enum NotificationType {

  /**
   * User was outbid on an item they were winning.
   * Triggered by: UserOutbidEvent from Bidding Service.
   */
  OUTBID,

  /**
   * New highest bid placed on an item.
   * Note: Currently not persisted (WebSocket broadcast only).
   */
  NEW_BID,

  /**
   * Auction has ended.
   * Triggered by: AuctionEndedEvent from Item Service (future use).
   */
  AUCTION_ENDED,

  /**
   * User won an auction.
   * Triggered by: AuctionEndedEvent when user is the winner (future use).
   */
  AUCTION_WON
}
