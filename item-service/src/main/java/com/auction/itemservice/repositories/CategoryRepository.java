package com.auction.itemservice.repositories;

import com.auction.itemservice.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

  /**
 * Finds a category by its exact name.
 *
 * @param name the category name (case-sensitive)
 * @return an Optional containing the Category if found, empty otherwise
 */
  Optional<Category> findByName(String name);

  /**
 * Retrieves all Category entities whose IDs are contained in the given set.
 *
 * @param ids set of category IDs to retrieve
 * @return set of matching categories; empty if none found
 */
  Set<Category> findByIdIn(Set<Integer> ids);

  /**
 * Determines whether a category with the specified name exists.
 *
 * @param name the category name to check
 * @return `true` if a category with the given name exists, `false` otherwise
 */
  boolean existsByName(String name);

}