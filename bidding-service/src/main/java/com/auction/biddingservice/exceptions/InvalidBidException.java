package com.auction.biddingservice.exceptions;

/**
 * Exception thrown when a bid violates business rules.
 * <p>
 * Common scenarios: - Bid amount is not higher than current highest bid - Auction is not in ACTIVE
 * status - User trying to bid on their own auction - Bid amount is invalid (negative, too large,
 * etc.)
 * <p>
 * HTTP Status: 400 BAD REQUEST
 */
public class InvalidBidException extends RuntimeException {

  /**
   * Creates an InvalidBidException with the specified detail message describing why the bid is invalid.
   *
   * @param message a human-readable detail explaining which business rule the bid violated
   */
  public InvalidBidException(String message) {
    super(message);
  }

  /**
   * Constructs an InvalidBidException with the specified detail message and cause.
   *
   * @param message the detail message explaining why the bid is invalid
   * @param cause the underlying cause of this exception, or {@code null} if none
   */
  public InvalidBidException(String message, Throwable cause) {
    super(message, cause);
  }
}