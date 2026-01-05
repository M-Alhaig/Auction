package com.auction.security;

/**
 * Exception thrown when a user attempts an action that requires email verification
 * but their email is not yet verified.
 *
 * <p>This exception should be caught by GlobalExceptionHandler in each service
 * and converted to HTTP 403 Forbidden response.
 *
 * <p>Actions requiring verification:
 * <ul>
 *   <li>Placing bids (Bidding Service)</li>
 *   <li>Creating auctions (Item Service)</li>
 *   <li>Updating auctions (Item Service)</li>
 * </ul>
 *
 * @see VerificationCheck#requireVerified(boolean)
 */
public class EmailNotVerifiedException extends RuntimeException {

  public EmailNotVerifiedException(String message) {
    super(message);
  }
}
