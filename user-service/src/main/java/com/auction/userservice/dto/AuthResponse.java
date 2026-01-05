package com.auction.userservice.dto;

/**
 * Response DTO for authentication (login/register/refresh).
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserResponse user
) {

  /**
   * Create AuthResponse with Bearer token type.
   */
  public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
    return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
  }
}
