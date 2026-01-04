package com.auction.biddingservice.listeners;

import com.auction.events.AuctionEndedEvent;
import com.auction.events.AuctionStartedEvent;
import com.auction.events.AuctionTimesUpdatedEvent;
import com.auction.biddingservice.exceptions.ConcurrentEventProcessingException;
import com.auction.biddingservice.models.ItemStatus;
import com.auction.biddingservice.services.AuctionCacheService;
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
 * Listener for all Item Service lifecycle events consumed via a single queue.
 *
 * <p><strong>Events Handled:</strong>
 * <ul>
 *   <li>{@link AuctionStartedEvent} - Caches auction metadata (startingPrice + endTime) for first bid validation</li>
 *   <li>{@link AuctionEndedEvent} - Marks auction as ended to reject late bids</li>
 *   <li>{@link AuctionTimesUpdatedEvent} - Invalidates cached metadata when seller changes auction times</li>
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
 * <p><strong>Queue Strategy:</strong> Single queue (BiddingServiceItemEventsQueue) bound with
 * wildcard pattern "item.*" receives all item lifecycle events.
 *
 * <p><strong>Design Rationale:</strong> Single queue + single listener because:
 * <ul>
 *   <li>Same producer (Item Service) → Same consumer (Bidding Service)</li>
 *   <li>Same retry policy and error handling</li>
 *   <li>Same idempotency requirements</li>
 *   <li>Simpler infrastructure (1 queue + 1 DLQ instead of 3 queues + 3 DLQs)</li>
 *   <li>Centralized idempotency logic (no duplication)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "#{itemEventsQueue.name}")
public class AuctionEventListener {

  private final AuctionCacheService auctionCacheService;
  private final RedisTemplate<String, String> redisTemplate;

  private static final Duration LOCK_TIMEOUT = Duration.ofHours(1);
  private static final String LOCK_KEY_PREFIX = "event:state:bid:";

  // Event processing states (three-state idempotency machine)
  private static final String STATE_PROCESSING = "processing";
  private static final String STATE_COMPLETED = "completed";
  private static final String STATE_FAILED = "failed";

  /**
   * Handles AuctionStartedEvent with centralized three-state idempotency logic.
   *
   * @param event the AuctionStartedEvent from Item Service
   */
  @RabbitHandler
  public void handleAuctionStarted(AuctionStartedEvent event) {
    handleEvent(event.eventId(), "AuctionStartedEvent", () -> processAuctionStarted(event));
  }

  /**
   * Handles AuctionEndedEvent with centralized three-state idempotency logic.
   *
   * @param event the AuctionEndedEvent from Item Service
   */
  @RabbitHandler
  public void handleAuctionEnded(AuctionEndedEvent event) {
    handleEvent(event.eventId(), "AuctionEndedEvent", () -> processAuctionEnded(event));
  }

  /**
   * Handles AuctionTimesUpdatedEvent with centralized three-state idempotency logic.
   *
   * @param event the AuctionTimesUpdatedEvent from Item Service
   */
  @RabbitHandler
  public void handleAuctionTimesUpdated(AuctionTimesUpdatedEvent event) {
    handleEvent(event.eventId(), "AuctionTimesUpdatedEvent",
        () -> processAuctionTimesUpdated(event));
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
   * @param eventId   the unique event identifier for idempotency tracking
   * @param eventType the event type name for logging
   * @param handler   the business logic to execute
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

    String result = redisTemplate.execute(RedisScript.of(claimScript, String.class),
        Collections.singletonList(stateKey), String.valueOf(LOCK_TIMEOUT.toSeconds()));

    if (STATE_COMPLETED.equals(result)) {
      log.info("Event already processed successfully - eventId: {}, type: {}, skipping", eventId,
          eventType);
      return; // No exception - RabbitMQ ACKs
    }

    if (STATE_PROCESSING.equals(result)) {
      log.warn(
          "Event is currently being processed by another instance - eventId: {}, type: {}, will retry",
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

    } catch (IllegalArgumentException e) {
      // Permanent error - leave as "processing", goes to DLQ after retries
      log.error(
          "Permanent error processing event - eventId: {}, type: {}, reason: {}. Will be moved to DLQ.",
          eventId, eventType, e.getMessage());
      throw e;

    } catch (Exception e) {
      // Transient error - mark as "failed" to allow retry
      redisTemplate.opsForValue().set(stateKey, STATE_FAILED, LOCK_TIMEOUT);
      log.warn(
          "Transient error processing event - eventId: {}, type: {}, reason: {}. Marked as 'failed' for retry...",
          eventId, eventType, e.getMessage());
      throw e;
    }
  }

  // ==================== BUSINESS LOGIC HANDLERS (No idempotency concerns) ====================

  /**
   * Processes AuctionStartedEvent - caches auction metadata for bid validation.
   */
  private void processAuctionStarted(AuctionStartedEvent event) {
    auctionCacheService.cacheAuctionMetadata(event.data().itemId(), event.data().startingPrice(),
        event.data().endTime(), ItemStatus.ACTIVE);
    log.info("Cached auction metadata - itemId: {}, startingPrice: {}, status: ACTIVE",
        event.data().itemId(), event.data().startingPrice());
  }

  /**
   * Processes AuctionEndedEvent - marks auction as ended in cache.
   */
  private void processAuctionEnded(AuctionEndedEvent event) {
    // Mark auction as ended in dedicated ended-flag cache
    auctionCacheService.markAuctionEnded(event.data().itemId(), event.data().endTime());

    // Update metadata cache status to ENDED with startingPrice from event (self-contained event)
    auctionCacheService.cacheAuctionMetadata(event.data().itemId(),
        event.data().startingPrice(), event.data().endTime(), ItemStatus.ENDED);

    log.info("Marked auction as ended - itemId: {}, startingPrice: {}, finalPrice: {}, status: ENDED",
        event.data().itemId(), event.data().startingPrice(), event.data().finalPrice());
  }

  /**
   * Processes AuctionTimesUpdatedEvent - invalidates cached metadata.
   */
  private void processAuctionTimesUpdated(AuctionTimesUpdatedEvent event) {
    String cacheKey = AuctionCacheService.AUCTION_METADATA_KEY_PREFIX + event.data().itemId();
    Boolean deleted = redisTemplate.delete(cacheKey);

    if (Boolean.TRUE.equals(deleted)) {
      log.info("Cache invalidated - itemId: {}, cacheKey: {}", event.data().itemId(), cacheKey);
    } else {
      log.debug("Cache key already absent - itemId: {} (no-op)", event.data().itemId());
    }

    log.info(
        "Processed time update - itemId: {}, oldStart: {}, newStart: {}, oldEnd: {}, newEnd: {}",
        event.data().itemId(), event.data().oldStartTime(), event.data().newStartTime(),
        event.data().oldEndTime(), event.data().newEndTime());
  }
}
