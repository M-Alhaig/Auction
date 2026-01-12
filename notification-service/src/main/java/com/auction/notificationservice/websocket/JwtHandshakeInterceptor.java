package com.auction.notificationservice.websocket;

import com.auction.security.JwtTokenValidator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket handshake interceptor - validates JWT during initial connection.
 *
 * <p>This is the WebSocket equivalent of JwtAuthenticationFilter for HTTP requests.
 * Runs once when the client first connects (HTTP upgrade handshake).
 *
 * <p>JWT can be provided via:
 * <ul>
 *   <li>Query parameter: ws://host/ws?token=jwt_token (recommended for SockJS)</li>
 *   <li>Authorization header: Bearer jwt_token</li>
 * </ul>
 *
 * <p>The validated token is stored in session attributes for {@link WebSocketAuthChannelInterceptor}.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  public static final String JWT_TOKEN_ATTR = "JWT_TOKEN";
  public static final String USER_ID_ATTR = "USER_ID";

  private final JwtTokenValidator jwtTokenValidator;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler, Map<String, Object> attributes) {
    String token = extractToken(request);

    if (token == null) {
      log.debug("WebSocket handshake - no token provided (anonymous connection)");
      return true; // Allow anonymous for public topics like /topic/items/{id}
    }

    if (!jwtTokenValidator.validateToken(token)) {
      log.warn("WebSocket handshake - invalid or expired JWT");
      return true; // Allow connection but won't receive private notifications
    }

    // Store validated token and userId in session attributes
    String userId = jwtTokenValidator.getUserIdFromToken(token).toString();
    attributes.put(JWT_TOKEN_ATTR, token);
    attributes.put(USER_ID_ATTR, userId);

    log.debug("WebSocket handshake - authenticated userId: {}", userId);
    return true;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
    // No action needed after handshake
  }

  private String extractToken(ServerHttpRequest request) {
    // Try query parameter first (works with SockJS)
    if (request instanceof ServletServerHttpRequest servletRequest) {
      String token = servletRequest.getServletRequest().getParameter("token");
      if (token != null && !token.isBlank()) {
        return token;
      }
    }

    // Try Authorization header (for native WebSocket clients)
    String authHeader = request.getHeaders().getFirst("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    return null;
  }
}
