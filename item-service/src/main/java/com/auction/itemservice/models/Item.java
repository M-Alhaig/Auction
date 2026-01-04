package com.auction.itemservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "items", indexes = {@Index(name = "idx_seller_id", columnList = "seller_id"),
    @Index(name = "idx_status_endtime", columnList = "status, end_time")})
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Item {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "seller_id", nullable = false)
  private UUID sellerId;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "starting_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal startingPrice;

  @Column(name = "current_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal currentPrice;

  @Column(name = "winner_id")
  private UUID winnerId;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ItemStatus status = ItemStatus.PENDING;

  @Column(name = "start_time", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant startTime;

  @Column(name = "end_time", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant endTime;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant updatedAt;

  @ToString.Exclude
  @ManyToMany
  @JoinTable(name = "item_categories", joinColumns = @JoinColumn(name = "item_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
  private Set<Category> categories = new LinkedHashSet<>();

  /**
   * Determine whether another object represents the same persistent Item, accounting for Hibernate proxies and entity identity.
   *
   * @param o the object to compare with this Item (may be a Hibernate proxy)
   * @return `true` if both objects are the same instance or have the same persistent class and a non-null, equal id; `false` otherwise.
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
    if (o instanceof Item item) {
      return getId() != null && Objects.equals(getId(), item.getId());
    }
    return false;
  }

  /**
   * Compute a class-based hash code that accounts for Hibernate proxies.
   *
   * @return the hash code of the underlying persistent class when this instance is a Hibernate proxy,
   *         otherwise the hash code of this instance's runtime class
   */
  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer()
        .getPersistentClass().hashCode() : getClass().hashCode();
  }
}