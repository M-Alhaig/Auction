package com.auction.notificationservice.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * RabbitMQ configuration for Notification Service.
 *
 * <p>Consumes bidding events from the shared "auction-events" exchange:
 * <ul>
 *   <li>BidPlacedEvent (routing key: "bidding.bid-placed") - Broadcast via WebSocket</li>
 *   <li>UserOutbidEvent (routing key: "bidding.user-outbid") - WebSocket + persist</li>
 * </ul>
 *
 * <p><strong>Queue Strategy:</strong> Single queue with wildcard binding "bidding.*" to receive
 * all bidding-related events.
 *
 * <p><strong>Retry Policy:</strong>
 * <ul>
 *   <li>Max attempts: 3</li>
 *   <li>Exponential backoff: 100ms → 150ms → 225ms</li>
 *   <li>IllegalArgumentException: NOT retried (permanent error)</li>
 * </ul>
 */
@Configuration
public class RabbitMQConfig {

  public static final String NOTIFICATION_SERVICE_BIDDING_EVENTS_QUEUE = "NotificationServiceBiddingEventsQueue";
  public static final String NOTIFICATION_SERVICE_BIDDING_EVENTS_QUEUE_DLQ = "NotificationServiceBiddingEventsQueue.dlq";

  @Value("${rabbitmq.exchange.name}")
  private String exchangeName;

  /**
   * Declares the shared topic exchange for auction events.
   * Same exchange used by bidding-service and item-service.
   */
  @Bean
  public TopicExchange auctionEventsExchange() {
    return new TopicExchange(exchangeName, true, false);
  }

  /**
   * Queue for consuming bidding events (BidPlacedEvent, UserOutbidEvent).
   * Configured with DLQ for failed message handling.
   */
  @Bean
  public Queue biddingEventsQueue() {
    return QueueBuilder
        .durable(NOTIFICATION_SERVICE_BIDDING_EVENTS_QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(NOTIFICATION_SERVICE_BIDDING_EVENTS_QUEUE_DLQ)
        .build();
  }

  /**
   * Dead letter queue for failed bidding event processing.
   * Monitor this queue's size - growth indicates systematic processing issues.
   */
  @Bean
  public Queue biddingEventsDlq() {
    return QueueBuilder.durable(NOTIFICATION_SERVICE_BIDDING_EVENTS_QUEUE_DLQ).build();
  }

  /**
   * Binds the bidding events queue to receive all events with routing key "bidding.*".
   *
   * <p>Matches:
   * <ul>
   *   <li>"bidding.bid-placed" → BidPlacedEvent</li>
   *   <li>"bidding.user-outbid" → UserOutbidEvent</li>
   * </ul>
   */
  @Bean
  public Binding biddingEventsBinding(Queue biddingEventsQueue, TopicExchange auctionEventsExchange) {
    return BindingBuilder
        .bind(biddingEventsQueue)
        .to(auctionEventsExchange)
        .with("bidding.*");
  }

  /**
   * Jackson JSON message converter for event serialization/deserialization.
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /**
   * Custom listener container factory with retry policy.
   *
   * <p><strong>Retry Configuration:</strong>
   * <ul>
   *   <li>Max attempts: 3</li>
   *   <li>Initial interval: 100ms</li>
   *   <li>Backoff multiplier: 1.5x</li>
   *   <li>Max interval: 500ms</li>
   * </ul>
   *
   * <p><strong>Non-Retryable Exceptions:</strong>
   * <ul>
   *   <li>IllegalArgumentException - Permanent error (malformed event data)</li>
   * </ul>
   */
  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      SimpleRabbitListenerContainerFactoryConfigurer configurer) {

    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);

    // Map<ExceptionClass, ShouldRetry> - true = retry, false = don't retry (go to DLQ)
    Map<Class<? extends Throwable>, Boolean> shouldRetryException = new HashMap<>();
    shouldRetryException.put(IllegalArgumentException.class, false); // Don't retry permanent errors

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
        3,                   // max attempts
        shouldRetryException,
        true,                // traverseCauses: check exception cause chain
        true                 // defaultRetryValue: retry unlisted exceptions by default
    );

    // Exponential backoff: 100ms → 150ms → 225ms
    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(100);
    backOffPolicy.setMultiplier(1.5);
    backOffPolicy.setMaxInterval(500);

    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.setBackOffPolicy(backOffPolicy);

    // Build retry interceptor
    RetryOperationsInterceptor retryInterceptor = new RetryOperationsInterceptor();
    retryInterceptor.setRetryOperations(retryTemplate);

    factory.setAdviceChain(retryInterceptor);
    factory.setDefaultRequeueRejected(false); // Failed messages go to DLQ
    factory.setMessageConverter(jsonMessageConverter());

    return factory;
  }
}
