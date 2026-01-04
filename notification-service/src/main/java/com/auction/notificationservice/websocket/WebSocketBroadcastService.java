package com.auction.notificationservice.websocket;

/**
 * Abstraction for WebSocket broadcast operations.
 *
 * <p>Decouples notification business logic from the specific WebSocket implementation.
 * Enables future migration to external message brokers without changing event listeners.
 *
 * <p><strong>Current Implementation:</strong> {@link StompWebSocketBroadcastService}
 * (uses Spring's SimpMessagingTemplate with in-memory simple broker)
 *
 * <p><strong>Future Implementations (Migration Path):</strong>
 * <ul>
 *   <li>RedisPubSubBroadcastService - Redis Pub/Sub for multi-instance support</li>
 *   <li>RabbitStompBroadcastService - RabbitMQ's STOMP plugin for external broker</li>
 *   <li>AwsApiGatewayBroadcastService - AWS API Gateway WebSocket</li>
 * </ul>
 *
 * <p><strong>Design Pattern:</strong> Similar to EventPublisher interface in bidding-service,
 * which abstracts RabbitMQ and enables SQS migration.
 */
public interface WebSocketBroadcastService {

  /**
   * Broadcast a message to all subscribers of a topic.
   *
   * <p>Used for public notifications that all watchers should receive,
   * such as new bid updates on an auction item.
   *
   * @param destination the topic destination (e.g., "/topic/items/123")
   * @param payload     the message payload to broadcast (will be JSON serialized)
   */
  void broadcastToTopic(String destination, Object payload);

  /**
   * Send a message to a specific user's private queue.
   *
   * <p>Used for personal notifications that only one user should receive,
   * such as "you've been outbid" alerts.
   *
   * @param userId  the user identifier (e.g., UUID string)
   * @param payload the message payload to send (will be JSON serialized)
   */
  void sendToUser(String userId, Object payload);
}
