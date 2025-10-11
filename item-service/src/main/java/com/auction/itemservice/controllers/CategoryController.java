package com.auction.itemservice.controllers;

import com.auction.itemservice.dto.CategoryResponse;
import com.auction.itemservice.repositories.CategoryRepository;
import com.auction.itemservice.services.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing auction item categories. Categories are used to classify items and
 * enable filtering.
 * <p>
 * Note: This is a simple read-only controller for now. In production, you might want admin-only
 * endpoints for creating/updating categories.
 */
@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryRepository categoryRepository;
  private final ItemMapper itemMapper;

  /**
   * Get all categories. Used by frontend to populate category selection dropdowns.
   *
   * @return list of all categories
   */
  @GetMapping
  public ResponseEntity<List<CategoryResponse>> getAllCategories() {
    log.debug("GET /api/categories - Fetching all categories");

    List<CategoryResponse> categories = categoryRepository.findAll()
        .stream()
        .map(itemMapper::toCategoryResponse)
        .toList();

    log.debug("GET /api/categories - Found {} categories", categories.size());
    return ResponseEntity.ok(categories);
  }

  /**
   * Get a single category by ID.
   *
   * @param id the category ID
   * @return the category
   */
  @GetMapping("/{id}")
  public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Integer id) {
    log.debug("GET /api/categories/{}", id);

    return categoryRepository.findById(id)
        .map(itemMapper::toCategoryResponse)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
