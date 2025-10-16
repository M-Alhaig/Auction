package com.auction.biddingservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

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

	public static final String BIDDING_SERVICE_AUCTION_QUEUE_DLQ = "BiddingServiceAuctionQueue.dlq";
	public static final String AUCTION_ENDED = "item.auction-ended";
	@Value("${rabbitmq.exchange.name}")
  private String exchangeName;

  /**
   * Declares a durable topic exchange for auction events shared with the Item Service.
   *
   * <p>Consumers bind queues to this exchange using routing key patterns. Examples:
   * <ul>
   *   <li>Producer sends with routing key "bidding.bid-placed".</li>
   *   <li>Queue bound with "bidding.*" → receives all bidding events.</li>
   *   <li>Queue bound with "*.bid-placed" → receives bid-placed events from any service.</li>
   *   <li>Queue bound with "#" → receives all auction events.</li>
   * </ul>
   *
   * @return the TopicExchange for auction events
   */
  @Bean
  public TopicExchange auctionEventsExchange() {
    return new TopicExchange(
        exchangeName,
        true,   // durable: exchange survives broker restart
        false   // auto-delete: don't delete when last queue unbinds
    );
  }

  @Bean
  public Queue queue() {
    return QueueBuilder
		.durable("BiddingServiceAuctionQueue")
		.deadLetterExchange("")
		.deadLetterRoutingKey(BIDDING_SERVICE_AUCTION_QUEUE_DLQ)
		.build();
  }

  @Bean
  public Queue deadLetterQueue() {
	  return QueueBuilder.durable(BIDDING_SERVICE_AUCTION_QUEUE_DLQ).build();
  }

  @Bean
  public Binding binding(Queue queue, TopicExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).with(AUCTION_ENDED);
  }

  /**
   * Create and configure a RabbitTemplate that serializes messages as JSON using Jackson.
   *
   * @return the configured RabbitTemplate
   */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    rabbitTemplate.setExchange(exchangeName);
    return rabbitTemplate;
  }

  /**
   * Provides a Jackson-based JSON MessageConverter that supports Java 8 date/time types.
   *
   * @return a MessageConverter that serializes and deserializes message payloads to/from JSON, including Java 8 date/time types (e.g., LocalDateTime)
   */
  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
	  SimpleRabbitListenerContainerFactoryConfigurer configurer) {

	  SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

	  configurer.configure(factory, connectionFactory);

	  RetryTemplate retryTemplate = new RetryTemplate();

	  SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3);

	  retryTemplate.setRetryPolicy(retryPolicy);

	  ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
	  backOffPolicy.setInitialInterval(100);
	  backOffPolicy.setMultiplier(1.5);
	  backOffPolicy.setMaxInterval(500);
	  retryTemplate.setBackOffPolicy(backOffPolicy);

	  factory.setRetryTemplate(retryTemplate);
	  factory.setMessageConverter(jsonMessageConverter());

	  return factory;
  }


}
