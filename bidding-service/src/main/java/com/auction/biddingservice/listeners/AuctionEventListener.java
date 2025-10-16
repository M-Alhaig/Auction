package com.auction.biddingservice.listeners;

import com.auction.biddingservice.events.AuctionEndedEvent;
import com.auction.biddingservice.services.AuctionCacheService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventListener {

	private final AuctionCacheService auctionCacheService;
	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration LOCK_TIMEOUT = Duration.ofHours(1);
	private static final String LOCK_KEY_PREFIX = "lock:event:processed:";

	/**
	 * Consumes AuctionEndedEvent messages from RabbitMQ and marks auctions as ended in Redis cache.
	 *
	 * <p><strong>Idempotency:</strong> Uses Redis to track processed event IDs (1-hour TTL) to
	 * prevent duplicate processing if the message is redelivered.
	 *
	 * <p><strong>Error Handling:</strong>
	 * <ul>
	 *   <li>IllegalArgumentException - Permanent error (invalid event data), rethrown to move to DLQ</li>
	 *   <li>All other exceptions - Transient errors (Redis timeout, network), rethrown to trigger retry</li>
	 * </ul>
	 *
	 * <p><strong>Retry Policy:</strong> Up to 3 attempts with exponential backoff (100ms → 150ms → 225ms).
	 * After max retries, message moves to dead letter queue (BiddingServiceAuctionQueue.dlq).
	 *
	 * @param event the AuctionEndedEvent containing itemId, endTime, and other auction details
	 */
	@RabbitListener(queues = "#{auctionEndedQueue.name}")
	public void onAuctionEnded(AuctionEndedEvent event) {
		String lockKey = LOCK_KEY_PREFIX + event.eventId();

		Boolean alreadyProcessed = redisTemplate.hasKey(lockKey);
		if (Boolean.TRUE.equals(alreadyProcessed)) {
			log.info("Event already processed successfully - eventId: {}, skipping", event.eventId());
			return;
		}

		try {
			auctionCacheService.markAuctionEnded(event.data().itemId(), event.data().endTime());

			redisTemplate.opsForValue().set(lockKey, "1", LOCK_TIMEOUT);
			log.info("AuctionEndedEvent processed successfully - eventId: {}, itemId: {}",
				event.eventId(), event.data().itemId());

		} catch (IllegalArgumentException e) {
			log.error("Invalid auction ended event - eventId: {}, itemId: {}, reason: {}",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;  // Permanent error - don't retry, move to DLQ
		} catch (Exception e) {
			log.warn("Failed to process auction ended event - eventId: {}, itemId: {}, will retry. Reason: {}",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;  // Transient error - retry with backoff
		}
	}
}
