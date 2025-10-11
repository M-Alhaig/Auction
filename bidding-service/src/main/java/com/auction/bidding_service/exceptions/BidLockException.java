package com.auction.bidding_service.exceptions;

/**
 * Exception thrown when Redis distributed lock cannot be acquired.
 * <p>
 * This indicates that another bid is currently being processed for the same item. The client should
 * retry the request after a short delay.
 * <p>
 * Common scenario: - Multiple users trying to bid on the same item simultaneously - Redis lock is
 * held by another service instance
 * <p>
 * HTTP Status: 409 CONFLICT (with Retry-After header suggestion)
 */
public class BidLockException extends RuntimeException {

  public BidLockException(String message) {
    super(message);
  }

  public BidLockException(String message, Throwable cause) {
    super(message, cause);
  }
}
