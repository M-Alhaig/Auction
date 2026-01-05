package com.auction.security;

/**
 * Standard JWT claim names used across all services.
 *
 * <p>These constants ensure consistency in token creation (User Service)
 * and parsing (all services). Using constants prevents typos and enables
 * IDE refactoring support.
 *
 * <p>Example usage in JwtTokenProvider:
 * <pre>
 * Jwts.builder()
 *     .subject(user.getId().toString())
 *     .claim(JwtClaimNames.EMAIL, user.getEmail())
 *     .claim(JwtClaimNames.ROLE, user.getRole().name())
 *     .claim(JwtClaimNames.EMAIL_VERIFIED, user.isEmailVerified())
 *     ...
 * </pre>
 */
public final class JwtClaimNames {

  /**
   * User ID claim (standard JWT "sub" claim).
   * Value: UUID string of the user.
   */
  public static final String USER_ID = "sub";

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
   * User display name.
   * Value: Display name string (e.g., "John Doe").
   */
  public static final String DISPLAY_NAME = "displayName";

  /**
   * Email verification status.
   * Value: Boolean (true if email is verified).
   *
   * <p>Used by other services to check if user can perform
   * protected actions (bidding, creating auctions).
   */
  public static final String EMAIL_VERIFIED = "emailVerified";

  private JwtClaimNames() {
    // Prevent instantiation
  }
}
