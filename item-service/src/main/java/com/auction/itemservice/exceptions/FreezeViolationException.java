package com.auction.itemservice.exceptions;

/**
 * Exception thrown when attempting to modify auction times within the freeze period.
 *
 * <p>The freeze period prevents sellers from changing startTime or endTime within 24 hours
 * of the auction's scheduled start. This ensures fairness by preventing last-minute rule
 * changes after bidders have made decisions based on the published schedule.
 *
 * <p>Business Rule: Once an auction is within 24 hours of starting, its timing cannot be
 * modified to maintain trust and predictability for bidders.
 *
 * <p>HTTP Status: 403 FORBIDDEN
 */
public class FreezeViolationException extends RuntimeException {

  /**
   * Constructs a FreezeViolationException with the specified detail message.
   *
   * @param message detail message describing the freeze violation
   */
  public FreezeViolationException(String message) {
    super(message);
  }
}
