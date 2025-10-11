package com.auction.item_service.events;

import com.auction.item_service.exceptions.EventPublishException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 *
 * RabbitMQ implementation of EventPublisher. Uses Spring AMQP RabbitTemplate to publish events to a
 * topic exchange.
 *
 * <p>Exchange Strategy: - Single topic exchange: "auction-events" - Routing key pattern:
 * "item.{event-type}" (e.g., "item.auction-started") - Consumers bind queues with wildcard routing
 * keys (e.g., "item.*", "item.auction-started")
 *
 * <p>Migration Path to SQS: 1. Create SQSEventPublisher implementing EventPublisher 2. Remove @Primary
 * annotation from this class 3. Add @Primary to SQSEventPublisher 4. No changes needed in
 * ItemLifecycleServiceImpl
 *
 * <p>Thread Safety: RabbitTemplate is thread-safe, this class is safe for concurrent use.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper = createObjectMapper();

  private static final String EXCHANGE_NAME = "auction-events";
  private static final String ROUTING_KEY_PREFIX = "item.";

  @Override
  public <T> void publish(T event) {
    String routingKey = buildRoutingKey(event.getClass().getSimpleName());
    publish(event, routingKey);
  }


  @Override
  public <T> void publish(T event, String routingKey) {
    try {
      String eventJson = objectMapper.writeValueAsString(event);

      log.debug("Publishing event - exchange: {}, routingKey: {}, event: {}", EXCHANGE_NAME,
          routingKey, event.getClass().getSimpleName());

      rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, eventJson);

      log.info("Event published successfully - type: {}, routingKey: {}",
          event.getClass().getSimpleName(), routingKey);

    } catch (JsonProcessingException e) {
      log.error("Failed to serialize event - type: {}, error: {}", event.getClass().getSimpleName(),
          e.getMessage(), e);
      throw new EventPublishException(
          "Event serialization failed: " + event.getClass().getSimpleName(), e);
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

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }
}
