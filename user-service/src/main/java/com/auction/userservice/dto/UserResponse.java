package com.auction.userservice.dto;

import com.auction.security.Role;
import com.auction.userservice.models.UserProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for user profile data.
 */
public record UserResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    Role role,
    boolean emailVerified,
    Instant createdAt
) {

  /**
   * Create UserResponse from UserProfile entity.
   */
  public static UserResponse fromEntity(UserProfile user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getRole(),
        user.isEmailVerified(),
        user.getCreatedAt()
    );
  }
}
