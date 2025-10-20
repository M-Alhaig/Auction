package com.auction.biddingservice.listeners;

import com.auction.biddingservice.events.AuctionEndedEvent;
import com.auction.biddingservice.events.AuctionStartedEvent;
import com.auction.biddingservice.models.ItemStatus;
import com.auction.biddingservice.services.AuctionCacheService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Listener for all Item Service lifecycle events consumed via a single queue.
 *
 * <p><strong>Events Handled:</strong>
 * <ul>
 *   <li>{@link AuctionStartedEvent} - Caches auction metadata (startingPrice + endTime) for first bid validation</li>
 *   <li>{@link AuctionEndedEvent} - Marks auction as ended to reject late bids</li>
 * </ul>
 *
 * <p><strong>Queue Strategy:</strong> Single queue (BiddingServiceItemEventsQueue) bound with
 * wildcard pattern "item.*" receives all item lifecycle events. Spring's method overloading
 * automatically discriminates between event types based on message payload class.
 *
 * <p><strong>Design Rationale:</strong> Single queue instead of separate queues because:
 * <ul>
 *   <li>Same producer (Item Service) → Same consumer (Bidding Service)</li>
 *   <li>Same retry policy and error handling</li>
 *   <li>Same criticality (auction-critical events)</li>
 *   <li>Simpler infrastructure (1 queue + 1 DLQ instead of 2 queues + 2 DLQs)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventListener {

	private final AuctionCacheService auctionCacheService;
	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration LOCK_TIMEOUT = Duration.ofHours(1);
	private static final String LOCK_KEY_PREFIX = "lock:event:processed:";

	/**
	 * Consumes AuctionEndedEvent messages and marks auctions as ended in Redis cache.
	 *
	 * <p><strong>Cache Strategy:</strong>
	 * <ul>
	 *   <li>Marks auction as ended in dedicated ended-flag cache (7-day TTL)</li>
	 *   <li>Updates metadata cache status to ENDED (allows status-based validation)</li>
	 * </ul>
	 *
	 * <p><strong>Purpose:</strong> Fast rejection of bids on ended auctions (~1ms Redis lookup)
	 * before acquiring distributed locks or querying databases.
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
	 * After max retries, message moves to dead letter queue (BiddingServiceItemEventsQueue.dlq).
	 *
	 * @param event the AuctionEndedEvent containing itemId, endTime, and other auction details
	 */
	@RabbitListener(queues = "#{itemEventsQueue.name}")
	public void onAuctionEnded(AuctionEndedEvent event) {
		String lockKey = LOCK_KEY_PREFIX + event.eventId();

		Boolean alreadyProcessed = redisTemplate.hasKey(lockKey);
		if (Boolean.TRUE.equals(alreadyProcessed)) {
			log.info("Event already processed successfully - eventId: {}, skipping", event.eventId());
			return;
		}

		try {
			// Mark auction as ended in dedicated ended-flag cache
			auctionCacheService.markAuctionEnded(event.data().itemId(), event.data().endTime());

			// Also update metadata cache status to ENDED (for status-based validation)
			auctionCacheService.cacheAuctionMetadata(
				event.data().itemId(),
				event.data().finalPrice(),  // Use final price as "starting price" for cache
				event.data().endTime(),
				ItemStatus.ENDED
			);

			redisTemplate.opsForValue().set(lockKey, "1", LOCK_TIMEOUT);
			log.info("AuctionEndedEvent processed successfully - eventId: {}, itemId: {}, finalPrice: {}, status: ENDED",
				event.eventId(), event.data().itemId(), event.data().finalPrice());

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

	/**
	 * Consumes AuctionStartedEvent messages and caches auction metadata for first bid validation.
	 *
	 * <p><strong>Cache Strategy:</strong> Stores startingPrice and endTime in Redis with dynamic TTL
	 * calculated as Duration.between(now, endTime). This ensures cache expires exactly when auction ends.
	 *
	 * <p><strong>Purpose:</strong> When first bid arrives, Bidding Service can validate against
	 * startingPrice without calling Item Service API (~1ms Redis lookup vs ~50ms HTTP call).
	 *
	 * <p><strong>Idempotency:</strong> Tracks processed event IDs in Redis (1-hour TTL) to prevent
	 * duplicate cache writes if event is redelivered.
	 *
	 * <p><strong>Error Handling:</strong>
	 * <ul>
	 *   <li>IllegalArgumentException - Permanent error (invalid event data), rethrown to move to DLQ</li>
	 *   <li>All other exceptions - Transient errors (Redis timeout, network), rethrown to trigger retry</li>
	 * </ul>
	 *
	 * <p><strong>Retry Policy:</strong> Up to 3 attempts with exponential backoff (100ms → 150ms → 225ms).
	 * After max retries, message moves to dead letter queue (BiddingServiceItemEventsQueue.dlq).
	 *
	 * @param event the AuctionStartedEvent containing itemId, startingPrice, endTime, and other details
	 */
	@RabbitListener(queues = "#{itemEventsQueue.name}")
	public void onAuctionStarted(AuctionStartedEvent event) {
		String lockKey = LOCK_KEY_PREFIX + event.eventId();

		Boolean alreadyProcessed = redisTemplate.hasKey(lockKey);
		if (Boolean.TRUE.equals(alreadyProcessed)) {
			log.info("AuctionStartedEvent already processed - eventId: {}, skipping", event.eventId());
			return;
		}

		try {
			auctionCacheService.cacheAuctionMetadata(
				event.data().itemId(),
				event.data().startingPrice(),
				event.data().endTime(),
				ItemStatus.ACTIVE  // Mark as ACTIVE in metadata cache
			);

			redisTemplate.opsForValue().set(lockKey, "1", LOCK_TIMEOUT);
			log.info("AuctionStartedEvent processed successfully - eventId: {}, itemId: {}, startingPrice: {}, status: ACTIVE",
				event.eventId(), event.data().itemId(), event.data().startingPrice());

		} catch (IllegalArgumentException e) {
			log.error("Invalid auction started event - eventId: {}, itemId: {}, reason: {}",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;  // Permanent error - don't retry, move to DLQ
		} catch (Exception e) {
			log.warn("Failed to process auction started event - eventId: {}, itemId: {}, will retry. Reason: {}",
				event.eventId(), event.data().itemId(), e.getMessage());
			throw e;  // Transient error - retry with backoff
		}
	}
}
