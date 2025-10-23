package com.auction.itemservice.services;

import com.auction.events.AuctionEndedEvent;
import com.auction.events.AuctionStartedEvent;
import com.auction.itemservice.events.EventPublisher;
import com.auction.itemservice.exceptions.ConcurrentBidException;
import com.auction.itemservice.exceptions.ItemNotFoundException;
import com.auction.itemservice.models.Item;
import com.auction.itemservice.models.ItemStatus;
import com.auction.itemservice.repositories.ItemRepository;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of ItemLifecycleService for managing auction lifecycle. Handles system-driven
 * operations: status transitions, price updates, events.
 *
 * <p>IMPORTANT: Uses self-injection pattern to enable proper transaction proxying.
 * Batch operations call individual auction operations via the 'self' proxy to ensure each auction
 * start/end gets its own transaction boundary for proper error isolation.
 */
@Slf4j
@Service
@Transactional
public class ItemLifecycleServiceImpl implements ItemLifecycleService {

	private final ItemRepository itemRepository;
	private final EventPublisher eventPublisher;
	private final ItemLifecycleService self;
	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
	private static final String LOCK_KEY_PREFIX = "lock:bid:";

	/**
	 * Create an ItemLifecycleServiceImpl with required dependencies and a lazily injected self
	 * reference for proxy-aware internal calls.
	 *
	 * @param itemRepository the repository used to load and persist Item entities
	 * @param eventPublisher the publisher used to emit domain events
	 * @param self           a lazily injected self-reference (proxy) used for internal calls that
	 *                       require proxy-aware behavior (e.g., per-method transactional
	 *                       boundaries)
	 */
	public ItemLifecycleServiceImpl(ItemRepository itemRepository, EventPublisher eventPublisher,
		@Lazy ItemLifecycleService self, RedisTemplate<String, String> redisTemplate) {
		this.itemRepository = itemRepository;
		this.eventPublisher = eventPublisher;
		this.self = self;
		this.redisTemplate = redisTemplate;
	}

	/**
	 * Transition the specified item from PENDING to ACTIVE and publish an AuctionStartedEvent.
	 * <p>
	 * Changes the item's status to ACTIVE, persists the update, and emits an event so downstream
	 * systems can react to the auction start.
	 *
	 * @param itemId the identifier of the item to start
	 * @throws ItemNotFoundException if no item exists with the given id
	 * @throws IllegalStateException if the item's current status is not PENDING
	 */

	@Override
	public void startAuction(Long itemId) {
		log.debug("Starting auction for item: {}", itemId);

		Item item = itemRepository.findById(itemId)
			.orElseThrow(() -> new ItemNotFoundException(itemId));

		if (item.getStatus() != ItemStatus.PENDING) {
			log.warn(
				"Cannot start auction - itemId: {}, currentStatus: {}, expectedStatus: PENDING",
				itemId, item.getStatus());
			throw new IllegalStateException("Item is not in PENDING status");
		}

		item.setStatus(ItemStatus.ACTIVE);
		item = itemRepository.save(item);

		log.info("Auction started - itemId: {}, title: '{}', startTime: {}", itemId,
			item.getTitle(), item.getStartTime());

		publishAuctionStartedEvent(item);
	}

	/**
	 * End the auction for the specified item.
	 * <p>
	 * Sets the item's status to ENDED, persists the change, logs the final price and end time, and
	 * publishes an AuctionEndedEvent.
	 *
	 * @param itemId the identifier of the item whose auction should be ended
	 * @throws ItemNotFoundException if no item exists with the given id
	 * @throws IllegalStateException if the item is not currently in ACTIVE status
	 */
	@Override
	public void endAuction(Long itemId) {
		log.debug("Ending auction for item: {}", itemId);

		Item item = itemRepository.findById(itemId)
			.orElseThrow(() -> new ItemNotFoundException(itemId));

		if (item.getStatus() != ItemStatus.ACTIVE) {
			log.warn("Cannot end auction - itemId: {}, currentStatus: {}, expectedStatus: ACTIVE",
				itemId, item.getStatus());
			throw new IllegalStateException("Item is not in ACTIVE status");
		}

		item.setStatus(ItemStatus.ENDED);
		item = itemRepository.save(item);

		log.info("Auction ended - itemId: {}, title: '{}', finalPrice: {}, endTime: {}", itemId,
			item.getTitle(), item.getCurrentPrice(), item.getEndTime());

		publishAuctionEndedEvent(item);
	}

	/**
	 * Start all pending auctions whose configured start time is now or in the past.
	 * <p>
	 * Processes each eligible item and attempts to transition it to the active state; failures for
	 * individual items are logged and do not stop the batch from continuing.
	 *
	 * @return the number of auctions that were successfully started
	 */

	@Override
	public int batchStartPendingAuctions() {
		List<Item> pendingItems = self.findPendingItemsToStart();

		if (pendingItems.isEmpty()) {
			log.debug("No pending auctions to start");
			return 0;
		}

		log.info("Starting batch auction start - found {} auctions ready to start",
			pendingItems.size());

		int successCount = 0;
		int failureCount = 0;

		for (Item item : pendingItems) {
			try {
				// Call via self-proxy to ensure proper transaction boundary for each auction
				self.startAuction(item.getId());
				successCount++;
			} catch (Exception e) {
				failureCount++;
				log.error("Failed to start auction - itemId: {}, error: {}", item.getId(),
					e.getMessage(), e);
			}
		}

		log.info("Batch auction start completed - total: {}, succeeded: {}, failed: {}",
			pendingItems.size(), successCount, failureCount);

		return successCount;
	}

	/**
	 * Processes active auctions whose end time has passed and attempts to end each auction.
	 * <p>
	 * Attempts to end every expired active auction, logs failures while continuing processing, and
	 * records per-auction success and failure counts.
	 *
	 * @return the number of auctions successfully ended
	 */
	@Override
	public int batchEndExpiredAuctions() {
		List<Item> activeItems = self.findActiveItemsToEnd();

		if (activeItems.isEmpty()) {
			log.debug("No active auctions to end");
			return 0;
		}

		log.info("Starting batch auction end - found {} auctions ready to end", activeItems.size());

		int successCount = 0;
		int failureCount = 0;

		for (Item item : activeItems) {
			try {
				// Call via self-proxy to ensure proper transaction boundary for each auction
				self.endAuction(item.getId());
				successCount++;
			} catch (Exception e) {
				failureCount++;
				log.error("Failed to end auction - itemId: {}, error: {}", item.getId(),
					e.getMessage(), e);
			}
		}

		log.info("Batch auction end completed - total: {}, succeeded: {}, failed: {}",
			activeItems.size(), successCount, failureCount);

		return successCount;
	}

	/**
	 * Update an item's current price and winner while preventing concurrent bid races using a distributed
	 * lock.
	 *
	 * <p>Acquires a Redis-based distributed lock for the item, validates that `newPrice` is greater than
	 * the current price, updates both price and winnerId, persists the change, and releases the lock.
	 *
	 * @param itemId   the id of the item whose current price should be updated
	 * @param winnerId the UUID of the bidder who placed the highest bid
	 * @param newPrice the candidate new current price; must be greater than the item's current price
	 * @throws ItemNotFoundException     if the item does not exist
	 * @throws ConcurrentBidException    if the Redis lock cannot be acquired (retry suggested)
	 * @throws IllegalArgumentException  if newPrice is not greater than the current price
	 */

	@Override
	public void updateCurrentPriceWithLock(Long itemId, UUID winnerId, BigDecimal newPrice) {
		log.info("updateCurrentPriceWithLock - itemId: {}, winnerId: {}, newPrice: {}", itemId, winnerId, newPrice);

		String lockKey = LOCK_KEY_PREFIX + itemId;
		String lockToken = UUID.randomUUID().toString();

		Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, LOCK_TIMEOUT);

		if (!Boolean.TRUE.equals(lockAcquired)) {
			log.warn("Failed to acquire lock for itemId: {}", itemId);
			throw new ConcurrentBidException("Another bid is being processed. Please retry.");
		}

		try {
			log.debug("updateCurrentPriceWithLock - lock acquired for itemId: {}", itemId);
			Item item = itemRepository.findById(itemId)
				.orElseThrow(() -> new ItemNotFoundException(itemId));

			// Defensive validation: ensure new price is actually higher
			if (newPrice.compareTo(item.getCurrentPrice()) <= 0) {
				log.warn("Invalid price update attempt - itemId: {}, currentPrice: {}, attemptedPrice: {}",
					itemId, item.getCurrentPrice(), newPrice);
				throw new IllegalArgumentException(
					"New price must be greater than current price. Current: " + item.getCurrentPrice() +
						", Attempted: " + newPrice
				);
			}

			item.setCurrentPrice(newPrice);
			item.setWinnerId(winnerId);
			itemRepository.save(item);
			log.info("updateCurrentPriceWithLock - updated currentPrice and winnerId for itemId: {} to {}, winner: {}",
				itemId, newPrice, winnerId);
		} finally {
			// Atomic lock release using Lua script to prevent race condition:
			// If we used GET + compare + DELETE, the lock could expire between GET and DELETE,
			// causing us to delete another thread's lock. Lua scripts execute atomically on Redis.
			String unlockScript = """
				if redis.call('get', KEYS[1]) == ARGV[1] then
				\treturn redis.call('del', KEYS[1])
				else
				\treturn 0
				end
				""";

			Long result = redisTemplate.execute(
				RedisScript.of(unlockScript, Long.class),
				Collections.singletonList(lockKey),
				lockToken
			);

			if (Long.valueOf(1L).equals(result)) {
				log.debug("updateCurrentPriceWithLock - lock released atomically for itemId: {}", itemId);
			} else {
				log.warn(
					"updateCurrentPriceWithLock - lock expired or was re-acquired by another process. Did not release. itemId: {}",
					itemId);
			}
		}
	}

	/**
	 * Check whether the specified item is currently active.
	 *
	 * @param itemId the database identifier of the item to check
	 * @return `true` if the item is active, `false` otherwise
	 */

	@Override
	@Transactional(readOnly = true)
	public boolean isItemActive(Long itemId) {
		log.debug("Checking if item is active: {}", itemId);
		return itemRepository.isItemActiveById(itemId);
	}

	/**
	 * Retrieve items with status PENDING whose start time is now or earlier.
	 *
	 * @return a list of Item entities in PENDING status with startTime <= now; an empty list if
	 * none are found
	 */

	@Override
	@Transactional(readOnly = true)
	public List<Item> findPendingItemsToStart() {
		log.debug("Finding pending items ready to start");
		return itemRepository.findByStatusAndStartTimeLessThanEqual(ItemStatus.PENDING,
			Instant.now());
	}

	/**
	 * Retrieve active items whose end time has passed.
	 *
	 * @return a list of items in `ACTIVE` status with `endTime` before the current time; empty list
	 * if none
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Item> findActiveItemsToEnd() {
		log.debug("Finding active items ready to end");
		return itemRepository.findByStatusAndEndTimeBefore(ItemStatus.ACTIVE, Instant.now());
	}

	// ==================== PRIVATE HELPER METHODS ====================

	/**
	 * Publish an AuctionStartedEvent for the given item to notify downstream services.
	 *
	 * @param item the item whose auction has started; its id, seller, title, start time, and
	 *             starting price are included in the event payload
	 */
	private void publishAuctionStartedEvent(Item item) {
		AuctionStartedEvent event = AuctionStartedEvent.create(item.getId(), item.getSellerId(),
			item.getTitle(), item.getStartTime(), item.getEndTime(), item.getStartingPrice());

		eventPublisher.publish(event);

		log.debug(
			"Published AuctionStartedEvent - itemId: {}, title: '{}', startingPrice: {}, endTime: {}, eventId: {}",
			event.data().itemId(), event.data().title(), event.data().startingPrice(),
			event.data().endTime(), event.eventId());
	}

	/**
	 * Publish an AuctionEndedEvent for the given item to notify downstream services.
	 * <p>
	 * The published event carries the item's id, seller id, title, end time, final price, and winnerId
	 * (the UUID of the highest bidder, or null if no bids were placed).
	 *
	 * @param item the item whose auction has ended; its end time, final price, and winnerId will be
	 *             included in the event
	 */
	private void publishAuctionEndedEvent(Item item) {
		AuctionEndedEvent event = AuctionEndedEvent.create(
			item.getId(),
			item.getSellerId(),
			item.getTitle(),
			item.getEndTime(),
			item.getCurrentPrice(),
			item.getWinnerId()  // Now properly tracked via BidPlacedEvent consumption
		);

		eventPublisher.publish(event);

		log.debug(
			"Published AuctionEndedEvent - itemId: {}, title: '{}', finalPrice: {}, winnerId: {}, eventId: {}",
			event.data().itemId(), event.data().title(), event.data().finalPrice(),
			event.data().winnerId(), event.eventId());
	}
}
