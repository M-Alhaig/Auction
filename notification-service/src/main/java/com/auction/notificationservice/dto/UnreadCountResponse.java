package com.auction.notificationservice.dto;

/**
 * Simple response for unread notification count endpoint.
 *
 * <p>Used for:
 * <ul>
 *   <li>GET /api/notifications/unread/count</li>
 *   <li>Notification badge counters in UI</li>
 * </ul>
 */
public record UnreadCountResponse(long count) {}
