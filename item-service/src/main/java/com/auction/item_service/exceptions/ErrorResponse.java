package com.auction.item_service.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response DTO. Returned to clients when exceptions occur.
 *
 * @JsonInclude excludes null fields from JSON output for cleaner responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> fieldErrors  // For validation errors
) {

  /**
   * Constructor for simple errors without field-level details.
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(LocalDateTime.now(), status, error, message, path, null);
  }

  /**
   * Constructor for validation errors with field-level details.
   */
  public ErrorResponse(int status, String error, String message, String path,
      Map<String, String> fieldErrors) {
    this(LocalDateTime.now(), status, error, message, path, fieldErrors);
  }
}
