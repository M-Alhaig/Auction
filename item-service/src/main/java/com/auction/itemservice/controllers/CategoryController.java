package com.auction.itemservice.controllers;

import com.auction.itemservice.dto.CategoryResponse;
import com.auction.itemservice.repositories.CategoryRepository;
import com.auction.itemservice.services.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
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
}
