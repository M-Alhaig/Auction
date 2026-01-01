package com.auction.notificationservice.repository;

import com.auction.notificationservice.model.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Notification entity operations.
 *
 * <p>Provides queries for:
 * <ul>
 *   <li>Paginated notification history per user</li>
 *   <li>Unread notification count</li>
 *   <li>Bulk mark-as-read operations</li>
 *   <li>Item-based cleanup (when auction ends)</li>
 * </ul>
 *
 * <p><strong>Query Strategy:</strong>
 * <ul>
 *   <li>Derived method names: Used for SELECT, DELETE, COUNT operations</li>
 *   <li>@Query: Used for UPDATE operations (derived queries don't support UPDATE)</li>
 * </ul>
 *
 * <p><strong>Index Usage:</strong>
 * <ul>
 *   <li>idx_notifications_user_created: Used by findByUserId* methods</li>
 *   <li>idx_notifications_item: Used by deleteByItemId for cleanup</li>
 * </ul>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /**
   * Find all notifications for a user, ordered by creation time descending.
   *
   * @param userId   the user's UUID
   * @param pageable pagination parameters
   * @return paginated notifications, newest first
   */
  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * Find unread notifications for a user, ordered by creation time descending.
   *
   * @param userId   the user's UUID
   * @param pageable pagination parameters
   * @return paginated unread notifications, newest first
   */
  Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * Count unread notifications for a user.
   * Uses partial index idx_notifications_user_unread for efficiency.
   *
   * @param userId the user's UUID
   * @return count of unread notifications
   */
  long countByUserIdAndReadFalse(UUID userId);

  /**
   * Mark all unread notifications for a user as read.
   * Uses @Query because derived method names don't support UPDATE operations.
   *
   * @param userId the user's UUID
   * @return the number of notifications updated
   */
  @Modifying
  @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
  int markAllAsReadForUser(@Param("userId") UUID userId);

  /**
   * Mark a specific notification as read.
   * Uses @Query because derived method names don't support UPDATE operations.
   * Includes userId check to prevent unauthorized access.
   *
   * @param id     the notification ID
   * @param userId the user's UUID (for ownership validation)
   * @return the number of notifications updated (0 or 1)
   */
  @Modifying
  @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.userId = :userId")
  int markAsRead(@Param("id") Long id, @Param("userId") UUID userId);

  /**
   * Delete all notifications for a specific item.
   * Used for cleanup when auction ends.
   *
   * @param itemId the auction item ID
   */
  void deleteByItemId(Long itemId);
}
