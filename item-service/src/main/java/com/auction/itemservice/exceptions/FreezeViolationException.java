package com.auction.itemservice.exceptions;

import java.time.Instant;

/**
 * Exception thrown when attempting to modify auction times within the freeze period.
 *
 * <p>The freeze period prevents sellers from changing startTime or endTime within 24 hours
 * of the auction's scheduled start. This ensures fairness by preventing last-minute rule
 * changes after bidders have made decisions based on the published schedule.
 *
 * <p>Business Rule: Once an auction is within 24 hours of starting, its timing cannot be
 * modified to maintain trust and predictability for bidders.
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

  /**
   * Creates a FreezeViolationException with a standardized message for auction time modification
   * attempts within the freeze period.
   *
   * @param itemId the ID of the item whose times cannot be modified
   * @param startTime the auction's scheduled start time
   * @param freezeStartsAt the instant when the freeze period begins (24 hours before start)
   * @return a FreezeViolationException with detailed context
   */
  public static FreezeViolationException forTimeModification(
      Long itemId, Instant startTime, Instant freezeStartsAt) {
    return new FreezeViolationException(
        String.format(
            "Cannot modify auction times for item %d. Auction is within 24-hour freeze period. "
                + "Start time: %s, Freeze began: %s. "
                + "Please contact support if you need to make changes.",
            itemId, startTime, freezeStartsAt));
  }
}
