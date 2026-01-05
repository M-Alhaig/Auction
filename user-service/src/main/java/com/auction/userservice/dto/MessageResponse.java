package com.auction.userservice.dto;

/**
 * Simple response DTO for success messages.
 */
public record MessageResponse(String message) {

  public static MessageResponse of(String message) {
    return new MessageResponse(message);
  }
}
