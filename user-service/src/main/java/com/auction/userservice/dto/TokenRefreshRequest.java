package com.auction.userservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for refreshing access token.
 */
public record TokenRefreshRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
