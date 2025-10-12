package com.auction.biddingservice.exceptions;

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
   * Creates an EventPublishException with a descriptive message.
   *
   * This exception signals that publishing an event ultimately failed (for example, after retry attempts).
   *
   * @param message detailed message describing the publish failure
   */
  public EventPublishException(String message) {
    super(message);
  }

  /**
   * Constructs a new EventPublishException with the specified detail message and cause.
   *
   * @param message descriptive detail message explaining the publishing failure
   * @param cause the underlying cause of the failure; may be {@code null}
   */
  public EventPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}