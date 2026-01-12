package com.auction.userservice.exceptions;

/**
 * Thrown when a JWT or refresh token has expired.
 */
public class TokenExpiredException extends RuntimeException {

  public TokenExpiredException(String message) {
    super(message);
  }

  public static TokenExpiredException accessToken() {
    return new TokenExpiredException("Access token has expired");
  }

  public static TokenExpiredException refreshToken() {
    return new TokenExpiredException("Refresh token has expired");
  }
}
