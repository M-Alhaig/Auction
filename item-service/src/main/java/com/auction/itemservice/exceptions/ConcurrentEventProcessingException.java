package com.auction.itemservice.exceptions;

/**
 * Exception thrown when an event is already being processed by another instance or thread.
 *
 * <p>This exception triggers RabbitMQ retry behavior, allowing the message to be redelivered
 * after a delay when the concurrent processing has completed.
 *
 * <p><strong>Retry Strategy:</strong> Transient error - message will be retried up to 3 times
 * with exponential backoff (100ms → 150ms → 225ms).
 *
 * <p><strong>Use Case:</strong> Thrown when the three-state idempotency check detects
 * an event is currently in "processing" state, indicating another consumer is handling it.
 */
public class ConcurrentEventProcessingException extends RuntimeException {

  /**
   * Constructs a new ConcurrentEventProcessingException with the specified detail message.
   *
   * @param message the detail message explaining which event is being processed concurrently
   */
  public ConcurrentEventProcessingException(String message) {
    super(message);
  }
}
