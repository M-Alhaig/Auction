package com.auction.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for refreshing access token.
 *
 * <p>Requires three components for security:
 * <ul>
 *   <li>tokenId - O(1) database lookup</li>
 *   <li>refreshToken - BCrypt validation</li>
 *   <li>accessToken - Extract userId (signature must be valid, can be expired)</li>
 * </ul>
 */
public record TokenRefreshRequest(
    @NotNull(message = "Token ID is required")
    UUID tokenId,

    @NotBlank(message = "Refresh token is required")
    String refreshToken,

    @NotBlank(message = "Access token is required")
    String accessToken
) {}
