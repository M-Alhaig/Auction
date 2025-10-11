package com.auction.itemservice.repositories;

import com.auction.itemservice.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

  /**
   * Find a category by its unique name. Used when looking up categories by name instead of ID.
   *
   * @param name the category name (case-sensitive)
   * @return Optional containing the category if found, empty otherwise
   */
  Optional<Category> findByName(String name);

  /**
   * Find all categories matching the given set of IDs. Used when creating/updating items with
   * multiple categories. Returns a Set to match the Item entity's category relationship type.
   *
   * @param ids set of category IDs to retrieve
   * @return set of matching categories (may be empty if none found)
   */
  Set<Category> findByIdIn(Set<Integer> ids);

  /**
   * Check if a category with the given name already exists. Used for validation before creating new
   * categories to prevent duplicates. More efficient than findByName() when you only need existence
   * check.
   *
   * @param name the category name to check
   * @return true if category exists, false otherwise
   */
  boolean existsByName(String name);

}
