package com.auction.biddingservice.services;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCacheService {

	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration LOCK_TIMEOUT = Duration.ofDays(7);
	public static final String AUCTION_ENDTIME_KEY_PREFIX = "auction:endtime:";

	public void markAuctionEnded(Long itemId, Instant endTime) {

		redisTemplate.opsForValue().set(AUCTION_ENDTIME_KEY_PREFIX + itemId, endTime.toString(), LOCK_TIMEOUT);
		log.info("Auction {} ended at {}", itemId, endTime);
	}
	public boolean isAuctionEnded(Long itemId) {
		boolean isEnded = redisTemplate.hasKey(AUCTION_ENDTIME_KEY_PREFIX + itemId);

		if (isEnded) {
			log.debug("Auction {} ended at {}", itemId, redisTemplate.opsForValue().get(AUCTION_ENDTIME_KEY_PREFIX + itemId));
		} else {
			log.debug("Auction {} is still active", itemId);
		}

		return isEnded;
	}
}
