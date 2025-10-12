package com.auction.biddingservice.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response structure for all API errors.
 * <p>
 * Follows RFC 7807 (Problem Details for HTTP APIs) principles.
 * <p>
 * Example JSON: { "status": 400, "error": "Bad Request", "message": "Bid amount must be higher than
 * current highest bid of $550.00", "path": "/api/bids", "timestamp": "2025-10-11T14:30:00",
 * "fieldErrors": { "bidAmount": "must be at least 0.01" } }
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
   * Create an ErrorResponse for an error without field-level validation details.
   *
   * The timestamp is set to the current time and fieldErrors will be null (omitted from JSON).
   *
   * @param status  the HTTP status code
   * @param error   the HTTP status reason phrase
   * @param message a human-readable error message
   * @param path    the request path where the error occurred
   */
  public ErrorResponse(int status, String error, String message, String path) {
    this(status, error, message, path, LocalDateTime.now(), null);
  }

  /**
   * Create an ErrorResponse for validation failures that includes field-level error messages.
   *
   * The created instance uses the current time as the `timestamp`.
   *
   * @param status      the HTTP status code
   * @param error       the HTTP status reason phrase
   * @param message     a human-readable error message
   * @param path        the request path where the error occurred
   * @param fieldErrors a map of field names to validation error messages; included in JSON only if non-null
   */
  public ErrorResponse(int status, String error, String message, String path,
      Map<String, String> fieldErrors) {
    this(status, error, message, path, LocalDateTime.now(), fieldErrors);
  }
}