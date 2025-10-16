package com.auction.itemservice.listeners;


import com.auction.itemservice.events.BidPlacedEvent;
import com.auction.itemservice.exceptions.ConcurrentBidException;
import com.auction.itemservice.exceptions.ItemNotFoundException;
import com.auction.itemservice.services.ItemLifecycleServiceImpl;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class BidEventListener {

	private final ItemLifecycleServiceImpl itemLifecycleServiceImpl;
	private final RedisTemplate<String, String> redisTemplate;

	public static final String QUEUE_NAME = "ItemServiceBidQueue";
	private static final Duration LOCK_TIMEOUT = Duration.ofHours(1);
	private static final String LOCK_KEY_PREFIX = "lock:event:processed:";

	/**
	 * Consume BidPlacedEvent from RabbitMQ and update item price/winner.
	 *
	 * <p><strong>Idempotency:</strong> Lock is set AFTER successful processing to allow retries on failure.
	 *
	 * <p><strong>Error Handling:</strong> Retry policy configured at container factory level:
	 * <ul>
	 *   <li>ItemNotFoundException, IllegalArgumentException: No retry, moved to DLQ immediately</li>
	 *   <li>ConcurrentBidException, others: Retry with exponential backoff (3 attempts)</li>
	 * </ul>
	 *
	 * @param event the BidPlacedEvent from Bidding Service
	 */
	@RabbitListener(queues = QUEUE_NAME)
	public void onBidEvent(BidPlacedEvent event) {
		String lockKey = LOCK_KEY_PREFIX + event.eventId();

		// Check if already processed (idempotency)
		Boolean alreadyProcessed = redisTemplate.hasKey(lockKey);
		if (Boolean.TRUE.equals(alreadyProcessed)) {
			log.info("Event already processed successfully - eventId: {}, skipping", event.eventId());
			return;
		}

		log.info("Processing BidPlacedEvent - eventId: {}, itemId: {}, bidderId: {}, bidAmount: {}",
			event.eventId(), event.data().itemId(), event.data().bidderId(), event.data().bidAmount());

		try {
			// Update item's currentPrice and winnerId with distributed locking
			itemLifecycleServiceImpl.updateCurrentPriceWithLock(
				event.data().itemId(),
				event.data().bidderId(),
				event.data().bidAmount()
			);

			// SUCCESS: Mark event as processed (idempotency lock)
			redisTemplate.opsForValue().set(lockKey, "1", LOCK_TIMEOUT);
			log.info("BidPlacedEvent processed successfully - eventId: {}, itemId: {}",
				event.eventId(), event.data().itemId());

		} catch (ItemNotFoundException e) {
			// Permanent error: Item was deleted or never existed - expected in normal operation
			// Error handler will reject this without retry
			log.warn("Item not found for bid event - eventId: {}, itemId: {}, reason: {}. Will be moved to DLQ.",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;

		} catch (IllegalArgumentException e) {
			// Permanent error: Invalid data - indicates bug in Bidding Service
			// Error handler will reject this without retry
			log.error("Invalid bid data received - eventId: {}, itemId: {}, reason: {}. Will be moved to DLQ.",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;

		} catch (ConcurrentBidException e) {
			// Transient error: Redis lock contention - error handler will allow retry with backoff
			log.warn("Concurrent bid conflict - eventId: {}, itemId: {}, reason: {}. Will retry...",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;

		} catch (Exception e) {
			// Unknown error: Could be transient (DB timeout) or permanent (bug)
			// Error handler will allow retry - if it persists after max attempts, moves to DLQ
			log.error("Unexpected error processing bid event - eventId: {}, itemId: {}, error: {} ({}). Will retry...",
				event.eventId(), event.data().itemId(), e.getMessage(), e.getClass().getSimpleName());
			log.debug("Full exception details:", e);
			throw e;
		}
	}

}
