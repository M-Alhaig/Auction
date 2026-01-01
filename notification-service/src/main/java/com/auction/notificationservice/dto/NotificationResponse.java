package com.auction.notificationservice.dto;

import com.auction.notificationservice.model.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for notification data returned via REST API.
 *
 * <p>Used for endpoints:
 * <ul>
 *   <li>GET /api/notifications - Paginated notification history</li>
 *   <li>GET /api/notifications/unread - Unread notifications only</li>
 * </ul>
 */
public record NotificationResponse(
    Long id,
    UUID userId,
    Long itemId,
    NotificationType type,
    String title,
    String message,
    boolean read,
    Instant createdAt
) {}
