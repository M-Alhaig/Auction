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

	private static final String QUEUE_NAME = "AuctionEndedQueue";
	private static final Duration LOCK_TIMEOUT = Duration.ofHours(1);
	private static final String LOCK_KEY_PREFIX = "lock:event:processed:";

	@RabbitListener(queues = "#{auctionEndedQueue.name}")
	public void onAuctionEnded(AuctionEndedEvent event) {
		String lockKey = LOCK_KEY_PREFIX + event.eventId();

		Boolean alreadyProcessed = redisTemplate.hasKey(lockKey);
		if (Boolean.TRUE.equals(alreadyProcessed)) {
			log.info("Event already processed successfully - eventId: {}, skipping", event.eventId());
			return;
		}

		auctionCacheService.markAuctionEnded(event.data().itemId(), event.data().endTime());

		redisTemplate.opsForValue().set(lockKey, "1", LOCK_TIMEOUT);
		log.info("AuctionEndedEvent processed successfully - eventId: {}", event.eventId());

	}
}
