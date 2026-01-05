package com.auction.userservice.exceptions;

import java.util.UUID;

/**
 * Thrown when a user is not found by ID or email.
 */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(String message) {
    super(message);
  }

  public UserNotFoundException(UUID userId) {
    super("User not found with ID: " + userId);
  }

  public static UserNotFoundException byEmail(String email) {
    return new UserNotFoundException("User not found with email: " + email);
  }

  public static UserNotFoundException byId(UUID id) {
    return new UserNotFoundException(id);
  }
}
