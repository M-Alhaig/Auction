package com.auction.itemservice.listeners;


import com.auction.events.BidPlacedEvent;
import com.auction.itemservice.exceptions.ConcurrentEventProcessingException;
import com.auction.itemservice.exceptions.ItemNotFoundException;
import com.auction.itemservice.services.ItemLifecycleServiceImpl;
import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Listener for all Bidding Service events consumed via a single queue.
 *
 * <p><strong>Events Handled:</strong>
 * <ul>
 *   <li>{@link BidPlacedEvent} - Updates item currentPrice and winnerId</li>
 * </ul>
 *
 * <p><strong>Listener Pattern:</strong> Single {@code @RabbitListener} method with manual type dispatch.
 * Centralized three-state idempotency logic handles all event types uniformly, preventing code duplication.
 *
 * <p><strong>Idempotency (Three-State Machine):</strong>
 * <ul>
 *   <li><strong>"completed"</strong>: Event successfully processed → Skip on retry (no exception)</li>
 *   <li><strong>"processing"</strong>: Event currently being processed by another thread/instance → Throw exception to retry later</li>
 *   <li><strong>"failed"</strong>: Event previously failed → Allow retry by transitioning back to "processing"</li>
 * </ul>
 *
 * <p><strong>Queue Strategy:</strong> Single queue (ItemServiceBidQueue) bound with
 * routing key "bidding.bid-placed" receives bid events from Bidding Service.
 *
 * <p><strong>Design Rationale:</strong> Single queue + single listener because:
 * <ul>
 *   <li>Same producer (Bidding Service) → Same consumer (Item Service)</li>
 *   <li>Same retry policy and error handling</li>
 *   <li>Same idempotency requirements</li>
 *   <li>Simpler infrastructure (1 queue + 1 DLQ instead of N queues + N DLQs)</li>
 *   <li>Centralized idempotency logic (no duplication)</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
@Component
@RabbitListener(queues = "#{itemServiceBidQueue.name}")
public class BidEventListener {

	private final ItemLifecycleServiceImpl itemLifecycleServiceImpl;
	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration LOCK_TIMEOUT = Duration.ofHours(1);
	private static final String LOCK_KEY_PREFIX = "event:state:item:";

	// Event processing states (three-state idempotency machine)
	private static final String STATE_PROCESSING = "processing";
	private static final String STATE_COMPLETED = "completed";
	private static final String STATE_FAILED = "failed";

	/**
	 * Handles BidPlacedEvent with centralized three-state idempotency logic.
	 *
	 * @param event the BidPlacedEvent from Bidding Service
	 */
	@RabbitHandler
	public void handleBidPlaced(BidPlacedEvent event) {
		handleEvent(event.eventId(), "BidPlacedEvent", () -> processBidPlaced(event));
	}

	/**
	 * Centralized idempotency logic for all event types.
	 *
	 * <p><strong>Idempotency Flow:</strong>
	 * <pre>
	 * 1. Claim event atomically using Lua script
	 * 2. If "completed" → Skip (already processed)
	 * 3. If "processing" → Throw exception (concurrent/retry)
	 * 4. If "claimed" → Execute business logic handler
	 * 5. On success → Mark as "completed"
	 * 6. On transient error → Mark as "failed" (allows retry)
	 * 7. On permanent error → Leave as "processing" (goes to DLQ)
	 * </pre>
	 *
	 * @param eventId the unique event identifier for idempotency tracking
	 * @param eventType the event type name for logging
	 * @param handler the business logic to execute
	 */
	private void handleEvent(String eventId, String eventType, Runnable handler) {
		String stateKey = LOCK_KEY_PREFIX + eventId;

		// Atomic state transition using Lua script (prevents race conditions)
		String claimScript = """
				local current = redis.call('get', KEYS[1])
				if current == 'completed' then
				\treturn 'completed'
				elseif current == 'processing' then
				\treturn 'processing'
				else
				\t-- current is 'failed' or nil - claim as 'processing'
				\tredis.call('set', KEYS[1], 'processing', 'EX', ARGV[1])
				\treturn 'claimed'
				end
				""";

		String result = redisTemplate.execute(
			RedisScript.of(claimScript, String.class),
			Collections.singletonList(stateKey),
			String.valueOf(LOCK_TIMEOUT.toSeconds())
		);

		if (STATE_COMPLETED.equals(result)) {
			log.info("Event already processed successfully - eventId: {}, type: {}, skipping",
				eventId, eventType);
			return; // No exception - RabbitMQ ACKs
		}

		if (STATE_PROCESSING.equals(result)) {
			log.warn("Event is currently being processed by another instance - eventId: {}, type: {}, will retry",
				eventId, eventType);
			throw new ConcurrentEventProcessingException(
				"Event is being processed concurrently, retry later");
		}

		// result == "claimed" - we successfully claimed it
		log.info("Processing event - eventId: {}, type: {}", eventId, eventType);

		try {
			// Execute business logic handler
			handler.run();

			// SUCCESS: Mark as completed
			redisTemplate.opsForValue().set(stateKey, STATE_COMPLETED, LOCK_TIMEOUT);
			log.info("Event processed successfully - eventId: {}, type: {}", eventId, eventType);

		} catch (ItemNotFoundException | IllegalArgumentException e) {
			// Permanent error - leave as "processing", goes to DLQ after retries
			log.error("Permanent error processing event - eventId: {}, type: {}, reason: {}. Will be moved to DLQ.",
				eventId, eventType, e.getMessage());
			throw e;

		} catch (Exception e) {
			// Transient error - mark as "failed" to allow retry
			redisTemplate.opsForValue().set(stateKey, STATE_FAILED, LOCK_TIMEOUT);
			log.warn("Transient error processing event - eventId: {}, type: {}, reason: {}. Marked as 'failed' for retry...",
				eventId, eventType, e.getMessage());
			throw e;
		}
	}

	// ==================== BUSINESS LOGIC HANDLERS (No idempotency concerns) ====================

	/**
	 * Processes BidPlacedEvent - updates item's currentPrice and winnerId.
	 */
	private void processBidPlaced(BidPlacedEvent event) {
		itemLifecycleServiceImpl.updateCurrentPriceWithLock(
			event.data().itemId(),
			event.data().bidderId(),
			event.data().bidAmount()
		);
		log.info("Updated item price - itemId: {}, newPrice: {}, winnerId: {}",
			event.data().itemId(), event.data().bidAmount(), event.data().bidderId());
	}
}
