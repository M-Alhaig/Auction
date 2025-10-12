package com.auction.itemservice.events;

import com.auction.itemservice.exceptions.EventPublishException;

/**
 * Generic event publisher abstraction for messaging infrastructure. Decouples business logic from
 * specific message broker implementations (RabbitMQ, SQS/SNS, Kafka).
 *
 * <p>Implementation Strategy: - Current: RabbitMQEventPublisher (uses RabbitTemplate) - Future:
 * SQSEventPublisher (uses AWS SDK SQS/SNS clients)
 *
 * <p>Migration Path: To migrate from RabbitMQ to AWS SQS/SNS: 1. Implement SQSEventPublisher
 * implementing this interface 2. Update Spring @Primary annotation or configuration 3. No changes
 * needed in service layer code
 *
 * <p>Thread Safety: Implementations must be thread-safe (typically singleton beans).
 */
public interface EventPublisher {

  /**
 * Publish an event to the messaging infrastructure.
 *
 * <p>Routing is determined by the underlying implementation (for example, exchange/topic or
 * topic/filters).
 *
 * @param event the event object to publish; must be serializable to JSON
 * @param <T>   the event type (typically a record or POJO)
 * @throws EventPublishException if publishing fails after retries
 */
  <T> void publish(T event);

  /**
 * Publish an event with an explicit routing hint for broker-directed routing.
 *
 * <p>The routing hint is used by the underlying implementation to determine delivery (for example,
 * as the RabbitMQ routing key or as an SQS/SNS message attribute). The event must be serializable to JSON.
 *
 * @param <T>        the event type
 * @param event      the event object to publish
 * @param routingKey routing hint interpreted by the underlying broker (e.g., RabbitMQ routing key or SQS/SNS message attribute)
 * @throws EventPublishException if publishing fails after retries
 */
  <T> void publish(T event, String routingKey);
}