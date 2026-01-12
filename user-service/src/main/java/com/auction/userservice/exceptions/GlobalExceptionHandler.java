package com.auction.userservice.exceptions;

import com.auction.security.EmailNotVerifiedException;
import com.auction.security.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for User Service.
 *
 * <p>Maps exceptions to appropriate HTTP status codes and error responses.
 * Follows logging conventions: WARN for 4xx, ERROR for 5xx.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // ============== 400 Bad Request ==============

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationErrors(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    Map<String, String> errors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }

    log.warn("Validation failed for {}: {}", request.getRequestURI(), errors);

    return ResponseEntity.badRequest()
        .body(new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "One or more fields have invalid values",
            request.getRequestURI(),
            errors
        ));
  }

  // ============== 401 Unauthorized ==============

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {

    log.warn("Invalid credentials attempt from {}", request.getRemoteAddr());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest request) {

    log.warn("Bad credentials from {}", request.getRemoteAddr());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            "Invalid email or password",
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(TokenExpiredException.class)
  public ResponseEntity<ErrorResponse> handleTokenExpired(
      TokenExpiredException ex, HttpServletRequest request) {

    log.warn("Expired token used: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Token Expired",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(TokenReusedException.class)
  public ResponseEntity<ErrorResponse> handleTokenReuse(
      TokenReusedException ex, HttpServletRequest request) {

    log.warn("{}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Security Alert",
            "Session invalidated. Please log in again.",
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthenticationException(
      AuthenticationException ex, HttpServletRequest request) {

    log.warn("Authentication failed: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            "Authentication required",
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(OAuth2AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleOAuth2Authentication(
      OAuth2AuthenticationException ex, HttpServletRequest request) {

    log.warn("OAuth2 authentication failed: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "OAuth2 Authentication Failed",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  // ============== 403 Forbidden ==============

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {

    log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            "You don't have permission to access this resource",
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(EmailNotVerifiedException.class)
  public ResponseEntity<ErrorResponse> handleEmailNotVerified(
      EmailNotVerifiedException ex, HttpServletRequest request) {

    log.warn("Unverified email attempted action: {}", request.getRequestURI());

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Email Not Verified",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  // ============== 404 Not Found ==============

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleUserNotFound(
      UserNotFoundException ex, HttpServletRequest request) {

    log.warn("User not found: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  // ============== 405 Method Not Allowed ==============

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

  // ============== 409 Conflict ==============

  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleEmailExists(
      EmailAlreadyExistsException ex, HttpServletRequest request) {

    log.warn("Duplicate email registration attempt: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  @ExceptionHandler(OAuth2AccountLinkedException.class)
  public ResponseEntity<ErrorResponse> handleOAuth2AccountLinked(
      OAuth2AccountLinkedException ex, HttpServletRequest request) {

    log.warn("OAuth2 account already linked: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Account Already Linked",
            ex.getMessage(),
            request.getRequestURI()
        ));
  }

  // ============== 500 Internal Server Error ==============

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
      Exception ex, HttpServletRequest request) {

    log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI()
        ));
  }
}
