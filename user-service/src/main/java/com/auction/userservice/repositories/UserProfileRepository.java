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
   * Find user by email if their account is enabled.
   * Used for authentication - disabled accounts (banned/suspended by admin) cannot log in.
   *
   * <p>Note: Unverified users CAN log in, they just can't bid or sell.
   * Email verification is checked at action time, not login time.
   *
   * @param email the email to search for
   * @return the user if found and account is enabled
   */
  Optional<UserProfile> findByEmailAndEnabledTrue(String email);

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
