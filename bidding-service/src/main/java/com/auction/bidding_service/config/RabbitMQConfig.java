package com.auction.bidding_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for Bidding Service. Declares the topic exchange used for publishing bid
 * events.
 * <p>
 * Architecture: - Bidding Service (Producer): Declares exchange, publishes events - Consumer
 * Services: Declare their own queues and bind to this exchange
 * <p>
 * Exchange Strategy: - Type: Topic Exchange (supports wildcard routing) - Name: "auction-events"
 * (shared with Item Service) - Durable: true (survives broker restarts) - Auto-delete: false
 * (persists even when no queues are bound)
 * <p>
 * Routing Keys Published by Bidding Service: - "bidding.bid-placed" → BidPlacedEvent -
 * "bidding.user-outbid" → UserOutbidEvent
 * <p>
 * Migration Notes: - When migrating to SQS, this config can be @Profile("rabbitmq") to disable in
 * AWS - SQS uses SNS topics instead of RabbitMQ exchanges
 */
@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE_NAME = "auction-events";

  /**
   * Declare the topic exchange for auction events. This is the same exchange used by Item Service
   * for auction lifecycle events. Consumers will bind their queues to this exchange with routing
   * key patterns.
   * <p>
   * Topic Exchange Routing Examples: - Producer sends with routing key "bidding.bid-placed" -
   * Consumer queue bound with "bidding.*" → receives all bidding events - Consumer queue bound with
   * "*.bid-placed" → receives bid-placed events from any service - Consumer queue bound with "#" →
   * receives all auction events
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
   * JSON message converter using Jackson. Handles Java 8 date/time types (LocalDateTime, etc.).
   *
   * @return the message converter
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
