package com.auction.itemservice.config;

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
   * Declares a durable topic exchange named "auction-events" for routing auction event messages.
   *
   * Consumers bind queues with routing key patterns to receive matching auction events.
   *
   * @return the TopicExchange configured with name "auction-events", durable = true, and autoDelete = false
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
   * Create and configure a RabbitTemplate that uses a Jackson-based JSON message converter.
   *
   * @param connectionFactory the ConnectionFactory provided by Spring Boot for creating connections
   * @return the RabbitTemplate configured to serialize messages as JSON using Jackson
   */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    return rabbitTemplate;
  }

  /**
   * Creates a Jackson-based message converter for JSON serialization and deserialization.
   *
   * @return a MessageConverter that serializes and deserializes messages as JSON using Jackson
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}