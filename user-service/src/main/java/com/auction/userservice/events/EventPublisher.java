package com.auction.userservice.events;

/**
 * Event publisher abstraction for messaging infrastructure.
 * Decouples business logic from specific broker implementations.
 */
public interface EventPublisher {

  /**
   * Publish an event with routing key derived from event class name.
   *
   * @param event the event object to publish
   */
  <T> void publish(T event);

  /**
   * Publish an event with explicit routing key.
   *
   * @param event the event object to publish
   * @param routingKey the routing key for message routing
   */
  <T> void publish(T event, String routingKey);
}
