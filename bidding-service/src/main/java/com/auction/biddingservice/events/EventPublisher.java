package com.auction.biddingservice.events;

import com.auction.biddingservice.exceptions.EventPublishException;

/**
 * Generic event publisher abstraction for messaging infrastructure.
 * Decouples business logic from specific message broker implementations (RabbitMQ, SQS/SNS, Kafka).
 * <p>
 * Implementation Strategy:
 * - Current: RabbitMQEventPublisher (uses RabbitTemplate)
 * - Future: SQSEventPublisher (uses AWS SDK SQS/SNS clients)
 * <p>
 * Migration Path:
 * To migrate from RabbitMQ to AWS SQS/SNS:
 * 1. Implement SQSEventPublisher implementing this interface
 * 2. Update Spring @Primary annotation or configuration
 * 3. No changes needed in service layer code
 * <p>
 * Thread Safety: Implementations must be thread-safe (typically singleton beans).
 */
public interface EventPublisher {

    /**
 * Publish an event to the configured messaging infrastructure.
 *
 * Routing and delivery semantics depend on the concrete implementation.
 *
 * @param <T>   the event type (typically a record or POJO)
 * @param event the event to publish; must be serializable to JSON
 * @throws EventPublishException if publishing fails after retries
 */
    <T> void publish(T event);

    /**
 * Publish the given event to the configured messaging infrastructure using a routing hint.
 *
 * @param event the event object to publish; must be serializable to JSON
 * @param routingKey routing hint used by the underlying broker (e.g., RabbitMQ routing key or SQS message attribute)
 * @param <T> the event type
 * @throws EventPublishException if publishing fails after retries
 */
    <T> void publish(T event, String routingKey);
}