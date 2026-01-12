package com.auction.itemservice.controllers;

import com.auction.itemservice.dto.CategoryResponse;
import com.auction.itemservice.dto.CreateCategoryRequest;
import com.auction.itemservice.models.Category;
import com.auction.itemservice.repositories.CategoryRepository;
import com.auction.itemservice.services.ItemMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing auction item categories. Categories are used to classify items and
 * enable filtering.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>GET /api/categories - List all categories (sorted alphabetically)</li>
 *   <li>GET /api/categories/{id} - Get category by ID</li>
 *   <li>POST /api/categories - Create new category (idempotent via unique constraint)</li>
 * </ul>
 */
// TODO(security): Add @PreAuthorize when API Gateway authentication is integrated:
//   - createCategory: @PreAuthorize("hasRole('ADMIN')") - only admins create categories
//   - Public endpoints (getAllCategories, getCategoryById) remain unauthenticated
@Validated
@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryRepository categoryRepository;
  private final ItemMapper itemMapper;

  /**
   * Retrieve all auction item categories.
   *
   * @return the list of all CategoryResponse objects
   */
  @GetMapping
  public ResponseEntity<List<CategoryResponse>> getAllCategories() {
    log.debug("GET /api/categories - Fetching all categories");

	  List<CategoryResponse> categories = categoryRepository.findAll(
			  Sort.by(Sort.Direction.ASC, "name"))
        .stream()
        .map(itemMapper::toCategoryResponse)
        .toList();

    log.debug("GET /api/categories - Found {} categories", categories.size());
    return ResponseEntity.ok(categories);
  }

  /**
   * Retrieve the category identified by the given ID.
   *
   * @param id the identifier of the category to retrieve
   * @return a ResponseEntity containing the CategoryResponse with HTTP 200 if found, or HTTP 404 if not found
   */
  @GetMapping("/{id}")
  public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Integer id) {
    log.debug("GET /api/categories/{}", id);

    return categoryRepository.findById(id)
        .map(itemMapper::toCategoryResponse)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new auction item category.
   *
   * <p><strong>Idempotency:</strong> If a category with the same name already exists,
   * returns the existing category with HTTP 200 instead of creating a duplicate.
   * This is handled via database UNIQUE constraint on the name column.
   *
   * <p><strong>Validation:</strong>
   * <ul>
   *   <li>name: Required, 1-100 characters</li>
   *   <li>Duplicate names prevented by UNIQUE constraint</li>
   * </ul>
   *
   * @param request the CreateCategoryRequest containing the category name
   * @return ResponseEntity with:
   *         <ul>
   *           <li>HTTP 201 + CategoryResponse if created successfully</li>
   *           <li>HTTP 200 + CategoryResponse if category already exists (idempotent)</li>
   *         </ul>
   */
  @PostMapping
  public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
    log.debug("POST /api/categories - Creating category: {}", request.name());

    try {
      Category category = new Category();
      category.setName(request.name().trim());

      Category savedCategory = categoryRepository.save(category);
      log.info("Category created successfully - id: {}, name: '{}'", savedCategory.getId(), savedCategory.getName());

      CategoryResponse response = itemMapper.toCategoryResponse(savedCategory);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);

    } catch (DataIntegrityViolationException e) {
      // Category with this name already exists (UNIQUE constraint violation)
      log.debug("Category already exists with name: '{}', returning existing category", request.name());

      // Find and return existing category (idempotent behavior)
      Category existingCategory = categoryRepository.findByName(request.name().trim())
          .orElseThrow(() -> new IllegalStateException("Expected existing category not found"));

      CategoryResponse response = itemMapper.toCategoryResponse(existingCategory);
      return ResponseEntity.status(HttpStatus.OK).body(response);
    }
  }
}
