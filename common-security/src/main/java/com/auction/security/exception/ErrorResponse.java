package com.auction.security.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response structure for all API errors.
 *
 * <p>Used across all services for consistent error handling.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    String path,
    Instant timestamp,
    Map<String, String> fieldErrors
) {

  /**
   * Create an ErrorResponse without field-level validation errors.
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(status, error, message, path, Instant.now(), null);
  }

  /**
   * Create an ErrorResponse with field-level validation errors.
   */
  public ErrorResponse(int status, String error, String message, String path,
                       Map<String, String> fieldErrors) {
    this(status, error, message, path, Instant.now(), fieldErrors);
  }
}
