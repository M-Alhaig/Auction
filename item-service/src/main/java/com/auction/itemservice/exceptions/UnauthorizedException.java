package com.auction.itemservice.exceptions;

/**
 * Exception thrown when a user attempts an operation they're not authorized to perform. Typically
 * used when a user tries to modify an item they don't own.
 */
public class UnauthorizedException extends RuntimeException {

  /**
   * Constructs an UnauthorizedException with the specified detail message.
   *
   * @param message a descriptive message explaining why the operation is unauthorized
   */
  public UnauthorizedException(String message) {
    super(message);
  }
}