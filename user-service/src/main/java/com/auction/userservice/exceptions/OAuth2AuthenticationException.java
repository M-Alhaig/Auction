package com.auction.userservice.exceptions;

/**
 * Exception thrown when OAuth2 authentication fails.
 *
 * <p>Covers failures such as:
 * <ul>
 *   <li>Invalid or expired authorization code</li>
 *   <li>Failed token exchange with provider</li>
 *   <li>Failed to fetch user info from provider</li>
 *   <li>Provider returned invalid/incomplete user data</li>
 * </ul>
 *
 * <p>Maps to HTTP 401 Unauthorized.
 */
public class OAuth2AuthenticationException extends RuntimeException {

  public OAuth2AuthenticationException(String message) {
    super(message);
  }

  public OAuth2AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
