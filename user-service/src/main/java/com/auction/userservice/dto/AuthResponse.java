package com.auction.userservice.dto;

import java.util.UUID;

/**
 * Response DTO for authentication (login/register/refresh).
 */
public record AuthResponse(
    String accessToken,
    UUID refreshTokenId,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserResponse user
) {

  /**
   * Create AuthResponse with Bearer token type.
   */
  public static AuthResponse of(String accessToken, UUID refreshTokenId, String refreshToken,
                                long expiresIn, UserResponse user) {
    return new AuthResponse(accessToken, refreshTokenId, refreshToken, "Bearer", expiresIn, user);
  }
}
