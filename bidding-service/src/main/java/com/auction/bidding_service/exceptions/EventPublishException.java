package com.auction.bidding_service.exceptions;

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

  public EventPublishException(String message) {
    super(message);
  }

  public EventPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
