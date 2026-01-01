package com.auction.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.proxy.HibernateProxy;

/**
 * Entity representing a user notification in the auction system.
 *
 * <p>Notifications are created from consumed events (e.g., UserOutbidEvent)
 * and persist the alert for users who may not be connected via WebSocket.
 *
 * <p><strong>Persistence Strategy:</strong>
 * <ul>
 *   <li>UserOutbidEvent: Persisted (personal notification for offline users)</li>
 *   <li>BidPlacedEvent: NOT persisted (WebSocket broadcast only, high volume)</li>
 * </ul>
 *
 * <p><strong>Concurrency:</strong> Uses @Version for optimistic locking on mark-as-read operations.
 *
 * <p><strong>Indexes:</strong>
 * <ul>
 *   <li>idx_notifications_user_created: For paginated user notification history</li>
 *   <li>idx_notifications_item: For cleanup when auction ends</li>
 * </ul>
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at DESC"),
    @Index(name = "idx_notifications_item", columnList = "item_id")
})
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * The user who receives this notification.
   * Not a foreign key (cross-service boundary with User Service).
   */
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /**
   * The auction item this notification relates to.
   * Not a foreign key (cross-service boundary with Item Service).
   */
  @Column(name = "item_id", nullable = false)
  private Long itemId;

  /**
   * The type of notification (OUTBID, AUCTION_WON, etc.).
   * Stored as VARCHAR for database readability.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 50)
  private NotificationType type;

  /**
   * Short title for the notification (e.g., "You've been outbid!").
   */
  @Column(name = "title", nullable = false)
  private String title;

  /**
   * Detailed message content.
   */
  @Column(name = "message", nullable = false, columnDefinition = "TEXT")
  private String message;

  /**
   * Whether the user has read this notification.
   * Default: false (unread).
   */
  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  /**
   * When this notification was created.
   * Auto-populated by Hibernate, stored in UTC.
   */
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant createdAt;

  /**
   * Optimistic locking version for concurrent mark-as-read operations.
   */
  @Version
  @Column(name = "version")
  private Long version;

  /**
   * Determine whether this Notification is equal to another object, using persistent identity
   * and correctly handling Hibernate proxy instances.
   *
   * @param o the object to compare with this Notification
   * @return true if o is a Notification of the same persistent class with the same non-null id
   */
  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    Class<?> oEffectiveClass =
        o instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer()
            .getPersistentClass() : o.getClass();
    Class<?> thisEffectiveClass =
        this instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer()
            .getPersistentClass() : this.getClass();
    if (thisEffectiveClass != oEffectiveClass) {
      return false;
    }
    if (o instanceof Notification notification) {
      return getId() != null && Objects.equals(getId(), notification.getId());
    }
    return false;
  }

  /**
   * Computes a hash code that is stable across Hibernate proxies and concrete instances.
   *
   * @return the hash code of the underlying persistent class
   */
  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer()
        .getPersistentClass().hashCode() : getClass().hashCode();
  }
}
