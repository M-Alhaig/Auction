package com.auction.userservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for User Service.
 * User Service only publishes events (UserRegisteredEvent, UserUpdatedEvent).
 *
 * <p>Exchange: "auction-events" (shared topic exchange)
 * <p>Routing keys: "user.registered", "user.updated"
 */
@Configuration
public class RabbitMQConfig {

  @Value("${rabbitmq.exchange.name}")
  private String exchangeName;

  @Bean
  public TopicExchange auctionEventsExchange() {
    return new TopicExchange(exchangeName, true, false);
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    rabbitTemplate.setExchange(exchangeName);
    return rabbitTemplate;
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
