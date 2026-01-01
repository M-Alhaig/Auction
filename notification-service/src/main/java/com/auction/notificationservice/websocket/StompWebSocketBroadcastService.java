package com.auction.notificationservice.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * STOMP-based implementation of WebSocketBroadcastService.
 *
 * <p>Uses Spring's SimpMessagingTemplate for WebSocket message delivery via the
 * in-memory simple broker configured in {@link com.auction.notificationservice.config.WebSocketConfiguration}.
 *
 * <p><strong>Topic Destinations:</strong>
 * <ul>
 *   <li>/topic/items/{itemId} - Public bid updates for item watchers</li>
 * </ul>
 *
 * <p><strong>User Destinations:</strong>
 * <ul>
 *   <li>/user/{userId}/queue/alerts - Personal notifications (outbid alerts)</li>
 * </ul>
 *
 * <p><strong>Migration Note:</strong> This implementation can be replaced with an
 * external broker implementation (Redis Pub/Sub, RabbitMQ STOMP) by creating a new
 * class implementing {@link WebSocketBroadcastService} and using @Primary or @Profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StompWebSocketBroadcastService implements WebSocketBroadcastService {

  private final SimpMessagingTemplate messagingTemplate;

  private static final String USER_ALERTS_QUEUE = "/queue/alerts";

  /**
   * Broadcast a message to all subscribers of a topic destination.
   *
   * @param destination the full destination path (e.g., "/topic/items/123")
   * @param payload     the message payload (automatically serialized to JSON)
   */
  @Override
  public void broadcastToTopic(String destination, Object payload) {
    log.debug("Broadcasting to topic - destination: {}", destination);
    messagingTemplate.convertAndSend(destination, payload);
    log.info("Broadcast sent - destination: {}", destination);
  }

  /**
   * Send a message to a specific user's alert queue.
   *
   * <p>Internally routes to /user/{userId}/queue/alerts using Spring's
   * user destination resolution.
   *
   * @param userId  the user identifier (typically UUID as string)
   * @param payload the message payload (automatically serialized to JSON)
   */
  @Override
  public void sendToUser(String userId, Object payload) {
    log.debug("Sending to user - userId: {}, queue: {}", userId, USER_ALERTS_QUEUE);
    messagingTemplate.convertAndSendToUser(userId, USER_ALERTS_QUEUE, payload);
    log.info("Message sent to user - userId: {}", userId);
  }
}
