package com.auction.security;

/**
 * Shared authentication constants used across all services.
 *
 * <p>Centralizing these constants ensures consistency in:
 * <ul>
 *   <li>HTTP header names</li>
 *   <li>Token formats</li>
 *   <li>Spring Security role prefixes</li>
 * </ul>
 */
public final class AuthConstants {

  /**
   * HTTP header name for JWT authentication.
   * Value: "Authorization"
   */
  public static final String AUTH_HEADER = "Authorization";

  /**
   * Prefix for Bearer token authentication.
   * Value: "Bearer " (note the trailing space).
   *
   * <p>Full header format: "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR..."
   */
  public static final String BEARER_PREFIX = "Bearer ";

  /**
   * Spring Security role prefix.
   * Value: "ROLE_"
   *
   * <p>Spring Security prefixes roles with "ROLE_" internally.
   * hasRole('ADMIN') checks for authority "ROLE_ADMIN".
   */
  public static final String ROLE_PREFIX = "ROLE_";

  private AuthConstants() {
    // Prevent instantiation
  }
}
