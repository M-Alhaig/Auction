package com.auction.itemservice.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  public static final String BAD_REQUEST = "Bad Request";

  /**
   * Convert an ItemNotFoundException into an HTTP 404 Not Found response.
   *
   * @return ResponseEntity containing an ErrorResponse with HTTP status 404, the exception message, and the request URI
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
   * Handle UnauthorizedException by returning an ErrorResponse for HTTP 403 Forbidden.
   *
   * @param ex the triggered UnauthorizedException
   * @param request the incoming request whose URI is included in the error response
   * @return ResponseEntity containing an ErrorResponse with HTTP status 403 (Forbidden),
   *         the exception message, and the request URI
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
   * Handle FreezeViolationException by returning an ErrorResponse for HTTP 403 Forbidden.
   *
   * <p>Triggered when a seller attempts to modify auction times (startTime or endTime)
   * within 24 hours of the auction's scheduled start. This enforces the freeze period
   * business rule to maintain fairness and trust.
   *
   * @param ex the triggered FreezeViolationException
   * @param request the incoming request whose URI is included in the error response
   * @return ResponseEntity containing an ErrorResponse with HTTP status 403 (Forbidden),
   *         the exception message describing the freeze period violation, and the request URI
   */
  @ExceptionHandler(FreezeViolationException.class)
  public ResponseEntity<ErrorResponse> handleFreezeViolation(
      FreezeViolationException ex,
      HttpServletRequest request
  ) {
    log.warn("Freeze period violation - path: {}, message: {}", request.getRequestURI(),
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
   * Convert a ConcurrentBidException into a 409 Conflict HTTP response.
   *
   * @return a ResponseEntity containing an ErrorResponse with HTTP status 409, error set to "Conflict",
   *         the exception message, and the originating request URI
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
   * Map an IllegalStateException to a 400 Bad Request ErrorResponse.
   *
   * <p>Common trigger: attempting an operation that is not allowed in the current state
   * (for example, updating an item that is not in the PENDING state).
   *
   * @param ex the IllegalStateException that was thrown
   * @param request the incoming HTTP request; its URI is included in the ErrorResponse
   * @return a ResponseEntity containing an ErrorResponse with HTTP status 400 and the exception message
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalState(
      IllegalStateException ex,
      HttpServletRequest request
  ) {
    log.warn("Illegal state - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        BAD_REQUEST,
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Converts an IllegalArgumentException into a 400 Bad Request response.
   *
   * @param ex the IllegalArgumentException whose message is used as the error message
   * @param request the HTTP request used to populate the request URI in the response
   * @return a ResponseEntity containing an ErrorResponse with HTTP status 400, error label "Bad Request", the exception message, and the request URI
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex,
      HttpServletRequest request
  ) {
    log.warn("Invalid argument - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        BAD_REQUEST,
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Convert a MethodArgumentNotValidException into a 400 Bad Request ErrorResponse containing field-level validation messages.
   *
   * <p>Populates the response's fieldErrors with a map of field names to their validation messages and includes the request URI.
   *
   * @param ex the validation exception containing binding results with field errors
   * @param request the HTTP request used to extract the request URI for the response
   * @return an ErrorResponse with status 400, error "Validation Failed", a summary message, the request URI, and a map of field-level errors
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationErrors(
      MethodArgumentNotValidException ex,
      HttpServletRequest request
  ) {
    Map<String, String> fieldErrors = new HashMap<>();

    // Extract field-level errors
	  ex.getBindingResult().getFieldErrors()
		  .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

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
   * Handle validation errors on method parameters (path variables, request params).
   *
   * <p>Triggered when @Valid or validation annotations (@NotNull, @Min, etc.) are placed
   * directly on controller method parameters rather than on request body objects.
   *
   * @param ex the ConstraintViolationException containing all validation violations
   * @param request the HTTP request whose URI is included in the error response
   * @return an ErrorResponse with HTTP status 400, error "Validation Failed", field-level errors,
   *         and the request URI
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex,
      HttpServletRequest request
  ) {
    Map<String, String> fieldErrors = new HashMap<>();

    // Extract clean field name and message from each violation
    ex.getConstraintViolations().forEach(violation -> {
      String fullPath = violation.getPropertyPath().toString();
      String field = fullPath.contains(".")
          ? fullPath.substring(fullPath.lastIndexOf('.') + 1)
          : fullPath;
      fieldErrors.put(field, violation.getMessage());
    });

    log.warn("Constraint violation - path: {}, errors: {}", request.getRequestURI(), fieldErrors);

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
   * Handle type conversion failures for path variables and request parameters.
   *
   * <p>Triggered when Spring cannot convert a String parameter to the expected type
   * (e.g., "abc" for Long, "invalid-uuid" for UUID, "not-a-date" for Instant).
   *
   * @param ex the MethodArgumentTypeMismatchException describing the conversion failure
   * @param request the HTTP request whose URI is included in the error response
   * @return an ErrorResponse with HTTP status 400, error "Bad Request", a user-friendly message
   *         describing the parameter name and expected type, and the request URI
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex,
      HttpServletRequest request
  ) {
    String paramName = ex.getName();
    String paramValue = String.valueOf(ex.getValue());
    String expectedType = ex.getRequiredType() != null
        ? ex.getRequiredType().getSimpleName()
        : "unknown";

    String message = String.format(
        "Invalid value '%s' for parameter '%s'. Expected type: %s",
        paramValue, paramName, expectedType
    );

    log.warn("Type mismatch - path: {}, param: {}, value: {}, expectedType: {}",
        request.getRequestURI(), paramName, paramValue, expectedType);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        BAD_REQUEST,
        message,
        request.getRequestURI()
    );

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Convert JSON deserialization failures into a standardized 400 Bad Request ErrorResponse.
   *
   * <p>Extracts a user-facing message from the exception cause chain when available (for example,
   * validation errors propagated as IllegalArgumentException) and returns an ErrorResponse
   * containing the HTTP status, error label, message, and request URI.
   *
   * @param ex      the HttpMessageNotReadableException thrown during request body deserialization
   * @param request the incoming HTTP request; used to populate the request URI in the response
   * @return an ErrorResponse with status 400, error label "Bad Request", a descriptive message,
   *         and the request URI
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
        BAD_REQUEST,
        message,
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle requests to non-existent endpoints or resources.
   *
   * <p>Triggered when Spring cannot find a matching endpoint for the request path,
   * such as typos in URLs, incorrect HTTP methods, or accessing non-existent static resources.
   *
   * @param ex the NoResourceFoundException thrown by Spring MVC
   * @param request the HTTP request whose URI is included in the error response
   * @return an ErrorResponse with HTTP status 404, error "Not Found", a message indicating
   *         the endpoint does not exist, and the request URI
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(
      NoResourceFoundException ex,
      HttpServletRequest request
  ) {
    log.warn("Endpoint not found - path: {}, method: {}", request.getRequestURI(),
        request.getMethod());

    ErrorResponse error = new ErrorResponse(
        HttpStatus.NOT_FOUND.value(),
        "Not Found",
        "The requested endpoint does not exist",
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  /**
   * Handle missing required request headers.
   *
   * <p>Triggered when a controller method requires a request header (e.g., X-Auth-Id)
   * but the header is not present in the request or is present but empty.
   *
   * @param ex the MissingRequestHeaderException describing which header is missing
   * @param request the HTTP request whose URI is included in the error response
   * @return an ErrorResponse with HTTP status 400, error "Bad Request", a message indicating
   *         which header is required, and the request URI
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
      MissingRequestHeaderException ex,
      HttpServletRequest request
  ) {
    String headerName = ex.getHeaderName();
    String message = String.format("Required request header '%s' is missing or empty", headerName);

    log.warn("Missing required header - path: {}, header: {}", request.getRequestURI(),
        headerName);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        BAD_REQUEST,
        message,
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  /**
   * Handle requests using an unsupported HTTP method.
   *
   * <p>Triggered when a request is made with an HTTP method not supported by the endpoint
   * (e.g., POST to a PATCH-only endpoint, DELETE to a GET-only endpoint).
   *
   * @param ex the HttpRequestMethodNotSupportedException describing which method was attempted
   * @param request the HTTP request whose URI is included in the error response
   * @return an ErrorResponse with HTTP status 405, error "Method Not Allowed", the supported methods,
   *         and the request URI
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpServletRequest request
  ) {
    String attempted = ex.getMethod();
    String[] supported = ex.getSupportedMethods();
    String supportedStr = supported != null ? String.join(", ", supported) : "unknown";
    String message = String.format("Method '%s' is not supported. Supported methods: %s",
        attempted, supportedStr);

    log.warn("Method not allowed - path: {}, attempted: {}, supported: {}",
        request.getRequestURI(), attempted, supportedStr);

    ErrorResponse error = new ErrorResponse(
        HttpStatus.METHOD_NOT_ALLOWED.value(),
        "Method Not Allowed",
        message,
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
  }

  /**
   * Handles uncaught exceptions by converting them into a standardized 500 Internal Server Error response.
   *
   * @param ex the unexpected exception that was thrown
   * @param request the HTTP request whose URI will be included in the error response
   * @return a ResponseEntity containing an ErrorResponse with HTTP status 500, error "Internal Server Error",
   *         a generic message indicating an unexpected error occurred, and the request URI
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
