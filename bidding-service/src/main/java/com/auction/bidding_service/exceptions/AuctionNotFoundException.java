package com.auction.bidding_service.exceptions;

/**
 * Exception thrown when trying to bid on a non-existent auction item.
 * <p>
 * This can occur if: - The item ID doesn't exist in Item Service - The item was deleted - There's a
 * typo in the item ID
 * <p>
 * HTTP Status: 404 NOT FOUND
 */
public class AuctionNotFoundException extends RuntimeException {

  public AuctionNotFoundException(Long itemId) {
    super("Auction with ID " + itemId + " not found");
  }

  public AuctionNotFoundException(String message) {
    super(message);
  }
}
