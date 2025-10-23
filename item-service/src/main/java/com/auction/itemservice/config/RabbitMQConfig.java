package com.auction.itemservice.config;

import com.auction.itemservice.exceptions.ItemNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
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
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
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
	public static final String ITEM_SERVICE_BID_QUEUE = "ItemServiceBidQueue";
	public static final String ITEM_SERVICE_BID_QUEUE_DLQ = "ItemServiceBidQueue.dlq";

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
	 * Creates the main queue for consuming BidPlacedEvent messages.
	 *
	 * <p>Queue Configuration:
	 * <ul>
	 *   <li>Queue name: "ItemServiceBidQueue"</li>
	 *   <li>Durable: true (survives RabbitMQ broker restarts)</li>
	 *   <li>Dead Letter Exchange: "" (default exchange)</li>
	 *   <li>Dead Letter Routing Key: "ItemServiceBidQueue.dlq"</li>
	 * </ul>
	 *
	 * <p>Messages that fail after max retries (3 attempts) are automatically routed
	 * to the dead letter queue for manual inspection.
	 *
	 * @return the configured Queue for bid placed events
	 */
	@Bean
	public Queue itemServiceBidQueue() {
		return QueueBuilder.durable(ITEM_SERVICE_BID_QUEUE)
			.deadLetterExchange("")  // Default exchange
			.deadLetterRoutingKey(ITEM_SERVICE_BID_QUEUE_DLQ)
			.build();
	}

	/**
	 * Creates the dead letter queue (DLQ) for failed BidPlacedEvent messages.
	 *
	 * <p>Messages are routed here when:
	 * <ul>
	 *   <li>Processing fails after 3 retry attempts</li>
	 *   <li>Permanent errors occur (ItemNotFoundException, IllegalArgumentException)</li>
	 *   <li>Message cannot be deserialized</li>
	 * </ul>
	 *
	 * <p>Monitor this queue's size - growth indicates systematic processing issues
	 * that require investigation.
	 *
	 * @return the configured Dead Letter Queue
	 */
	@Bean
	public Queue itemServiceBidDLQ() {
		return QueueBuilder.durable(ITEM_SERVICE_BID_QUEUE_DLQ).build();
	}

	/**
	 * Binds the item service bid queue to the auction-events exchange using the
	 * routing key "bidding.bid-placed".
	 *
	 * <p>This binding ensures that when Bidding Service publishes BidPlacedEvent
	 * with routing key "bidding.bid-placed", it will be routed to this service's
	 * queue for processing.
	 *
	 * @param itemServiceBidQueue the queue to bind (injected by Spring)
	 * @param exchange the topic exchange to bind to (injected by Spring)
	 * @return the configured Binding
	 */
	@Bean
	public Binding bidPlacedBinding(Queue itemServiceBidQueue, TopicExchange exchange) {
		return BindingBuilder.bind(itemServiceBidQueue).to(exchange).with(BIDDING_BID_PLACED);
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
	 * <p><strong>Retry Configuration via adviceChain:</strong>
	 * Uses {@link RetryInterceptorBuilder} to create a stateless retry interceptor that
	 * integrates with RabbitMQ's acknowledgment system. The {@code setDefaultRequeueRejected(false)}
	 * prevents failed messages from being requeued indefinitely - they go to DLQ instead.
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

		// Exponential backoff: 100ms -> 150ms -> 225ms (auction-critical timing)
		ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
		backOffPolicy.setInitialInterval(100);
		backOffPolicy.setMultiplier(1.5);
		backOffPolicy.setMaxInterval(500);

		// Create retry template with configured policies
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(retryPolicy);
		retryTemplate.setBackOffPolicy(backOffPolicy);

		// Build retry interceptor using adviceChain (integrates with RabbitMQ ACK/NACK)
		// RetryOperationsInterceptor wraps the retry logic as AOP advice
		Advice retryInterceptor = new RetryOperationsInterceptor();
		((RetryOperationsInterceptor) retryInterceptor).setRetryOperations(retryTemplate);

		factory.setAdviceChain(retryInterceptor);

		// CRITICAL: Prevent infinite requeue loops - failed messages go to DLQ
		factory.setDefaultRequeueRejected(false);

		// Set message converter
		factory.setMessageConverter(jsonMessageConverter());

		return factory;
	}
}
