package com.auction.userservice.models;

import com.auction.security.Role;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Core user entity containing profile information.
 *
 * <p>Separated from authentication credentials to support multiple auth methods
 * per user (e.g., local password + Google + GitHub all linked to same profile).
 *
 * <p>Mapped to "user_profiles" table in database.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * User's email address - unique identifier across the platform.
   * Used for login and notifications.
   */
  @Column(unique = true, nullable = false, length = 255)
  private String email;

  /**
   * User's display name shown in the UI.
   * Can be updated by the user.
   */
  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  /**
   * URL to user's avatar image.
   * Can be set from OAuth provider or uploaded manually.
   */
  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  /**
   * User's role determining permissions.
   * Default: BIDDER. Can be upgraded to SELLER or ADMIN.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private Role role = Role.BIDDER;

  /**
   * Whether the user account is enabled.
   * Disabled accounts cannot log in or perform actions.
   */
  @Builder.Default
  private boolean enabled = true;

  /**
   * Whether the user has verified their email address.
   * Required for bidding and selling actions.
   */
  @Column(name = "email_verified")
  @Builder.Default
  private boolean emailVerified = false;

  /**
   * Timestamp when the account was created.
   * Set automatically on insert.
   */
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  /**
   * Timestamp of the last profile update.
   * Updated automatically on every save.
   */
  @Column(name = "updated_at")
  private Instant updatedAt;

  /**
   * Timestamp of the last successful login.
   * Updated on each authentication.
   */
  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  /**
   * Optimistic locking version.
   * Prevents concurrent modification conflicts.
   */
  @Version
  private Long version;

  /**
   * Authentication credentials linked to this user.
   * One user can have multiple auth methods (LOCAL, GOOGLE, GITHUB).
   */
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<AuthCredential> credentials = new HashSet<>();

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  /**
   * Add a credential to this user.
   * Maintains bidirectional relationship.
   */
  public void addCredential(AuthCredential credential) {
    credentials.add(credential);
    credential.setUser(this);
  }

  /**
   * Remove a credential from this user.
   * Maintains bidirectional relationship.
   */
  public void removeCredential(AuthCredential credential) {
    credentials.remove(credential);
    credential.setUser(null);
  }
}
