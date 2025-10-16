package com.auction.biddingservice.services;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for caching ended auction states in Redis.
 *
 * <p>This service provides fast (sub-millisecond) lookups to determine if an auction
 * has ended, preventing bids on closed auctions without querying Item Service or database.
 *
 * <p><strong>Cache Strategy:</strong>
 * <ul>
 *   <li>Key pattern: "auction:endtime:{itemId}"</li>
 *   <li>Value: ISO-8601 formatted end timestamp</li>
 *   <li>TTL: 7 days (prevents indefinite growth, long enough to reject late bids)</li>
 * </ul>
 *
 * <p><strong>Performance:</strong>
 * <ul>
 *   <li>Redis lookup: ~0.1ms (vs. 1-5ms REST call to Item Service)</li>
 *   <li>Scalable: Handles millions of lookups per second</li>
 *   <li>Decoupled: No dependency on Item Service availability</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCacheService {

	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration CACHE_TTL = Duration.ofDays(7);
	public static final String AUCTION_ENDTIME_KEY_PREFIX = "auction:endtime:";

	/**
	 * Marks an auction as ended in Redis cache with the given end time.
	 *
	 * <p>Stores the end time with 7-day TTL. After 7 days, the cache entry expires
	 * automatically, preventing indefinite Redis memory growth.
	 *
	 * @param itemId the auction item ID to mark as ended
	 * @param endTime the instant when the auction ended (stored as ISO-8601 string)
	 */
	public void markAuctionEnded(Long itemId, Instant endTime) {
		redisTemplate.opsForValue().set(AUCTION_ENDTIME_KEY_PREFIX + itemId, endTime.toString(), CACHE_TTL);
		log.info("Auction {} marked as ended in cache - endTime: {}", itemId, endTime);
	}

	/**
	 * Checks if an auction has ended by querying the Redis cache.
	 *
	 * <p>This is a fast path check (sub-millisecond) used before processing bids
	 * to immediately reject bids on ended auctions without acquiring locks or
	 * querying databases.
	 *
	 * @param itemId the auction item ID to check
	 * @return true if the auction has ended (cache entry exists), false otherwise
	 */
	public boolean isAuctionEnded(Long itemId) {
		boolean isEnded = Boolean.TRUE.equals(redisTemplate.hasKey(AUCTION_ENDTIME_KEY_PREFIX + itemId));

		if (isEnded) {
			log.debug("Auction {} is ended (cached)", itemId);
		} else {
			log.debug("Auction {} is active (not in ended cache)", itemId);
		}

		return isEnded;
	}

	/**
	 * Retrieves the end time of an auction from the Redis cache.
	 *
	 * <p>Returns the actual timestamp when the auction ended, or null if the
	 * auction is not in the ended cache (either still active or cache expired).
	 *
	 * @param itemId the auction item ID
	 * @return the Instant when the auction ended, or null if not found in cache
	 */
	public Instant getAuctionEndTime(Long itemId) {
		String endTimeStr = redisTemplate.opsForValue().get(AUCTION_ENDTIME_KEY_PREFIX + itemId);

		if (endTimeStr != null) {
			Instant endTime = Instant.parse(endTimeStr);
			log.debug("Retrieved auction {} end time from cache: {}", itemId, endTime);
			return endTime;
		}

		log.debug("No end time found in cache for auction {}", itemId);
		return null;
	}
}
