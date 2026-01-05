package com.auction.userservice.exceptions;

/**
 * Thrown when login credentials are invalid.
 *
 * <p>Generic message to prevent email enumeration attacks.
 * Don't reveal whether email exists or password is wrong.
 */
public class InvalidCredentialsException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "Invalid email or password";

  public InvalidCredentialsException() {
    super(DEFAULT_MESSAGE);
  }

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
