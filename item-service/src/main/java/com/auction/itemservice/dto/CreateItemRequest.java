package com.auction.itemservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * DTO for creating a new auction item. Used by sellers to submit new items for auction.
 * <p>
 * NOTE: sellerId is NOT included in this DTO - it is extracted from the authenticated user's JWT
 * token to prevent impersonation attacks. Clients cannot specify sellerId.
 */
public record CreateItemRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title,

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    String description,

    @NotNull(message = "Starting price is required")
    @DecimalMin(value = "0.01", message = "Starting price must be at least $0.01")
    @Digits(integer = 10, fraction = 2, message = "Starting price must have at most 10 integer digits and 2 decimal places")
    BigDecimal startingPrice,

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    String imageUrl,

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    Instant startTime,

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    Instant endTime,

    @Size(max = 10, message = "Maximum 10 categories allowed")
    Set<Integer> categoryIds
) {

  /**
   * Compact constructor for additional validation. Ensures business rules that can't be expressed
   * with annotations.
   */
  public CreateItemRequest {
    // Validate that end time is after start time
    if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("End time must be after start time");
    }
  }
}