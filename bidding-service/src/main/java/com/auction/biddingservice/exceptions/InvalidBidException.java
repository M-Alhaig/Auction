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

  public InvalidBidException(String message) {
    super(message);
  }

  public InvalidBidException(String message, Throwable cause) {
    super(message, cause);
  }
}
