package com.auction.item_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for Item Service. Declares the topic exchange used for publishing auction
 * events.
 * <p>
 * Architecture: - Item Service (Producer): Declares exchange, publishes events - Consumer Services:
 * Declare their own queues and bind to this exchange
 * <p>
 * Exchange Strategy: - Type: Topic Exchange (supports wildcard routing) - Name: "auction-events" -
 * Durable: true (survives broker restarts) - Auto-delete: false (persists even when no queues are
 * bound)
 * <p>
 * Migration Notes: - When migrating to SQS, this config can be @Profile("rabbitmq") to disable in
 * AWS - SQS uses SNS topics instead of RabbitMQ exchanges
 */
@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE_NAME = "auction-events";

  /**
   * Declare the topic exchange for auction events. Consumers will bind their queues to this
   * exchange with routing key patterns.
   * <p>
   * Topic Exchange Routing Examples: - Producer sends with routing key "item.auction-started" -
   * Consumer queue bound with "item.*" → receives all item events - Consumer queue bound with
   * "item.auction-started" → receives only auction-started events - Consumer queue bound with
   * "*.auction-*" → receives all auction events from any service
   *
   * @return the topic exchange bean
   */
  @Bean
  public TopicExchange auctionEventsExchange() {
    return new TopicExchange(
        EXCHANGE_NAME,
        true,   // durable: exchange survives broker restart
        false   // auto-delete: don't delete when last queue unbinds
    );
  }

  /**
   * Configure RabbitTemplate to use Jackson for JSON serialization. This ensures events are sent as
   * JSON strings, not Java serialized objects.
   * <p>
   * Why JSON over Java Serialization: - Cross-language compatibility (consumers can be Python,
   * Node.js, etc.) - Human-readable in RabbitMQ management UI - Version-safe (no
   * ClassNotFoundException on schema changes)
   *
   * @param connectionFactory injected by Spring Boot auto-configuration
   * @return configured RabbitTemplate
   */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    return rabbitTemplate;
  }

  /**
   * JSON message converter using Jackson. Handles Java 8 date/time types (configured in
   * RabbitMQEventPublisher).
   *
   * @return the message converter
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
