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

	// Single queue for all Item Service events (auction lifecycle)
	public static final String BIDDING_SERVICE_ITEM_EVENTS_QUEUE = "BiddingServiceItemEventsQueue";
	public static final String BIDDING_SERVICE_ITEM_EVENTS_QUEUE_DLQ = "BiddingServiceItemEventsQueue.dlq";

	// Routing keys
	public static final String AUCTION_STARTED = "item.auction-started";
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

  /**
   * Creates a single queue for consuming all Item Service lifecycle events.
   *
   * <p><strong>Events Consumed:</strong>
   * <ul>
   *   <li>AuctionStartedEvent (routing key: "item.auction-started") - Cache auction metadata</li>
   *   <li>AuctionEndedEvent (routing key: "item.auction-ended") - Mark auction as ended</li>
   * </ul>
   *
   * <p><strong>Queue Configuration:</strong>
   * <ul>
   *   <li>Queue name: "BiddingServiceItemEventsQueue"</li>
   *   <li>Durable: true (survives RabbitMQ broker restarts)</li>
   *   <li>Dead Letter Exchange: "" (default exchange)</li>
   *   <li>Dead Letter Routing Key: "BiddingServiceItemEventsQueue.dlq"</li>
   * </ul>
   *
   * <p><strong>Design Rationale:</strong> Single queue for all item lifecycle events because:
   * <ul>
   *   <li>Same producer (Item Service) → Same consumer (Bidding Service)</li>
   *   <li>Same retry policy and error handling</li>
   *   <li>Same criticality (auction-critical events)</li>
   *   <li>Simpler infrastructure (1 queue + 1 DLQ instead of 2 queues + 2 DLQs)</li>
   * </ul>
   *
   * <p>Messages that fail after max retries (3 attempts) are automatically routed
   * to the dead letter queue for manual inspection.
   *
   * @return the configured Queue for all item lifecycle events
   */
  @Bean
  public Queue itemEventsQueue() {
    return QueueBuilder
		.durable(BIDDING_SERVICE_ITEM_EVENTS_QUEUE)
		.deadLetterExchange("")
		.deadLetterRoutingKey(BIDDING_SERVICE_ITEM_EVENTS_QUEUE_DLQ)
		.build();
  }

  /**
   * Creates the dead letter queue (DLQ) for failed Item Service event processing.
   *
   * <p>Messages are routed here when:
   * <ul>
   *   <li>Processing fails after 3 retry attempts</li>
   *   <li>Permanent errors occur (IllegalArgumentException - invalid event data)</li>
   *   <li>Message cannot be deserialized</li>
   * </ul>
   *
   * <p>Monitor this queue's size - growth indicates systematic processing issues
   * that require investigation (e.g., Item Service publishing malformed events,
   * Redis unavailability, or consumer bugs).
   *
   * @return the configured Dead Letter Queue
   */
  @Bean
  public Queue itemEventsDLQ() {
	  return QueueBuilder.durable(BIDDING_SERVICE_ITEM_EVENTS_QUEUE_DLQ).build();
  }

  /**
   * Binds the item events queue to the auction-events exchange using wildcard routing.
   *
   * <p><strong>Routing Key Pattern:</strong> "item.*" (wildcard matches all item events)
   * <ul>
   *   <li>Matches: "item.auction-started" → AuctionStartedEvent</li>
   *   <li>Matches: "item.auction-ended" → AuctionEndedEvent</li>
   *   <li>Future-proof: Automatically receives any new "item.*" events</li>
   * </ul>
   *
   * <p><strong>Listener Discrimination:</strong> The ItemEventListener uses method overloading
   * to discriminate between event types based on the message payload class.
   *
   * @param itemEventsQueue the queue to bind (injected by Spring)
   * @param exchange the topic exchange to bind to (injected by Spring)
   * @return the configured Binding with wildcard pattern
   */
  @Bean
  public Binding itemEventsBinding(Queue itemEventsQueue, TopicExchange exchange) {
    return BindingBuilder.bind(itemEventsQueue).to(exchange).with("item.*");
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

  /**
   * Creates a custom RabbitMQ listener container factory with retry policy that classifies
   * exceptions as retryable or non-retryable.
   *
   * <p><strong>Non-Retryable Exceptions (permanent errors, move to DLQ immediately):</strong>
   * <ul>
   *   <li>IllegalArgumentException - Invalid event data (indicates bug in producer)</li>
   * </ul>
   *
   * <p><strong>Retryable Exceptions (transient errors, retry with backoff):</strong>
   * <ul>
   *   <li>All other exceptions - Could be transient (Redis timeout, network issues)</li>
   * </ul>
   *
   * <p><strong>Retry Configuration:</strong>
   * <ul>
   *   <li>Max attempts: 3</li>
   *   <li>Initial interval: 100ms</li>
   *   <li>Backoff multiplier: 1.5x</li>
   *   <li>Max interval: 500ms</li>
   *   <li>Total retry time: ~250ms (auction-critical timing)</li>
   * </ul>
   *
   * @param connectionFactory the RabbitMQ connection factory
   * @param configurer Spring Boot auto-configurer for default settings
   * @return configured container factory with custom retry policy
   */
  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
	  SimpleRabbitListenerContainerFactoryConfigurer configurer) {

	  SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

	  configurer.configure(factory, connectionFactory);

	  RetryTemplate retryTemplate = new RetryTemplate();

	  // Classify exceptions: which ones should NOT be retried
	  java.util.Map<Class<? extends Throwable>, Boolean> retryableExceptions = new java.util.HashMap<>();
	  retryableExceptions.put(IllegalArgumentException.class, false);  // Don't retry - permanent error

	  SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
		  3,  // max attempts
		  retryableExceptions,
		  true,  // traverseCauses = true (check exception cause chain)
		  true   // defaultValue = true (retry by default for unlisted exceptions)
	  );

	  retryTemplate.setRetryPolicy(retryPolicy);

	  // Exponential backoff: 100ms → 150ms → 225ms (auction-critical timing)
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
