package com.auction.notificationservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response structure for REST API errors.
 *
 * <p>{@code @JsonInclude(NON_NULL)} excludes null fields from JSON output,
 * keeping responses clean (e.g., fieldErrors only appears when there are validation errors).
 *
 * <p>Follows the same pattern as bidding-service and item-service for consistency.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    String path,
    LocalDateTime timestamp,
    Map<String, String> fieldErrors
) {

  /**
   * Create an error response without field errors.
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(status, error, message, path, LocalDateTime.now(), null);
  }

  /**
   * Create an error response with field-level validation errors.
   */
  public ErrorResponse(int status, String error, String message, String path,
      Map<String, String> fieldErrors) {
    this(status, error, message, path, LocalDateTime.now(), fieldErrors);
  }
}
