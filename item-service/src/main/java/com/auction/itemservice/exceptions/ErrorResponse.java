package com.auction.itemservice.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response DTO. Returned to clients when exceptions occur.
 *
 * @JsonInclude excludes null fields from JSON output for cleaner responses.
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
   *
   * @param status  HTTP status code
   * @param error   HTTP status reason phrase
   * @param message human-readable error message
   * @param path    request path where the error occurred
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(status, error, message, path, Instant.now(), null);
  }

  /**
   * Create an ErrorResponse with field-level validation errors.
   *
   * @param status      HTTP status code
   * @param error       HTTP status reason phrase
   * @param message     human-readable error message
   * @param path        request path where the error occurred
   * @param fieldErrors map of field names to validation error messages
   */
  public ErrorResponse(int status, String error, String message, String path,
                       Map<String, String> fieldErrors) {
    this(status, error, message, path, Instant.now(), fieldErrors);
  }
}