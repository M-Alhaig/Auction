package com.auction.item_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for updating an existing auction item. Only PENDING items can be updated (not ACTIVE or
 * ENDED). All fields are optional to support partial updates. Item ID comes from URL path, seller
 * ID from JWT token.
 */
public record UpdateItemRequest(
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title,

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    String description,

    @DecimalMin(value = "0.01", message = "Starting price must be at least $0.01")
    @Digits(integer = 10, fraction = 2, message = "Starting price must have at most 10 integer digits and 2 decimal places")
    BigDecimal startingPrice,

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    String imageUrl,

    LocalDateTime startTime,

    LocalDateTime endTime,

    @Size(max = 10, message = "Maximum 10 categories allowed")
    Set<Integer> categoryIds
) {

  /**
   * Compact constructor for additional validation.
   */
  public UpdateItemRequest {
    // Validate that end time is after start time (if both are provided)
    if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("End time must be after start time");
    }
  }
}
