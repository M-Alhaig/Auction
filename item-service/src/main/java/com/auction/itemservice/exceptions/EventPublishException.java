package com.auction.itemservice.exceptions;

/**
 * Exception thrown when event publishing fails after all retry attempts. This is a runtime
 * exception to avoid forcing try-catch in business logic.
 * <p>
 * Retry Strategy: - Implementations should handle transient failures with exponential backoff -
 * This exception is only thrown after final retry failure
 * <p>
 * Handling Strategy: - Log the error with full context - Consider dead-letter queue (DLQ) for
 * failed events - Alert monitoring systems for investigation
 */
public class EventPublishException extends RuntimeException {

  /**
   * Constructs an EventPublishException with the specified detail message.
   *
   * @param message the detail message describing the publish failure
   */
  public EventPublishException(String message) {
    super(message);
  }

  /**
   * Creates an EventPublishException with a detail message and cause.
   *
   * @param message detail message describing the publish failure
   * @param cause the underlying cause of the failure; may be null
   */
  public EventPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}