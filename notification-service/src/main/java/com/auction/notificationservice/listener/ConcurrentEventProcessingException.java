package com.auction.notificationservice.listener;

/**
 * Exception thrown when an event is already being processed by another instance.
 *
 * <p>This is a transient error - RabbitMQ will retry the message after the backoff period.
 * The retry mechanism will eventually succeed once the other instance completes processing.
 */
public class ConcurrentEventProcessingException extends RuntimeException {

  public ConcurrentEventProcessingException(String message) {
    super(message);
  }
}
