package com.auction.bidding_service.exceptions;

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
 * Global exception handler for all REST controllers in Bidding Service.
 * Catches exceptions and converts them to standardized error responses.
 * Logs all exceptions with appropriate severity levels for monitoring and debugging.
 *
 * Logging Strategy:
 * - WARN for 4xx errors (client errors - expected in normal operation)
 * - ERROR for 5xx errors (server errors - indicate bugs or infrastructure issues)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle InvalidBidException.
     * Returns 400 BAD REQUEST.
     *
     * Common causes:
     * - Bid amount not higher than current highest
     * - Auction not active
     * - User bidding on own auction
     */
    @ExceptionHandler(InvalidBidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBid(
            InvalidBidException ex,
            HttpServletRequest request
    ) {
        log.warn("Invalid bid attempt - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle BidLockException.
     * Returns 409 CONFLICT.
     *
     * Indicates another bid is being processed for the same item.
     * Client should retry after a short delay.
     */
    @ExceptionHandler(BidLockException.class)
    public ResponseEntity<ErrorResponse> handleBidLock(
            BidLockException ex,
            HttpServletRequest request
    ) {
        log.warn("Bid lock conflict - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle AuctionNotFoundException.
     * Returns 404 NOT FOUND.
     *
     * Item doesn't exist in Item Service or was deleted.
     */
    @ExceptionHandler(AuctionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuctionNotFound(
            AuctionNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("Auction not found - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle BidNotFoundException.
     * Returns 404 NOT FOUND.
     *
     * Specific bid ID doesn't exist in database.
     */
    @ExceptionHandler(BidNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBidNotFound(
            BidNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("Bid not found - path: {}, message: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle IllegalArgumentException (generic business rule violations).
     * Returns 400 BAD REQUEST.
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
     * Handle validation errors from @Valid annotations.
     * Returns 400 BAD REQUEST with field-level error details.
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
     * Handle JSON deserialization errors.
     * Returns 400 BAD REQUEST.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
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

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle all other unexpected exceptions.
     * Returns 500 INTERNAL SERVER ERROR.
     *
     * CRITICAL: This catches all unexpected errors. Always logs with full stack trace
     * for debugging. Monitor these logs closely - they indicate bugs or infrastructure issues.
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
