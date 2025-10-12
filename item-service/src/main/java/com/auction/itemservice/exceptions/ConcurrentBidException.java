package com.auction.itemservice.exceptions;

/**
 * Exception thrown when a price update fails due to concurrent modification. Suggests that the
 * client should retry the operation.
 */
public class ConcurrentBidException extends RuntimeException {

  public ConcurrentBidException(String message) {
    super(message);
  }
}
