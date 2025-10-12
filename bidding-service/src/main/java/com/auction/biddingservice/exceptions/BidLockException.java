package com.auction.biddingservice.exceptions;

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

  /**
   * Create a BidLockException with a detail message describing the lock acquisition failure.
   *
   * @param message human-readable description of why the Redis distributed lock could not be acquired
   */
  public BidLockException(String message) {
    super(message);
  }

  /**
   * Create a new BidLockException with the specified detail message and cause.
   *
   * @param message detail message describing the lock acquisition failure
   * @param cause   the underlying cause of this exception
   */
  public BidLockException(String message, Throwable cause) {
    super(message, cause);
  }
}