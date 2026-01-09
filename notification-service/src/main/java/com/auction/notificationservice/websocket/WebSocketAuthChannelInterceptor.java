package com.auction.notificationservice.websocket;

import com.auction.security.AuthenticatedUser;
import com.auction.security.JwtTokenValidator;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.Nullable;

/**
 * STOMP channel interceptor - the "aloo" handler.
 *
 * <p>When client sends STOMP CONNECT (first message after WebSocket opens),
 * this interceptor reads the JWT from session attributes and creates a Principal.
 * Spring uses Principal.getName() to route /user/queue/* messages.
 *
 * <p>Only acts on CONNECT - all other STOMP frames pass through unchanged
 * since the Principal is already set for the session.
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

  private final JwtTokenValidator jwtTokenValidator;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
      return message; // Not CONNECT - pass through, Principal already set
    }

    // Get session attributes from HandshakeInterceptor
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes == null) {
      log.debug("STOMP CONNECT - no session attributes (anonymous)");
      return message;
    }

    String token = (String) sessionAttributes.get(JwtHandshakeInterceptor.JWT_TOKEN_ATTR);
    String userId = (String) sessionAttributes.get(JwtHandshakeInterceptor.USER_ID_ATTR);

    if (token == null || userId == null) {
      log.debug("STOMP CONNECT - no JWT in session (anonymous user)");
      return message;
    }

    // Build AuthenticatedUser from JWT claims
    AuthenticatedUser user = AuthenticatedUser.fromJwt(
        UUID.fromString(userId),
        jwtTokenValidator.getEmailFromToken(token),
        jwtTokenValidator.getRoleFromToken(token),
        jwtTokenValidator.isEmailVerifiedFromToken(token),
        jwtTokenValidator.isEnabledFromToken(token)
    );

    // Create Principal - getName() returns userId for Spring's user destination routing
    Principal principal = new WebSocketPrincipal(userId, user);
    accessor.setUser(principal);

    log.info("STOMP CONNECT - authenticated userId: {}, email: {}", userId, user.getEmail());
    return message;
  }

  /**
   * Principal that wraps AuthenticatedUser.
   * getName() returns userId - this is what Spring uses to route /user/queue/* messages.
   */
  @RequiredArgsConstructor
  public static class WebSocketPrincipal implements Principal {
    private final String userId;
    private final AuthenticatedUser authenticatedUser;

    @Override
    public String getName() {
      return userId; // Used by convertAndSendToUser(userId, ...) routing
    }

    public AuthenticatedUser getAuthenticatedUser() {
      return authenticatedUser;
    }
  }
}
