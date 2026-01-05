package com.auction.userservice.security;

import com.auction.security.JwtClaimNames;
import com.auction.userservice.config.JwtConfig;
import com.auction.userservice.models.UserProfile;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JWT token generation and validation using JJWT 0.12.x.
 *
 * <p>Access tokens contain:
 * <ul>
 *   <li>sub: User ID (UUID)</li>
 *   <li>email: User's email</li>
 *   <li>role: User's role (BIDDER, SELLER, ADMIN)</li>
 *   <li>displayName: User's display name</li>
 *   <li>emailVerified: Email verification status</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

  private final JwtConfig jwtConfig;
  private SecretKey key;

  @PostConstruct
  public void init() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
    this.key = Keys.hmacShaKeyFor(keyBytes);
    log.info("JWT provider initialized with {}-bit key", keyBytes.length * 8);
  }

  /**
   * Generate an access token for a user.
   */
  public String generateAccessToken(UserProfile user) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(jwtConfig.getAccessTokenExpirationMs());

    return Jwts.builder()
        .subject(user.getId().toString())
        .claim(JwtClaimNames.EMAIL, user.getEmail())
        .claim(JwtClaimNames.ROLE, user.getRole().name())
        .claim(JwtClaimNames.DISPLAY_NAME, user.getDisplayName())
        .claim(JwtClaimNames.EMAIL_VERIFIED, user.isEmailVerified())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(key)
        .compact();
  }

  /**
   * Generate a raw refresh token (UUID).
   * This will be hashed before storage.
   */
  public String generateRefreshToken() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generate a new token family UUID.
   */
  public String generateTokenFamily() {
    return UUID.randomUUID().toString();
  }

  /**
   * Get refresh token expiry as Instant.
   */
  public Instant getRefreshTokenExpiry() {
    return Instant.now().plusMillis(jwtConfig.getRefreshTokenExpirationMs());
  }

  /**
   * Get access token expiration in seconds (for response).
   */
  public long getAccessTokenExpirationSeconds() {
    return jwtConfig.getAccessTokenExpirationMs() / 1000;
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

  private Claims parseToken(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
