package com.auction.userservice.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response structure for all API errors.
 *
 * <p>TODO: Extract to common-exceptions module to share across all services
 * and eliminate duplication (currently duplicated in item-service, bidding-service, etc.)
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
   * Create an ErrorResponse without field-level validation errors.
   *
   * @param status  HTTP status code
   * @param error   HTTP status reason phrase
   * @param message human-readable error message
   * @param path    request path where the error occurred
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(status, error, message, path, LocalDateTime.now(), null);
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
    this(status, error, message, path, LocalDateTime.now(), fieldErrors);
  }
}
