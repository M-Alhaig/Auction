package com.auction.itemservice.exceptions;

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
   * Create an ErrorResponse with the current timestamp and no field-level errors.
   *
   * @param status HTTP status code
   * @param error short error description
   * @param message detailed error message
   * @param path request path where the error occurred
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(LocalDateTime.now(), status, error, message, path, null);
  }

  /**
   * Create an ErrorResponse with the current timestamp and field-level validation details.
   *
   * @param status      the HTTP status code
   * @param error       a short error description
   * @param message     a detailed error message
   * @param path        the request path where the error occurred
   * @param fieldErrors a map of field names to validation error messages; may be null
   */
  public ErrorResponse(int status, String error, String message, String path,
      Map<String, String> fieldErrors) {
    this(LocalDateTime.now(), status, error, message, path, fieldErrors);
  }
}