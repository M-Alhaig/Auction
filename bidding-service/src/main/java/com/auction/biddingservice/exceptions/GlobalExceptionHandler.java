package com.auction.biddingservice.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for all REST controllers in Bidding Service. Catches exceptions and
 * converts them to standardized error responses. Logs all exceptions with appropriate severity
 * levels for monitoring and debugging.
 * <p>
 * Logging Strategy: - WARN for 4xx errors (client errors - expected in normal operation) - ERROR
 * for 5xx errors (server errors - indicate bugs or infrastructure issues)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	public static final String BAD_REQUEST = "Bad Request";

	/**
	 * Convert an InvalidBidException into an HTTP 400 Bad Request error response.
	 *
	 * @param ex      the InvalidBidException describing why the bid is invalid
	 * @param request the HTTP request whose URI is included in the error payload
	 * @return a ResponseEntity containing an ErrorResponse with status 400, the exception message,
	 * and the request URI
	 */
	@ExceptionHandler(InvalidBidException.class)
	public ResponseEntity<ErrorResponse> handleInvalidBid(InvalidBidException ex,
		HttpServletRequest request) {
		log.warn("Invalid bid attempt - path: {}, message: {}", request.getRequestURI(),
			ex.getMessage());

		ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), BAD_REQUEST,
			ex.getMessage(), request.getRequestURI());

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	/**
	 * Handle a bid lock conflict and produce a 409 Conflict response.
	 *
	 * @param ex      the BidLockException indicating another bid for the same item is being
	 *                processed
	 * @param request the HTTP request that triggered the exception (used to populate the request
	 *                path)
	 * @return a ResponseEntity containing an ErrorResponse with HTTP status 409, error "Conflict",
	 * the exception message, and the request URI
	 */
	@ExceptionHandler(BidLockException.class)
	public ResponseEntity<ErrorResponse> handleBidLock(BidLockException ex,
		HttpServletRequest request) {
		log.warn("Bid lock conflict - path: {}, message: {}", request.getRequestURI(),
			ex.getMessage());

		ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), "Conflict",
			ex.getMessage(), request.getRequestURI());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
	}

	/**
	 * Create a 404 Not Found error response for a missing auction.
	 *
	 * @param ex      the AuctionNotFoundException whose message will be included in the response
	 * @param request the HTTP request used to obtain the request URI for the response
	 * @return a ResponseEntity containing an ErrorResponse with HTTP 404 status, error "Not Found",
	 * the exception message, and the request URI
	 */
	@ExceptionHandler(AuctionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAuctionNotFound(AuctionNotFoundException ex,
		HttpServletRequest request) {
		log.warn("Auction not found - path: {}, message: {}", request.getRequestURI(),
			ex.getMessage());

		ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found",
			ex.getMessage(), request.getRequestURI());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	/**
	 * Converts a BidNotFoundException into an HTTP 404 Not Found ErrorResponse.
	 *
	 * @return an ErrorResponse containing HTTP status 404, error code "Not Found", the exception
	 * message, and the request URI
	 */
	@ExceptionHandler(BidNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleBidNotFound(BidNotFoundException ex,
		HttpServletRequest request) {
		log.warn("Bid not found - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

		ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found",
			ex.getMessage(), request.getRequestURI());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	/**
	 * Map an IllegalArgumentException to a 400 Bad Request response.
	 *
	 * <p>Logs a warning with the request URI and the exception message.
	 *
	 * @param ex      the IllegalArgumentException that occurred
	 * @param request the HTTP request whose URI is included in the response
	 * @return an ErrorResponse with HTTP status 400, error code "Bad Request", the exception
	 * message, and the request URI
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
		HttpServletRequest request) {
		log.warn("Invalid argument - path: {}, message: {}", request.getRequestURI(),
			ex.getMessage());

		ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), BAD_REQUEST,
			ex.getMessage(), request.getRequestURI());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	/**
	 * Convert a MethodArgumentNotValidException into a 400 Bad Request ErrorResponse containing
	 * field-level validation messages.
	 *
	 * @param ex      the validation exception containing binding results with field errors
	 * @param request the HTTP request used to extract the request URI for the response
	 * @return an ErrorResponse with HTTP status 400, error "Validation Failed", a generic
	 * validation message, the request path, and a map of field names to validation messages under
	 * `fieldErrors`
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex,
		HttpServletRequest request) {
		Map<String, String> fieldErrors = new HashMap<>();

		// Extract field-level errors
		ex.getBindingResult().getFieldErrors()
			.forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

		log.warn("Validation failed - path: {}, errors: {}", request.getRequestURI(), fieldErrors);

		ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Failed",
			"Input validation failed. Check fieldErrors for details.", request.getRequestURI(),
			fieldErrors);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	/**
	 * Handle validation errors on method parameters (path variables, request params).
	 *
	 * <p>Triggered when @Valid or validation annotations (@NotNull, @Min, etc.) are placed
	 * directly on controller method parameters rather than on request body objects.
	 *
	 * @param ex      the ConstraintViolationException containing all validation violations
	 * @param request the HTTP request whose URI is included in the error response
	 * @return an ErrorResponse with HTTP status 400, error "Validation Failed", field-level errors,
	 * and the request URI
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
		HttpServletRequest request) {
		Map<String, String> fieldErrors = new HashMap<>();

		// Extract clean field name and message from each violation
		ex.getConstraintViolations().forEach(violation -> {
			String fullPath = violation.getPropertyPath().toString();
			String field = fullPath.contains(".")
				? fullPath.substring(fullPath.lastIndexOf('.') + 1)
				: fullPath;
			fieldErrors.put(field, violation.getMessage());
		});

		log.warn("Constraint violation - path: {}, errors: {}", request.getRequestURI(),
			fieldErrors);

		ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Failed",
			"Input validation failed. Check fieldErrors for details.", request.getRequestURI(),
			fieldErrors);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	/**
	 * Handle type conversion failures for path variables and request parameters.
	 *
	 * <p>Triggered when Spring cannot convert a String parameter to the expected type
	 * (e.g., "abc" for UUID, "invalid" for Long, "not-a-date" for Instant).
	 *
	 * @param ex      the MethodArgumentTypeMismatchException describing the conversion failure
	 * @param request the HTTP request whose URI is included in the error response
	 * @return an ErrorResponse with HTTP status 400, error "Bad Request", a user-friendly message
	 * describing the parameter name and expected type, and the request URI
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
		HttpServletRequest request) {
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

		ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), BAD_REQUEST,
			message, request.getRequestURI());

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(AuctionEndedException.class)
	public ResponseEntity<ErrorResponse> handleAuctionEnded(AuctionEndedException ex,
		HttpServletRequest request) {
		log.warn("Auction ended - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

		ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), "Auction Ended",
			ex.getMessage(), request.getRequestURI());

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	/**
	 * Convert a malformed or unreadable HTTP request body into a 400 Bad Request error response.
	 *
	 * <p>If the exception's root cause is an IllegalArgumentException, its message is used as the
	 * error message; otherwise a generic "Invalid request body" message is returned.
	 *
	 * @return a ResponseEntity containing an ErrorResponse describing the bad request with HTTP
	 * status 400
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMessageNotReadable(
		HttpMessageNotReadableException ex,
		HttpServletRequest request) {
		// Extract the root cause message
		String message = "Invalid request body";
		Throwable cause = ex.getCause();

		while (cause != null) {
			if (cause instanceof IllegalArgumentException) {
				message = cause.getMessage();
				break;
			}
			cause = cause.getCause();
		}

		log.warn("Invalid request body - path: {}, message: {}", request.getRequestURI(), message);

		ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), BAD_REQUEST,
			message,
			request.getRequestURI());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	/**
	 * Convert any uncaught exception into a standardized 500 Internal Server Error response.
	 *
	 * @param ex      the unexpected exception that was thrown
	 * @param request the HTTP request that triggered the exception (used to populate the response
	 *                path)
	 * @return an ErrorResponse with HTTP status 500, error "Internal Server Error", a generic error
	 * message, and the request URI
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex,
		HttpServletRequest request) {
		// ERROR level with full stack trace - this is a bug or infrastructure failure
		log.error("Unexpected error - path: {}, exception: {}, message: {}",
			request.getRequestURI(),
			ex.getClass().getSimpleName(), ex.getMessage(), ex);

		ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
			"Internal Server Error", "An unexpected error occurred", request.getRequestURI());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
	}
}
