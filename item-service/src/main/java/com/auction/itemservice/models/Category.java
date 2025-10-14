package com.auction.itemservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Entity
@Table(name = "categories")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(unique = true, nullable = false, length = 100)
  @NotBlank
  private String name;

  /**
   * Determine equality by entity identity, resolving Hibernate proxies to their persistent class.
   *
   * @param o the object to compare with this Category
   * @return `true` if both objects are of the same persistent class (proxy-resolved) and this entity's `id`
   *         is non-null and equal to the other object's `id`, `false` otherwise
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
    if (o instanceof Category category) {
      return getId() != null && Objects.equals(getId(), category.getId());
    }
    return false;
  }

  /**
   * Compute the hash code aligned with the entity's effective persistent class, handling Hibernate proxies.
   *
   * @return the hash code of the persistent class when this instance is a HibernateProxy, otherwise the hash code of this instance's runtime class
   */
  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer()
        .getPersistentClass().hashCode() : getClass().hashCode();
  }
}
