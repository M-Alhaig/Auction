package com.auction.itemservice.events;

import com.auction.itemservice.exceptions.EventPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ implementation of EventPublisher. Uses Spring AMQP RabbitTemplate to publish events to a
 * topic exchange.
 *
 * <p>Exchange Strategy:
 * - Single topic exchange: "auction-events"
 * - Routing key pattern: "item.{event-type}" (e.g., "item.auction-started")
 * - Consumers bind queues with wildcard routing keys (e.g., "item.*", "item.auction-started")
 *
 * <p>Serialization:
 * - RabbitTemplate is configured with Jackson2JsonMessageConverter in RabbitMQConfig
 * - Event objects are automatically serialized to JSON
 * - LocalDateTime fields are handled by JavaTimeModule registered in the converter
 *
 * <p>Migration Path to SQS:
 * 1. Create SQSEventPublisher implementing EventPublisher
 * 2. Remove @Primary annotation from this class
 * 3. Add @Primary to SQSEventPublisher
 * 4. No changes needed in ItemLifecycleServiceImpl
 *
 * <p>Thread Safety: RabbitTemplate is thread-safe, this class is safe for concurrent use.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;

  private static final String ROUTING_KEY_PREFIX = "item.";

  /**
   * Publishes the given event to the default topic routing key derived from its class name.
   *
   * <p>The routing key is computed by removing a trailing "Event" suffix (if present), converting
   * the remaining CamelCase name to kebab-case, and prefixing it with "item.", then the event is
   * published using that routing key.
   *
   * @param event the event object to publish; its class name is used to derive the routing key
   */
  @Override
  public <T> void publish(T event) {
    String routingKey = buildRoutingKey(event.getClass().getSimpleName());
    publish(event, routingKey);
  }

  /**
   * Publish the given event to the "auction-events" topic exchange using the specified routing key.
   *
   * @param <T> the event type
   * @param event the event payload to publish; will be serialized to JSON by the configured message converter
   * @param routingKey the routing key to use (e.g., "item.auction-started")
   * @throws EventPublishException if sending the message fails
   */
  @Override
  public <T> void publish(T event, String routingKey) {
    try {
      log.debug("Publishing event - exchange: {}, routingKey: {}, event: {}", rabbitTemplate.getExchange(),
          routingKey, event.getClass().getSimpleName());

      // Pass event object directly - Jackson2JsonMessageConverter handles serialization
      rabbitTemplate.convertAndSend(routingKey, event);

      log.info("Event published successfully - type: {}, routingKey: {}",
          event.getClass().getSimpleName(), routingKey);

    } catch (Exception e) {
      log.error("Failed to publish event - type: {}, routingKey: {}, error: {}",
          event.getClass().getSimpleName(), routingKey, e.getMessage(), e);
      throw new EventPublishException(
          "Event publishing failed: " + event.getClass().getSimpleName(), e);
    }
  }

  /**
   * Builds the routing key used for publishing an event.
   *
   * <p>Removes a trailing "Event" suffix from the provided class name, converts the remaining
   * Pascal/CamelCase name to kebab-case, and prefixes it with the configured routing-key prefix.
   *
   * @param eventClassName the event's class name (for example, "AuctionStartedEvent")
   * @return the routing key, e.g. "item.auction-started"
   */
  private String buildRoutingKey(String eventClassName) {
    String eventName = eventClassName.replace("Event", "");
    String kebabCase = eventName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();

    return ROUTING_KEY_PREFIX + kebabCase;
  }
}
