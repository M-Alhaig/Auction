package com.auction.userservice.exceptions;

/**
 * Exception thrown when event publishing fails.
 */
public class EventPublishException extends RuntimeException {

  public EventPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
