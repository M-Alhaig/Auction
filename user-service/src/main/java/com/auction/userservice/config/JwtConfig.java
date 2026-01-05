package com.auction.userservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration properties.
 *
 * <p>Configure via application.properties:
 * <pre>
 * jwt.secret=your-base64-encoded-secret-key-at-least-256-bits
 * jwt.access-token-expiration-ms=900000     # 15 minutes
 * jwt.refresh-token-expiration-ms=604800000 # 7 days
 * </pre>
 *
 * <p>The secret must be Base64-encoded and at least 256 bits for HS256.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtConfig {

  /**
   * Base64-encoded secret key for signing JWTs.
   * Must be at least 256 bits (32 bytes) for HS256.
   *
   * <p>Generate with: {@code openssl rand -base64 32}
   */
  @NotBlank(message = "JWT secret is required")
  private String secret;

  /**
   * Access token expiration time in milliseconds.
   * Default: 15 minutes (900000 ms).
   *
   * <p>Short-lived for security. Client refreshes using refresh token.
   */
  @Positive
  private long accessTokenExpirationMs = 900_000L; // 15 minutes

  /**
   * Refresh token expiration time in milliseconds.
   * Default: 7 days (604800000 ms).
   *
   * <p>Longer-lived, stored securely. Used to obtain new access tokens.
   */
  @Positive
  private long refreshTokenExpirationMs = 604_800_000L; // 7 days
}
