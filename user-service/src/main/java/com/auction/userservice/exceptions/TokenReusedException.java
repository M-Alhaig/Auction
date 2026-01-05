package com.auction.userservice.exceptions;

/**
 * Thrown when a revoked refresh token is reused.
 *
 * <p>This indicates potential token theft:
 * <ul>
 *   <li>Legitimate user refreshed token (old token revoked)</li>
 *   <li>Attacker tries to use the stolen old token</li>
 *   <li>RESULT: Entire token family is revoked for safety</li>
 * </ul>
 *
 * <p>User must re-authenticate to get a new token family.
 */
public class TokenReusedException extends RuntimeException {

  public TokenReusedException(String tokenFamily) {
    super("Token reuse detected for family " + tokenFamily + " - possible theft. All sessions revoked.");
  }
}
