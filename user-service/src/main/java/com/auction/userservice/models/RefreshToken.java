package com.auction.userservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token entity for JWT token rotation.
 *
 * <p>Tokens are stored as BCrypt hashes for security - even if the database is
 * compromised, the actual tokens cannot be used.
 *
 * <p><strong>Token Family Rotation:</strong> Each refresh token belongs to a "family"
 * (UUID). When a token is refreshed, the new token inherits the same family.
 * If a revoked token is reused (indicating theft), the entire family is revoked.
 *
 * <p>Flow:
 * <ol>
 *   <li>User logs in → new token with new family UUID</li>
 *   <li>Token refreshed → old token revoked, new token with same family</li>
 *   <li>Revoked token reused → THEFT DETECTED → revoke entire family</li>
 * </ol>
 *
 * <p>Mapped to "refresh_tokens" table.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * The user this token belongs to.
   * Lazy loaded to avoid N+1 queries.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserProfile user;

  /**
   * BCrypt-hashed refresh token.
   * The raw token is only known to the client.
   *
   * <p>To validate: passwordEncoder.matches(rawToken, tokenHash)
   */
  @Column(name = "token_hash", nullable = false, length = 255)
  private String tokenHash;

  /**
   * Token family UUID for rotation detection.
   * All tokens in a rotation chain share the same family.
   *
   * <p>If a revoked token is reused, the entire family is revoked
   * (indicates token theft - both legitimate user and attacker have tokens).
   */
  @Column(name = "token_family", nullable = false, length = 36)
  private String tokenFamily;

  /**
   * When this token expires.
   * Typically 7 days after creation.
   */
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /**
   * When this token was created.
   * Set automatically on insert.
   */
  @Column(name = "created_at")
  private Instant createdAt;

  /**
   * Whether this token has been revoked.
   * Set to true when token is refreshed or explicitly revoked.
   */
  @Builder.Default
  private boolean revoked = false;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  /**
   * Check if this token is expired.
   *
   * @return true if current time is past expiresAt
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /**
   * Check if this token is valid (not revoked and not expired).
   *
   * @return true if token can be used
   */
  public boolean isValid() {
    return !revoked && !isExpired();
  }

  /**
   * Revoke this token.
   * Called when token is refreshed or user logs out.
   */
  public void revoke() {
    this.revoked = true;
  }
}
