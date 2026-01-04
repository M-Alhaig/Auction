package com.auction.notificationservice.listener;

import com.auction.events.BidPlacedEvent;
import com.auction.events.UserOutbidEvent;
import com.auction.notificationservice.dto.BidUpdatePayload;
import com.auction.notificationservice.dto.OutbidAlertPayload;
import com.auction.notificationservice.model.NotificationType;
import com.auction.notificationservice.service.NotificationService;
import com.auction.notificationservice.websocket.WebSocketBroadcastService;
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
 * Listener for bidding events from RabbitMQ.
 *
 * <p><strong>Events Handled:</strong>
 * <ul>
 *   <li>BidPlacedEvent - Broadcasts to /topic/items/{itemId} (WebSocket only, no DB)</li>
 *   <li>UserOutbidEvent - Sends to /user/{oldBidderId}/queue/alerts + persists notification</li>
 * </ul>
 *
 * <p><strong>Idempotency Pattern (Three-State Machine):</strong>
 * <ul>
 *   <li><strong>nil/failed</strong> → Claim as "processing", execute handler</li>
 *   <li><strong>processing</strong> → Another instance is handling it, throw to retry later</li>
 *   <li><strong>completed</strong> → Already processed successfully, skip (no exception)</li>
 * </ul>
 *
 * <p>This pattern prevents duplicate notifications when RabbitMQ redelivers messages
 * (e.g., after consumer crash, network issues, or manual requeue).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "#{biddingEventsQueue.name}")
public class BiddingEventListener {

  private final WebSocketBroadcastService webSocketBroadcastService;
  private final NotificationService notificationService;
  private final RedisTemplate<String, String> redisTemplate;

  private static final Duration STATE_TTL = Duration.ofHours(1);
  private static final String STATE_KEY_PREFIX = "event:state:notification:";
  private static final String STATE_PROCESSING = "processing";
  private static final String STATE_COMPLETED = "completed";
  private static final String STATE_FAILED = "failed";


  // ==================== EVENT HANDLERS (Entry Points) ====================

  /**
   * Handle BidPlacedEvent - broadcast new bid to all watchers of the item.
   * Does NOT persist to database (high-volume, WebSocket-only broadcast).
   */
  @RabbitHandler
  public void handleBidPlaced(BidPlacedEvent event) {
    handleEvent(event.eventId(), "BidPlacedEvent", () -> processBidPlaced(event));
  }

  /**
   * Handle UserOutbidEvent - notify the outbid user via WebSocket AND persist notification.
   */
  @RabbitHandler
  public void handleUserOutbid(UserOutbidEvent event) {
    handleEvent(event.eventId(), "UserOutbidEvent", () -> processUserOutbid(event));
  }


  // ==================== THREE-STATE IDEMPOTENCY LOGIC ====================

  /**
   * Centralized idempotency logic using Redis Lua script.
   *
   * <p><strong>State Transitions:</strong>
   * <pre>
   * nil → "processing" → (execute) → "completed" (success)
   *                                    → "failed" (transient error, allows retry)
   *                                    → stays "processing" (permanent error, goes to DLQ)
   * "failed" → "processing" → (retry the handler)
   * "processing" → throw exception (another instance is handling it)
   * "completed" → skip silently (already done)
   * </pre>
   *
   * @param eventId   the unique event identifier for idempotency tracking
   * @param eventType the event type name for logging
   * @param handler   the business logic to execute
   */
  private void handleEvent(String eventId, String eventType, Runnable handler) {
    String stateKey = STATE_KEY_PREFIX + eventId;

    // Lua script for atomic state claim
    // Returns: "completed", "processing", or "claimed"
    String claimScript = """
        local current = redis.call('get', KEYS[1])
        if current == 'completed' then
            return 'completed'
        elseif current == 'processing' then
            return 'processing'
        else
            -- current is 'failed' or nil - claim as 'processing'
            redis.call('set', KEYS[1], 'processing', 'EX', ARGV[1])
            return 'claimed'
        end
        """;

    String result = redisTemplate.execute(
        RedisScript.of(claimScript, String.class),
        Collections.singletonList(stateKey),
        String.valueOf(STATE_TTL.toSeconds())
    );

    if (STATE_COMPLETED.equals(result)) {
      log.info("Event already processed - eventId: {}, type: {}, skipping", eventId, eventType);
      return; // No exception - RabbitMQ will ACK the message
    }

    if (STATE_PROCESSING.equals(result)) {
      log.warn("Event being processed by another instance - eventId: {}, type: {}, will retry",
          eventId, eventType);
      throw new ConcurrentEventProcessingException(
          "Event is being processed concurrently, retry later");
    }

    // result == "claimed" - we successfully claimed it
    log.info("Processing event - eventId: {}, type: {}", eventId, eventType);

    try {
      handler.run();

      // SUCCESS: Mark as completed
      redisTemplate.opsForValue().set(stateKey, STATE_COMPLETED, STATE_TTL);
      log.info("Event processed successfully - eventId: {}, type: {}", eventId, eventType);

    } catch (IllegalArgumentException e) {
      // Permanent error - leave as "processing" so it goes to DLQ after retries
      log.error("Permanent error processing event - eventId: {}, type: {}, reason: {}",
          eventId, eventType, e.getMessage());
      throw e;

    } catch (Exception e) {
      // Transient error - mark as "failed" to allow retry
      redisTemplate.opsForValue().set(stateKey, STATE_FAILED, STATE_TTL);
      log.warn("Transient error processing event - eventId: {}, type: {}, reason: {}",
          eventId, eventType, e.getMessage());
      throw e;
    }
  }

  // ==================== BUSINESS LOGIC (Called by idempotency wrapper) ====================

  private void processBidPlaced(BidPlacedEvent event) {
    Long itemId = event.data().itemId();
    String destination = "/topic/items/" + itemId;

    BidUpdatePayload payload = BidUpdatePayload.fromEvent(
        itemId,
        event.data().bidderId(),
        event.data().bidAmount(),
        event.data().bidTimestamp()
    );

    webSocketBroadcastService.broadcastToTopic(destination, payload);

    log.info("Broadcast BidPlacedEvent - itemId: {}, amount: {}, eventId: {}",
        itemId, event.data().bidAmount(), event.eventId());
  }

  private void processUserOutbid(UserOutbidEvent event) {
    Long itemId = event.data().itemId();
    String oldBidderId = event.data().oldBidderId().toString();

    // 1. Send WebSocket alert to the outbid user
    OutbidAlertPayload payload = OutbidAlertPayload.fromEvent(itemId, event.data().newAmount());
    webSocketBroadcastService.sendToUser(oldBidderId, payload);

    // 2. Persist notification for users who may not be connected
    notificationService.createNotification(
        event.data().oldBidderId(),
        itemId,
        NotificationType.OUTBID,
        payload.title(),
        payload.message()
    );

    log.info("Processed UserOutbidEvent - itemId: {}, outbidUser: {}, newAmount: {}, eventId: {}",
        itemId, oldBidderId, event.data().newAmount(), event.eventId());
  }
}
