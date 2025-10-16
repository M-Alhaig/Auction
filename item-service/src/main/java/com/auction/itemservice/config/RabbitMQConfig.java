package com.auction.itemservice.config;

import com.auction.itemservice.exceptions.ItemNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
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

import java.util.HashMap;
import java.util.Map;

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
@Slf4j
@Configuration
public class RabbitMQConfig {

	public static final String BIDDING_BID_PLACED = "bidding.bid-placed";
	public static final String QUEUE_NAME = "ItemServiceBidQueue";
	public static final String DLQ_NAME = "ItemServiceBidQueue.dlq";

	@Value("${rabbitmq.exchange.name}")
	private String exchangeName;

	/**
	 * Declares a durable topic exchange named "auction-events" for routing auction event messages.
	 *
	 * <p>Consumers bind queues with routing key patterns to receive matching auction events.
	 *
	 * @return the TopicExchange configured with the name "auction-events", durable = true, and
	 * autoDelete = false
	 */
	@Bean
	public TopicExchange auctionEventsExchange() {
		return new TopicExchange(exchangeName, true,   // durable: exchange survives broker restart
			false   // auto-delete: don't delete when last queue unbinds
		);
	}

	/**
	 * Main queue for BidPlacedEvent consumption with Dead Letter Queue configuration.
	 *
	 * <p>After 3 failed delivery attempts, messages are routed to the DLQ for manual inspection.
	 * This prevents poison pill messages from blocking the queue indefinitely.
	 *
	 * @return configured Queue with DLQ routing
	 */
	@Bean
	public Queue queue() {
		return QueueBuilder.durable(QUEUE_NAME)
			.deadLetterExchange("")  // Default exchange
			.deadLetterRoutingKey(DLQ_NAME)
			.build();
	}

	/**
	 * Dead Letter Queue for failed BidPlacedEvent processing.
	 *
	 * <p>Messages that fail after max retries are moved here for manual inspection and debugging.
	 * Monitor this queue's size - growth indicates systematic processing issues.
	 *
	 * @return the Dead Letter Queue
	 */
	@Bean
	public Queue deadLetterQueue() {
		return QueueBuilder.durable(DLQ_NAME).build();
	}

	@Bean
	public Binding binding(Queue queue, TopicExchange exchange) {
		return BindingBuilder.bind(queue).to(exchange).with(BIDDING_BID_PLACED);
	}

	/**
	 * Create and configure a RabbitTemplate that uses a Jackson-based JSON message converter.
	 *
	 * @param connectionFactory the ConnectionFactory provided by Spring Boot for creating
	 *                          connections
	 * @return the RabbitTemplate configured to serialize messages as JSON using Jackson
	 */
	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMessageConverter(jsonMessageConverter());
		rabbitTemplate.setExchange(exchangeName);
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

	/**
	 * Custom container factory with retry policy that classifies exceptions as retryable or
	 * non-retryable.
	 *
	 * <p><strong>Non-Retryable (permanent errors, move to DLQ immediately):</strong>
	 * <ul>
	 *   <li>ItemNotFoundException - Item was deleted or never existed</li>
	 *   <li>IllegalArgumentException - Invalid data from producer (bug)</li>
	 *   <li>AmqpRejectAndDontRequeueException - Explicit rejection signal</li>
	 * </ul>
	 *
	 * <p><strong>Retryable (transient errors, retry with backoff):</strong>
	 * <ul>
	 *   <li>ConcurrentBidException - Redis lock contention</li>
	 *   <li>All other exceptions - Could be transient (DB timeout, network)</li>
	 * </ul>
	 *
	 * @param configurer Spring Boot auto-configurer
	 * @param connectionFactory RabbitMQ connection factory
	 * @return configured container factory with custom retry policy
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
			SimpleRabbitListenerContainerFactoryConfigurer configurer,
			ConnectionFactory connectionFactory) {

		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

		// Apply default Spring Boot configuration
		configurer.configure(factory, connectionFactory);

		// Configure the retry template with non-retryable exceptions
		RetryTemplate retryTemplate = new RetryTemplate();

		// Retry policy: max 3 attempts, but NOT for permanent errors
		Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
		retryableExceptions.put(ItemNotFoundException.class, false);  // Don't retry
		retryableExceptions.put(IllegalArgumentException.class, false);  // Don't retry
		retryableExceptions.put(AmqpRejectAndDontRequeueException.class, false);  // Don't retry

		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
				3,  // max attempts
				retryableExceptions,
				true,  // traverseCauses = true (check exception cause chain)
				true   // defaultValue = true (retry by default for unlisted exceptions)
		);
		retryTemplate.setRetryPolicy(retryPolicy);

		// Exponential backoff: 100ms -> 150ms -> 225ms (auction-critical timing)
		ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
		backOffPolicy.setInitialInterval(100);
		backOffPolicy.setMultiplier(1.5);
		backOffPolicy.setMaxInterval(500);
		retryTemplate.setBackOffPolicy(backOffPolicy);

		factory.setRetryTemplate(retryTemplate);

		// Set message converter
		factory.setMessageConverter(jsonMessageConverter());

		return factory;
	}
}
