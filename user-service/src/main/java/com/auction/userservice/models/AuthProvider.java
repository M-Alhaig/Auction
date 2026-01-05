package com.auction.userservice.models;

/**
 * Authentication providers supported by the platform.
 *
 * <p>Each user can have multiple credentials linked to different providers,
 * enabling flexible authentication options (e.g., local + Google + GitHub).
 */
public enum AuthProvider {

  /**
   * Local email/password authentication.
   * Credentials stored as BCrypt-hashed passwords in auth_credentials table.
   */
  LOCAL,

  /**
   * Google OAuth2 authentication.
   * Provider ID is Google's unique user identifier (sub claim).
   */
  GOOGLE,

  /**
   * GitHub OAuth2 authentication.
   * Provider ID is GitHub's unique user identifier.
   */
  GITHUB
}
