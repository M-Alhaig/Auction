package com.auction.itemservice.exceptions;

/**
 * Exception thrown when a price update fails due to concurrent modification. Suggests that the
 * client should retry the operation.
 */
public class ConcurrentBidException extends RuntimeException {

  /**
   * Constructs a ConcurrentBidException with the specified detail message.
   *
   * @param message detail message describing the concurrent modification that caused the price update to fail; may include guidance that the client should retry
   */
  public ConcurrentBidException(String message) {
    super(message);
  }
}