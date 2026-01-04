package com.auction.notificationservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for Notification Service REST controllers.
 *
 * <p>Follows the same patterns as bidding-service and item-service:
 * <ul>
 *   <li>WARN level for 4xx errors (expected, client issues)</li>
 *   <li>ERROR level for 5xx errors (unexpected, bugs)</li>
 *   <li>Consistent ErrorResponse structure</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String BAD_REQUEST = "Bad Request";
  private static final String NOT_FOUND = "Not Found";

  /**
   * Handle IllegalArgumentException (invalid input data).
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.warn("Invalid argument - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(), BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle type mismatch errors (e.g., invalid UUID format, invalid number).
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

    String requiredType = ex.getRequiredType() != null
        ? ex.getRequiredType().getSimpleName()
        : "unknown";
    String message = String.format(
        "Invalid value '%s' for parameter '%s'. Expected type: %s",
        ex.getValue(), ex.getName(), requiredType);

    log.warn("Type mismatch - path: {}, message: {}", request.getRequestURI(), message);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(), BAD_REQUEST, message, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle missing required request header (e.g., X-Auth-Id).
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
      MissingRequestHeaderException ex, HttpServletRequest request) {

    String message = String.format("Required request header '%s' is missing", ex.getHeaderName());
    log.warn("Missing header - path: {}, header: {}", request.getRequestURI(), ex.getHeaderName());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(), BAD_REQUEST, message, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle resource not found (invalid endpoint).
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {

    log.warn("Endpoint not found - path: {}", request.getRequestURI());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.NOT_FOUND.value(), NOT_FOUND,
        "The requested endpoint does not exist", request.getRequestURI());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  /**
   * Handle requests using an unsupported HTTP method.
   *
   * <p>Triggered when a request is made with an HTTP method not supported by the endpoint
   * (e.g., POST to a PATCH-only endpoint, DELETE to a GET-only endpoint).
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

    String attempted = ex.getMethod();
    String[] supported = ex.getSupportedMethods();
    String supportedStr = supported != null ? String.join(", ", supported) : "unknown";
    String message = String.format("Method '%s' is not supported. Supported methods: %s",
        attempted, supportedStr);

    log.warn("Method not allowed - path: {}, attempted: {}, supported: {}",
        request.getRequestURI(), attempted, supportedStr);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed",
        message, request.getRequestURI());
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
  }

  /**
   * Catch-all handler for unexpected errors.
   * Logs at ERROR level (these indicate bugs).
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneralException(
      Exception ex, HttpServletRequest request) {

    log.error("Unexpected error - path: {}, exception: {}", request.getRequestURI(), ex.getMessage(), ex);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
        "An unexpected error occurred", request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
