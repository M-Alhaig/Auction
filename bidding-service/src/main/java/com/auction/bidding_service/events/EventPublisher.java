package com.auction.bidding_service.events;

import com.auction.bidding_service.exceptions.EventPublishException;

/**
 * Generic event publisher abstraction for messaging infrastructure.
 * Decouples business logic from specific message broker implementations (RabbitMQ, SQS/SNS, Kafka).
 *
 * Implementation Strategy:
 * - Current: RabbitMQEventPublisher (uses RabbitTemplate)
 * - Future: SQSEventPublisher (uses AWS SDK SQS/SNS clients)
 *
 * Migration Path:
 * To migrate from RabbitMQ to AWS SQS/SNS:
 * 1. Implement SQSEventPublisher implementing this interface
 * 2. Update Spring @Primary annotation or configuration
 * 3. No changes needed in service layer code
 *
 * Thread Safety: Implementations must be thread-safe (typically singleton beans).
 */
public interface EventPublisher {

    /**
     * Publish an event to the messaging infrastructure.
     * The event will be routed based on its type and the underlying implementation.
     *
     * RabbitMQ Implementation:
     * - Routes to exchange based on event class name
     * - Uses topic exchange with routing key pattern: {service}.{event-type}
     *
     * SQS/SNS Implementation:
     * - Publishes to SNS topic based on event class name
     * - Subscribers (SQS queues) filter by event type
     *
     * @param event the event object to publish (must be serializable to JSON)
     * @param <T> the event type (should be a record or POJO)
     * @throws EventPublishException if publishing fails after retries
     */
    <T> void publish(T event);

    /**
     * Publish an event with additional routing hints.
     * Useful for explicit routing in multi-tenant or complex routing scenarios.
     *
     * @param event the event object to publish
     * @param routingKey routing hint (RabbitMQ: routing key, SQS: message attribute)
     * @param <T> the event type
     * @throws EventPublishException if publishing fails after retries
     */
    <T> void publish(T event, String routingKey);
}
