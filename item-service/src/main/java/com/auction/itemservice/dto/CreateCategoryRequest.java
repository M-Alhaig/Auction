package com.auction.itemservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new auction item category.
 *
 * <p>Categories are used to classify items and enable filtering in search/browse functionality.
 *
 * <p>Validation Rules:
 * <ul>
 *   <li>name: Required, 1-100 characters, trimmed</li>
 *   <li>Duplicate names are prevented by database UNIQUE constraint</li>
 * </ul>
 */
public record CreateCategoryRequest(
    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    String name
) {
}
