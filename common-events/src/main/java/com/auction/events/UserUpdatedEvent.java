package com.auction.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a user updates their profile.
 *
 * <p>Publisher: User Service
 *
 * <p>Consumers:
 * - Item Service: Update cached seller display names (future)
 * - Notification Service: Notify relevant parties of profile changes (future)
 *
 * <p>Routing Key Pattern: "user.updated"
 */
public record UserUpdatedEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    UserUpdatedData data
) {

  public static UserUpdatedEvent create(
      UUID userId,
      String displayName,
      String avatarUrl,
      String role
  ) {
    return new UserUpdatedEvent(
        UUID.randomUUID().toString(),
        "UserUpdatedEvent",
        Instant.now(),
        new UserUpdatedData(userId, displayName, avatarUrl, role)
    );
  }

  public record UserUpdatedData(
      UUID userId,
      String displayName,
      String avatarUrl,
      String role
  ) {}
}
