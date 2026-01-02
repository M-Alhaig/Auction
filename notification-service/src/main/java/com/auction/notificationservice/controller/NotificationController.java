package com.auction.notificationservice.controller;

import com.auction.notificationservice.dto.NotificationResponse;
import com.auction.notificationservice.dto.UnreadCountResponse;
import com.auction.notificationservice.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for notification management.
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li>GET /api/notifications - Get paginated notification history</li>
 *   <li>GET /api/notifications/unread - Get unread notifications only</li>
 *   <li>GET /api/notifications/unread/count - Get unread notification count (for badges)</li>
 *   <li>PUT /api/notifications/{id}/read - Mark single notification as read</li>
 *   <li>PUT /api/notifications/read-all - Mark all notifications as read</li>
 * </ul>
 *
 * <p><strong>Pagination:</strong> Uses @PageableDefault to set sensible defaults.
 * Clients can override via query params: ?page=0&size=20&sort=createdAt,desc
 *
 * <p><strong>Authentication:</strong> Uses X-Auth-Id header to identify the user.
 * In production, this should be replaced with JWT token validation via Spring Security.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  /**
   * Get paginated notification history for the authenticated user.
   *
   * @param authId   user UUID from X-Auth-Id header
   * @param pageable pagination parameters (default: 20 items, sorted by createdAt DESC)
   * @return page of notifications
   */
  @GetMapping
  public ResponseEntity<Page<NotificationResponse>> getNotifications(
      @RequestHeader("X-Auth-Id") String authId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
      Pageable pageable) {

    UUID userId = UUID.fromString(authId);
    log.debug("GET /api/notifications - userId: {}", userId);

    Page<NotificationResponse> notifications = notificationService.getNotifications(userId, pageable);
    return ResponseEntity.ok(notifications);
  }

  /**
   * Get paginated unread notifications for the authenticated user.
   *
   * @param authId   user UUID from X-Auth-Id header
   * @param pageable pagination parameters (default: 20 items, sorted by createdAt DESC)
   * @return page of unread notifications
   */
  @GetMapping("/unread")
  public ResponseEntity<Page<NotificationResponse>> getUnreadNotifications(
      @RequestHeader("X-Auth-Id") String authId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
      Pageable pageable) {

    UUID userId = UUID.fromString(authId);
    log.debug("GET /api/notifications/unread - userId: {}", userId);

    Page<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId, pageable);
    return ResponseEntity.ok(notifications);
  }

  /**
   * Get unread notification count for the authenticated user.
   * Used for notification badge counters in the UI.
   *
   * @param authId user UUID from X-Auth-Id header
   * @return count of unread notifications
   */
  @GetMapping("/unread/count")
  public ResponseEntity<UnreadCountResponse> getUnreadCount(
      @RequestHeader("X-Auth-Id") String authId) {

    UUID userId = UUID.fromString(authId);
    log.debug("GET /api/notifications/unread/count - userId: {}", userId);

    long count = notificationService.getUnreadCount(userId);
    return ResponseEntity.ok(new UnreadCountResponse(count));
  }

  /**
   * Mark a specific notification as read.
   *
   * @param id     notification ID
   * @param authId user UUID from X-Auth-Id header (for ownership validation)
   * @return 200 OK if updated, 404 Not Found if notification doesn't exist or not owned
   */
  @PutMapping("/{id}/read")
  public ResponseEntity<Void> markAsRead(
      @PathVariable Long id,
      @RequestHeader("X-Auth-Id") String authId) {

    UUID userId = UUID.fromString(authId);
    log.debug("PUT /api/notifications/{}/read - userId: {}", id, userId);

    boolean updated = notificationService.markAsRead(id, userId);

    if (updated) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.notFound().build();
  }

  /**
   * Mark all notifications as read for the authenticated user.
   *
   * @param authId user UUID from X-Auth-Id header
   * @return 200 OK (always succeeds, even if no notifications to mark)
   */
  @PutMapping("/read-all")
  public ResponseEntity<Void> markAllAsRead(
      @RequestHeader("X-Auth-Id") String authId) {

    UUID userId = UUID.fromString(authId);
    log.debug("PUT /api/notifications/read-all - userId: {}", userId);

    notificationService.markAllAsRead(userId);
    return ResponseEntity.ok().build();
  }
}
