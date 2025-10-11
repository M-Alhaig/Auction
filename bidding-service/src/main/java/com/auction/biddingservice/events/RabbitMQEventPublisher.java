package com.auction.biddingservice.events;

import com.auction.biddingservice.exceptions.EventPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ implementation of EventPublisher. Uses Spring AMQP RabbitTemplate to publish events to a
 * topic exchange.
 * <p>
 * Exchange Strategy:
 * - Single topic exchange: "auction-events" (shared with all services)
 * - Routing key pattern: "bidding.{event-type}" (e.g., "bidding.bid-placed")
 * - Consumers bind queues with wildcard routing keys (e.g., "bidding.*", "bidding.bid-placed")
 * <p>
 * Routing Key Examples:
 * - BidPlacedEvent → "bidding.bid-placed"
 * - UserOutbidEvent → "bidding.user-outbid"
 * <p>
 * Serialization:
 * - RabbitTemplate is configured with Jackson2JsonMessageConverter in RabbitMQConfig
 * - Event objects are automatically serialized to JSON
 * - LocalDateTime fields are handled by JavaTimeModule registered in the converter
 * <p>
 * Migration Path to SQS:
 * 1. Create SQSEventPublisher implementing EventPublisher
 * 2. Remove @Primary annotation from this class
 * 3. Add @Primary to SQSEventPublisher
 * 4. No changes needed in BidServiceImpl
 * <p>
 * Thread Safety: RabbitTemplate is thread-safe, this class is safe for concurrent use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Primary
public class RabbitMQEventPublisher implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;

  private static final String EXCHANGE_NAME = "auction-events";
  private static final String ROUTING_KEY_PREFIX = "bidding.";

  @Override
  public <T> void publish(T event) {
    String routingKey = buildRoutingKey(event.getClass().getSimpleName());
    publish(event, routingKey);
  }

  @Override
  public <T> void publish(T event, String routingKey) {
    try {
      log.debug("Publishing event - exchange: {}, routingKey: {}, event: {}", EXCHANGE_NAME,
          routingKey, event.getClass().getSimpleName());

      // Pass event object directly - Jackson2JsonMessageConverter handles serialization
      rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, event);

      log.info("Event published successfully - type: {}, routingKey: {}",
          event.getClass().getSimpleName(), routingKey);

    } catch (Exception e) {
      log.error("Failed to publish event - type: {}, routingKey: {}, error: {}",
          event.getClass().getSimpleName(), routingKey, e.getMessage(), e);
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
