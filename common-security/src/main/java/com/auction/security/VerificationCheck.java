package com.auction.security;

/**
 * Utility class for email verification checks in service layers.
 *
 * <p>Used by Item Service and Bidding Service to enforce email verification
 * before allowing protected actions. The verification status comes from
 * JWT claims (emailVerified field in UserPrincipal).
 *
 * <p>Example usage in BidService:
 * <pre>
 * public BidResponse placeBid(UserPrincipal principal, PlaceBidRequest request) {
 *     VerificationCheck.requireVerified(principal.isEmailVerified());
 *     // proceed with bid...
 * }
 * </pre>
 *
 * @see EmailNotVerifiedException
 */
public final class VerificationCheck {

  private static final String VERIFICATION_REQUIRED_MESSAGE =
      "Email verification required to perform this action. Please verify your email first.";

  /**
   * Throws EmailNotVerifiedException if the user's email is not verified.
   *
   * @param emailVerified the user's email verification status from JWT claims
   * @throws EmailNotVerifiedException if emailVerified is false
   */
  public static void requireVerified(boolean emailVerified) {
    if (!emailVerified) {
      throw new EmailNotVerifiedException(VERIFICATION_REQUIRED_MESSAGE);
    }
  }

  private VerificationCheck() {
    // Prevent instantiation
  }
}
