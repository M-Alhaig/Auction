package com.auction.userservice.dto.oauth2;

/**
 * Internal DTO representing user information from OAuth provider.
 *
 * <p>Normalized structure for both Google and GitHub responses.
 * Used internally by OAuth2AuthService, not exposed in API.
 *
 * @param providerId    unique user ID from the provider (Google: sub, GitHub: id)
 * @param email         user's email address
 * @param name          user's display name (may be null)
 * @param avatarUrl     URL to user's profile picture (may be null)
 * @param emailVerified whether the provider has verified the email
 */
public record OAuth2UserInfo(
    String providerId,
    String email,
    String name,
    String avatarUrl,
    boolean emailVerified
) {}
