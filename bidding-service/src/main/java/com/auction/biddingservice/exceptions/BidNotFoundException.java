package com.auction.biddingservice.exceptions;

/**
 * Exception thrown when a specific bid cannot be found.
 * <p>
 * Used in queries where a bid ID is provided but doesn't exist.
 * <p>
 * HTTP Status: 404 NOT FOUND
 */
public class BidNotFoundException extends RuntimeException {

  /**
   * Creates an exception indicating a bid with the given ID was not found.
   *
   * <p>The exception message will be "Bid with ID {bidId} not found".</p>
   *
   * @param bidId the ID of the bid that could not be found
   */
  public BidNotFoundException(Long bidId) {
    super("Bid with ID " + bidId + " not found");
  }

  /**
   * Create a BidNotFoundException with a custom detail message.
   *
   * @param message the detail message describing the not-found condition
   */
  public BidNotFoundException(String message) {
    super(message);
  }
}