package com.auction.biddingservice.exceptions;

/**
 * Exception thrown when trying to bid on a non-existent auction item.
 * <p>
 * This can occur if: - The item ID doesn't exist in Item Service - The item was deleted - There's a
 * typo in the item ID
 * <p>
 * HTTP Status: 404 NOT FOUND
 */
public class AuctionNotFoundException extends RuntimeException {

  /**
   * Constructs an exception indicating the auction with the specified ID could not be found.
   *
   * The exception message will be "Auction with ID {itemId} not found".
   *
   * @param itemId the identifier of the auction item that was not found
   */
  public AuctionNotFoundException(Long itemId) {
    super("Auction with ID " + itemId + " not found");
  }

  /**
   * Create an AuctionNotFoundException with a custom detail message.
   *
   * @param message the detail message describing the reason the auction was not found
   */
  public AuctionNotFoundException(String message) {
    super(message);
  }
}