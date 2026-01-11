package com.auction.userservice.exceptions;

/**
 * Exception thrown when an OAuth account is already linked to a different user.
 *
 * <p>This prevents one OAuth provider account from being linked to multiple users.
 * For example, if Google account "abc123" is already linked to user A,
 * attempting to link it to user B will throw this exception.
 *
 * <p>Maps to HTTP 409 Conflict.
 */
public class OAuth2AccountLinkedException extends RuntimeException {

  public OAuth2AccountLinkedException(String message) {
    super(message);
  }
}
