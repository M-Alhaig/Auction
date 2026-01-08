package com.auction.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT token validator for downstream services.
 *
 * <p>Read-only validation - does NOT generate tokens.
 * Token generation is exclusive to user-service's JwtTokenProvider.
 *
 * <p>Services configure this with the shared JWT secret via application.properties:
 * <pre>
 * jwt.secret=your-base64-encoded-secret
 * </pre>
 */
@Slf4j
public class JwtTokenValidator {

  private final SecretKey key;

  public JwtTokenValidator(String base64Secret) {
    byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
    this.key = Keys.hmacShaKeyFor(keyBytes);
    log.info("JWT validator initialized with {}-bit key", keyBytes.length * 8);
  }

  /**
   * Validate a token.
   *
   * @return true if valid and not expired
   */
  public boolean validateToken(String token) {
    try {
      parseToken(token);
      return true;
    } catch (ExpiredJwtException ex) {
      log.debug("Token expired: {}", ex.getMessage());
      return false;
    } catch (JwtException ex) {
      log.warn("Invalid token: {}", ex.getMessage());
      return false;
    }
  }

  /**
   * Extract user ID from token.
   */
  public UUID getUserIdFromToken(String token) {
    return UUID.fromString(parseToken(token).getSubject());
  }

  /**
   * Extract email from token.
   */
  public String getEmailFromToken(String token) {
    return parseToken(token).get(JwtClaimNames.EMAIL, String.class);
  }

  /**
   * Extract role from token.
   */
  public String getRoleFromToken(String token) {
    return parseToken(token).get(JwtClaimNames.ROLE, String.class);
  }

  /**
   * Extract email verified status from token.
   */
  public boolean isEmailVerifiedFromToken(String token) {
    return Boolean.TRUE.equals(parseToken(token).get(JwtClaimNames.EMAIL_VERIFIED, Boolean.class));
  }

  /**
   * Extract account enabled status from token.
   */
  public boolean isEnabledFromToken(String token) {
    return Boolean.TRUE.equals(parseToken(token).get(JwtClaimNames.ENABLED, Boolean.class));
  }

  private Claims parseToken(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
