package com.auction.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time auction notifications using STOMP protocol.
 *
 * <p><strong>Client Connection Flow:</strong>
 * <pre>
 * 1. Client connects to WebSocket endpoint: ws://localhost:8084/ws
 * 2. Client subscribes to topics:
 *    - /topic/items/{itemId} - Public updates for specific auction items (new highest bid)
 *    - /user/queue/alerts - Personal notifications (outbid alerts, auction won)
 * 3. Server pushes messages to subscribed clients in real-time
 * </pre>
 *
 * <p><strong>Topic Design:</strong>
 * <ul>
 *   <li><strong>/topic/items/{itemId}</strong> - Broadcast to all users watching a specific auction
 *       <br>Example: "New highest bid: $500 by user123"</li>
 *   <li><strong>/user/queue/alerts</strong> - Private notifications to specific users
 *       <br>Example: "You've been outbid on 'Vintage Watch'"</li>
 * </ul>
 *
 * <p><strong>Message Flow:</strong>
 * <pre>
 * RabbitMQ Event → EventListener → SimpMessagingTemplate → WebSocket → Connected Clients
 * </pre>
 *
 * <p><strong>STOMP Configuration:</strong>
 * <ul>
 *   <li>Endpoint: /ws (with SockJS fallback for older browsers)</li>
 *   <li>Application prefix: /app (for client-to-server messages)</li>
 *   <li>Broker prefixes: /topic (public), /queue (private)</li>
 * </ul>
 *
 * @see org.springframework.messaging.simp.SimpMessagingTemplate for sending messages
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

  private final ObjectMapper objectMapper;

  public WebSocketConfiguration(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Configures the message broker for routing messages between clients.
   *
   * <p><strong>Broker Prefixes:</strong>
   * <ul>
   *   <li>/topic - Public broadcasts (one-to-many)</li>
   *   <li>/queue - Private messages (one-to-one)</li>
   * </ul>
   *
   * <p><strong>Application Prefix:</strong>
   * <ul>
   *   <li>/app - Prefix for messages sent from clients to server
   *       (not heavily used in this service since notifications are server-initiated)</li>
   * </ul>
   *
   * <p><strong>User Destination Prefix:</strong>
   * <ul>
   *   <li>/user - Prefix for user-specific destinations (e.g., /user/{username}/queue/alerts)</li>
   * </ul>
   *
   * @param registry the message broker registry
   */
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // Enable simple in-memory message broker with /topic and /queue prefixes
    registry.enableSimpleBroker("/topic", "/queue");

    // Prefix for application-bound messages (client → server)
    registry.setApplicationDestinationPrefixes("/app");

    // Prefix for user-specific destinations
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(objectMapper);
    messageConverters.add(converter);  // Use the configured converter with JavaTimeModule
    return false;
  }

  /**
   * Registers STOMP endpoints that clients use to connect to the WebSocket server.
   *
   * <p><strong>Endpoint Details:</strong>
   * <ul>
   *   <li>Path: /ws</li>
   *   <li>CORS: Allowed from all origin patterns (configure for production!)</li>
   *   <li>SockJS: Enabled as fallback for browsers without WebSocket support</li>
   * </ul>
   *
   * <p><strong>Client Connection Example (JavaScript):</strong>
   * <pre>{@code
   * const socket = new SockJS('http://localhost:8084/ws');
   * const stompClient = Stomp.over(socket);
   * stompClient.connect({}, () => {
   *   // Subscribe to public auction updates
   *   stompClient.subscribe('/topic/items/123', (message) => {
   *     console.log('New bid:', JSON.parse(message.body));
   *   });
   *
   *   // Subscribe to personal notifications
   *   stompClient.subscribe('/user/queue/alerts', (message) => {
   *     console.log('Alert:', JSON.parse(message.body));
   *   });
   * });
   * }</pre>
   *
   * @param registry the STOMP endpoint registry
   */
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*")  // TODO: Configure specific origins for production
        .withSockJS();  // Enable SockJS fallback for older browsers
  }
}
