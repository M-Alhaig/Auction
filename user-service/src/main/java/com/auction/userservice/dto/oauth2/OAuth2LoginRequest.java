package com.auction.userservice.dto.oauth2;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for OAuth2 authentication.
 *
 * <p>Frontend sends this after receiving the authorization code from the OAuth provider.
 * The code is exchanged for tokens on the backend (keeping client_secret secure).
 *
 * @param code        the authorization code from OAuth provider callback
 * @param redirectUri the redirect URI used in the OAuth flow (must match registered URI)
 */
public record OAuth2LoginRequest(
    @NotBlank(message = "Authorization code is required")
    String code,

    @NotBlank(message = "Redirect URI is required")
    String redirectUri
) {}
