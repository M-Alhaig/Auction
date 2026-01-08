package com.auction.security;

/**
 * Standard JWT claim names used across all services.
 *
 * <p>These constants ensure consistency in token creation (User Service)
 * and parsing (all services). Using constants prevents typos and enables
 * IDE refactoring support.
 *
 * <p>Note: User ID uses standard JWT "sub" claim via .subject() method,
 * not a custom claim constant.
 *
 * <p>Example usage in JwtTokenProvider:
 * <pre>
 * Jwts.builder()
 *     .subject(user.getId().toString())  // "sub" claim
 *     .claim(JwtClaimNames.EMAIL, user.getEmail())
 *     .claim(JwtClaimNames.ROLE, user.getRole().name())
 *     .claim(JwtClaimNames.EMAIL_VERIFIED, user.isEmailVerified())
 *     .claim(JwtClaimNames.ENABLED, user.isEnabled())
 *     ...
 * </pre>
 */
public final class JwtClaimNames {

  /**
   * User email address.
   * Value: Email string (e.g., "user@example.com").
   */
  public static final String EMAIL = "email";

  /**
   * User role.
   * Value: Role enum name (e.g., "BIDDER", "SELLER", "ADMIN").
   */
  public static final String ROLE = "role";

  /**
   * Email verification status.
   * Value: Boolean (true if email is verified).
   *
   * <p>Used by other services to check if user can perform
   * protected actions (bidding, creating auctions).
   */
  public static final String EMAIL_VERIFIED = "emailVerified";

  /**
   * Account enabled status.
   * Value: Boolean (true if account is enabled).
   *
   * <p>Used by services to reject disabled users even if token is valid.
   * If user is disabled after token issuance, this allows services to
   * deny access until token expires and refresh fails.
   */
  public static final String ENABLED = "enabled";

  private JwtClaimNames() {
    // Prevent instantiation
  }
}
