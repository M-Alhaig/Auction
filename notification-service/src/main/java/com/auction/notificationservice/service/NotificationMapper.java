package com.auction.notificationservice.service;

import com.auction.notificationservice.dto.NotificationResponse;
import com.auction.notificationservice.model.Notification;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting between Notification entities and DTOs.
 */
@Component
public class NotificationMapper {

  /**
   * Convert a Notification entity to a NotificationResponse DTO.
   *
   * @param notification the entity to convert
   * @return the DTO representation, or null if input is null
   */
  public NotificationResponse toResponse(Notification notification) {
    if (notification == null) {
      return null;
    }

    return new NotificationResponse(
        notification.getId(),
        notification.getUserId(),
        notification.getItemId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.isRead(),
        notification.getCreatedAt()
    );
  }
}
