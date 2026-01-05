package com.auction.userservice.repositories;

import com.auction.userservice.models.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for UserProfile entity operations.
 *
 * <p>Provides methods for user lookup by email and ID,
 * existence checks, and profile updates.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

  /**
   * Find a user by their email address.
   * Used for login and duplicate email checks.
   *
   * @param email the email to search for
   * @return the user if found
   */
  Optional<UserProfile> findByEmail(String email);

  /**
   * Check if an email is already registered.
   * Used during registration to prevent duplicates.
   *
   * @param email the email to check
   * @return true if email exists
   */
  boolean existsByEmail(String email);

  /**
   * Find enabled users by email.
   * Used for authentication (disabled users cannot log in).
   *
   * @param email the email to search for
   * @return the enabled user if found
   */
  @Query("SELECT u FROM UserProfile u WHERE u.email = :email AND u.enabled = true")
  Optional<UserProfile> findByEmailAndEnabled(String email);

  /**
   * Update the last login timestamp for a user.
   * Called after successful authentication.
   *
   * @param userId the user's ID
   */
  @Modifying
  @Query("UPDATE UserProfile u SET u.lastLoginAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
  void updateLastLoginAt(UUID userId);
}
