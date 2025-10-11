package com.auction.item_service.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for all REST controllers. Catches exceptions and converts them to
 * standardized error responses. Logs all exceptions with appropriate severity levels for monitoring
 * and debugging.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handle ItemNotFoundException. Returns 404 NOT FOUND.
   */
  @ExceptionHandler(ItemNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleItemNotFound(
      ItemNotFoundException ex,
      HttpServletRequest request
  ) {
    log.warn("Item not found - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.NOT_FOUND.value(),
        "Not Found",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  /**
   * Handle UnauthorizedException. Returns 403 FORBIDDEN.
   */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorized(
      UnauthorizedException ex,
      HttpServletRequest request
  ) {
    log.warn("Unauthorized access attempt - path: {}, message: {}", request.getRequestURI(),
        ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.FORBIDDEN.value(),
        "Forbidden",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  /**
   * Handle ConcurrentBidException. Returns 409 CONFLICT.
   */
  @ExceptionHandler(ConcurrentBidException.class)
  public ResponseEntity<ErrorResponse> handleConcurrentBid(
      ConcurrentBidException ex,
      HttpServletRequest request
  ) {
    log.warn("Concurrent bid conflict - path: {}, message: {}", request.getRequestURI(),
        ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.CONFLICT.value(),
        "Conflict",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  /**
   * Handle IllegalStateException (e.g., trying to update non-PENDING item). Returns 400 BAD
   * REQUEST.
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalState(
      IllegalStateException ex,
      HttpServletRequest request
  ) {
    log.warn("Illegal state - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "Bad Request",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle IllegalArgumentException (e.g., invalid categories, business rule violations). Returns
   * 400 BAD REQUEST.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex,
      HttpServletRequest request
  ) {
    log.warn("Invalid argument - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "Bad Request",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle validation errors from @Valid annotations. Returns 400 BAD REQUEST with field-level
   * error details.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationErrors(
      MethodArgumentNotValidException ex,
      HttpServletRequest request
  ) {
    Map<String, String> fieldErrors = new HashMap<>();

    // Extract field-level errors
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(error.getField(), error.getDefaultMessage());
    }

    log.warn("Validation failed - path: {}, errors: {}", request.getRequestURI(), fieldErrors);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "Validation Failed",
        "Input validation failed. Check fieldErrors for details.",
        request.getRequestURI(),
        fieldErrors
    );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle JSON deserialization errors (e.g., compact constructor validation in records). This
   * catches exceptions thrown during Jackson deserialization, including those from
   * CreateItemRequest's compact constructor validation. Returns 400 BAD REQUEST.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpServletRequest request
  ) {
    // Extract the root cause message (often contains the actual validation error)
    String message = "Invalid request body";
    Throwable cause = ex.getCause();

    // Drill down to find IllegalArgumentException from compact constructor
    while (cause != null) {
      if (cause instanceof IllegalArgumentException) {
        message = cause.getMessage();
        break;
      }
      cause = cause.getCause();
    }

    log.warn("Invalid request body - path: {}, message: {}", request.getRequestURI(), message);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "Bad Request",
        message,
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle all other unexpected exceptions. Returns 500 INTERNAL SERVER ERROR.
   * <p>
   * CRITICAL: This catches all unexpected errors. Always logs with full stack trace for debugging.
   * Monitor these logs closely - they indicate bugs or infrastructure issues.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneralException(
      Exception ex,
      HttpServletRequest request
  ) {
    // ERROR level with full stack trace - this is a bug or infrastructure failure
    log.error("Unexpected error - path: {}, exception: {}, message: {}",
        request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage(), ex);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "Internal Server Error",
        "An unexpected error occurred",
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
