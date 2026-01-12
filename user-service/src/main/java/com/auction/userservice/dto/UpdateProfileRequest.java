package com.auction.userservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating user profile.
 * All fields are optional - only non-null fields are updated.
 */
public record UpdateProfileRequest(
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    String displayName,

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    String avatarUrl
) {}
