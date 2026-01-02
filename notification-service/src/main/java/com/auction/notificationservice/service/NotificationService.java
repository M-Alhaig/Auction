package com.auction.notificationservice.service;

import com.auction.notificationservice.dto.NotificationResponse;
import com.auction.notificationservice.model.Notification;
import com.auction.notificationservice.model.NotificationType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for notification management.
 *
 * <p>Provides CRUD operations and queries for user notifications.
 */
public interface NotificationService {

  /**
   * Create and persist a new notification.
   *
   * @param userId  the user who will receive the notification
   * @param itemId  the auction item this notification relates to
   * @param type    the notification type (OUTBID, AUCTION_WON, etc.)
   * @param title   short title for the notification
   * @param message detailed message content
   * @return the created notification entity
   */
  Notification createNotification(UUID userId, Long itemId, NotificationType type,
      String title, String message);

  /**
   * Get paginated notification history for a user.
   *
   * @param userId   the user's UUID
   * @param pageable pagination parameters
   * @return page of notifications, newest first
   */
  Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable);

  /**
   * Get paginated unread notifications for a user.
   *
   * @param userId   the user's UUID
   * @param pageable pagination parameters
   * @return page of unread notifications, newest first
   */
  Page<NotificationResponse> getUnreadNotifications(UUID userId, Pageable pageable);

  /**
   * Get count of unread notifications for a user.
   *
   * @param userId the user's UUID
   * @return count of unread notifications
   */
  long getUnreadCount(UUID userId);

  /**
   * Mark a specific notification as read.
   *
   * @param notificationId the notification ID
   * @param userId         the user's UUID (for ownership validation)
   * @return true if notification was found and updated, false otherwise
   */
  boolean markAsRead(Long notificationId, UUID userId);

  /**
   * Mark all notifications as read for a user.
   *
   * @param userId the user's UUID
   * @return number of notifications marked as read
   */
  int markAllAsRead(UUID userId);
}
