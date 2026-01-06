package com.auction.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a new user registers on the platform.
 *
 * <p>Publisher: User Service
 *
 * <p>Consumers:
 * - Notification Service: Send welcome email/notification
 * - Analytics Service: Track user signups (future)
 *
 * <p>Routing Key Pattern: "user.registered"
 */
public record UserRegisteredEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    UserRegisteredData data
) {

  public static UserRegisteredEvent create(
      UUID userId,
      String email,
      String displayName,
      String role,
      String authProvider
  ) {
    return new UserRegisteredEvent(
        UUID.randomUUID().toString(),
        "UserRegisteredEvent",
        Instant.now(),
        new UserRegisteredData(userId, email, displayName, role, authProvider)
    );
  }

  public record UserRegisteredData(
      UUID userId,
      String email,
      String displayName,
      String role,
      String authProvider
  ) {}
}
