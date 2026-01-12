package com.auction.userservice.events;

import com.auction.userservice.exceptions.EventPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ implementation of EventPublisher.
 *
 * <p>Routing key pattern: "user.{event-type}"
 * Examples: "user.registered", "user.updated"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;

  private static final String ROUTING_KEY_PREFIX = "user.";

  @Override
  public <T> void publish(T event) {
    String routingKey = buildRoutingKey(event.getClass().getSimpleName());
    publish(event, routingKey);
  }

  @Override
  public <T> void publish(T event, String routingKey) {
    try {
      log.debug("Publishing event - routingKey: {}, type: {}",
          routingKey, event.getClass().getSimpleName());

      rabbitTemplate.convertAndSend(routingKey, event);

      log.info("Event published - type: {}, routingKey: {}",
          event.getClass().getSimpleName(), routingKey);

    } catch (Exception e) {
      log.error("Failed to publish event - type: {}, error: {}",
          event.getClass().getSimpleName(), e.getMessage(), e);
      throw new EventPublishException(
          "Event publishing failed: " + event.getClass().getSimpleName(), e);
    }
  }

  private String buildRoutingKey(String eventClassName) {
    String eventName = eventClassName.replace("Event", "");
    String kebabCase = eventName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    return ROUTING_KEY_PREFIX + kebabCase;
  }
}
