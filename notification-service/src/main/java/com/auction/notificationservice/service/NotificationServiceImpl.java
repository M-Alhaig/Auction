package com.auction.notificationservice.service;

import com.auction.notificationservice.dto.NotificationResponse;
import com.auction.notificationservice.model.Notification;
import com.auction.notificationservice.model.NotificationType;
import com.auction.notificationservice.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of NotificationService for CRUD operations on notifications.
 *
 * <p><strong>Transaction Strategy:</strong>
 * <ul>
 *   <li>Class-level @Transactional: All methods run in a transaction</li>
 *   <li>readOnly=true on queries: Optimizes SELECT operations (no dirty checking, can use read replicas)</li>
 *   <li>Write methods use default (readOnly=false): Allows INSERT/UPDATE/DELETE</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional  // All methods run in a transaction; writes will rollback on exception
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  @Override
  public Notification createNotification(UUID userId, Long itemId, NotificationType type,
      String title, String message) {
    Notification notification = new Notification();
    notification.setUserId(userId);
    notification.setItemId(itemId);
    notification.setType(type);
    notification.setTitle(title);
    notification.setMessage(message);
    notification.setRead(false);

    notification = notificationRepository.save(notification);
    log.info("Created notification - id: {}, userId: {}, itemId: {}, type: {}",
        notification.getId(), userId, itemId, type);

    return notification;
  }

  /**
   * Get paginated notification history.
   * readOnly=true: Optimizes for SELECT (skips dirty checking, can use read replica).
   */
  @Override
  @Transactional(readOnly = true)
  public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
    log.debug("getNotifications - userId: {}, page: {}, size: {}",
        userId, pageable.getPageNumber(), pageable.getPageSize());

    return notificationRepository
        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(notificationMapper::toResponse);
  }

  /**
   * Get paginated unread notifications.
   * readOnly=true: Optimizes for SELECT.
   */
  @Override
  @Transactional(readOnly = true)
  public Page<NotificationResponse> getUnreadNotifications(UUID userId, Pageable pageable) {
    log.debug("getUnreadNotifications - userId: {}, page: {}, size: {}",
        userId, pageable.getPageNumber(), pageable.getPageSize());

    return notificationRepository
        .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
        .map(notificationMapper::toResponse);
  }

  /**
   * Get unread notification count.
   * readOnly=true: Optimizes for SELECT.
   */
  @Override
  @Transactional(readOnly = true)
  public long getUnreadCount(UUID userId) {
    log.debug("getUnreadCount - userId: {}", userId);
    return notificationRepository.countByUserIdAndReadFalse(userId);
  }

  /**
   * Mark a specific notification as read.
   * Uses @Modifying query in repository - requires writable transaction.
   */
  @Override
  public boolean markAsRead(Long notificationId, UUID userId) {
    log.debug("markAsRead - notificationId: {}, userId: {}", notificationId, userId);

    int updated = notificationRepository.markAsRead(notificationId, userId);

    if (updated > 0) {
      log.info("Marked notification as read - id: {}, userId: {}", notificationId, userId);
      return true;
    }

    log.warn("Notification not found or not owned by user - id: {}, userId: {}",
        notificationId, userId);
    return false;
  }

  /**
   * Mark all notifications as read for a user.
   * Uses @Modifying query in repository - requires writable transaction.
   */
  @Override
  public int markAllAsRead(UUID userId) {
    log.debug("markAllAsRead - userId: {}", userId);

    int updated = notificationRepository.markAllAsReadForUser(userId);
    log.info("Marked {} notifications as read for userId: {}", updated, userId);

    return updated;
  }
}
