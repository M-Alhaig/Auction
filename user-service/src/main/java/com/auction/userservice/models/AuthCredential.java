package com.auction.userservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authentication credentials for a user.
 *
 * <p>Supports multiple auth methods per user:
 * <ul>
 *   <li>LOCAL: Email/password (passwordHash stored)</li>
 *   <li>GOOGLE: OAuth2 via Google (providerId = Google's user ID)</li>
 *   <li>GITHUB: OAuth2 via GitHub (providerId = GitHub's user ID)</li>
 * </ul>
 *
 * <p>One user can have multiple credentials, enabling "Link with Google" features.
 *
 * <p>Mapped to "auth_credentials" table with unique constraint on (provider, provider_id).
 */
@Entity
@Table(
    name = "auth_credentials",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_provider_id",
        columnNames = {"provider", "provider_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthCredential {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * The user this credential belongs to.
   * Lazy loaded to avoid N+1 queries.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserProfile user;

  /**
   * The authentication provider.
   * Determines how this credential is validated.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AuthProvider provider;

  /**
   * BCrypt-hashed password for LOCAL provider.
   * Null for OAuth providers (GOOGLE, GITHUB).
   */
  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  /**
   * External provider's unique user identifier.
   * Used for GOOGLE and GITHUB providers.
   * Null for LOCAL provider.
   */
  @Column(name = "provider_id", length = 255)
  private String providerId;

  /**
   * Timestamp when this credential was created.
   * Set automatically on insert.
   */
  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  /**
   * Factory method for creating a LOCAL credential.
   *
   * @param user the user this credential belongs to
   * @param passwordHash BCrypt-hashed password
   * @return new AuthCredential for local authentication
   */
  public static AuthCredential createLocal(UserProfile user, String passwordHash) {
    return AuthCredential.builder()
        .user(user)
        .provider(AuthProvider.LOCAL)
        .passwordHash(passwordHash)
        .build();
  }

  /**
   * Factory method for creating an OAuth credential.
   *
   * @param user the user this credential belongs to
   * @param provider the OAuth provider (GOOGLE or GITHUB)
   * @param providerId the provider's unique user identifier
   * @return new AuthCredential for OAuth authentication
   */
  public static AuthCredential createOAuth(UserProfile user, AuthProvider provider, String providerId) {
    return AuthCredential.builder()
        .user(user)
        .provider(provider)
        .providerId(providerId)
        .build();
  }
}
