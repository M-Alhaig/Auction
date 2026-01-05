package com.auction.userservice.repositories;

import com.auction.userservice.models.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for RefreshToken entity operations.
 *
 * <p>Supports token family rotation pattern for security:
 * <ul>
 *   <li>Each token belongs to a family (UUID)</li>
 *   <li>Refreshing creates new token in same family</li>
 *   <li>Reusing revoked token = theft → revoke entire family</li>
 * </ul>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /**
   * Find all tokens in a token family.
   * Used to revoke entire family on theft detection.
   *
   * @param tokenFamily the family UUID
   * @return all tokens in the family
   */
  List<RefreshToken> findByTokenFamily(String tokenFamily);

  /**
   * Find active (non-revoked, non-expired) tokens for a user.
   * Used for session management (show active sessions).
   *
   * @param userId the user's ID
   * @return list of active tokens
   */
  @Query("SELECT t FROM RefreshToken t WHERE t.user.id = :userId AND t.revoked = false AND t.expiresAt > CURRENT_TIMESTAMP")
  List<RefreshToken> findActiveTokensByUserId(UUID userId);

  /**
   * Find a valid token by user and family.
   * Used during token refresh to validate the token belongs to this user.
   *
   * @param userId the user's ID
   * @param tokenFamily the token family
   * @return the valid token if exists
   */
  @Query("SELECT t FROM RefreshToken t WHERE t.user.id = :userId AND t.tokenFamily = :tokenFamily AND t.revoked = false AND t.expiresAt > CURRENT_TIMESTAMP")
  Optional<RefreshToken> findValidTokenByUserIdAndFamily(UUID userId, String tokenFamily);

  /**
   * Revoke all tokens in a token family.
   * Called when theft is detected (revoked token reused).
   *
   * @param tokenFamily the family UUID to revoke
   * @return number of tokens revoked
   */
  @Modifying
  @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.tokenFamily = :tokenFamily")
  int revokeByTokenFamily(String tokenFamily);

  /**
   * Revoke all tokens for a user.
   * Called on logout-all or password change.
   *
   * @param userId the user's ID
   * @return number of tokens revoked
   */
  @Modifying
  @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId")
  int revokeAllByUserId(UUID userId);

  /**
   * Delete expired tokens (cleanup job).
   * Should be run periodically to prevent table bloat.
   *
   * @param before delete tokens expired before this time
   * @return number of tokens deleted
   */
  @Modifying
  @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :before")
  int deleteExpiredBefore(Instant before);
}
