package com.auction.userservice.services;

import com.auction.userservice.dto.AuthResponse;
import com.auction.userservice.dto.oauth2.OAuth2LoginRequest;

/**
 * Service for OAuth2 authentication (Google, GitHub).
 *
 * <p>Handles the backend part of the OAuth2 authorization code flow:
 * <ol>
 *   <li>Exchange authorization code for provider tokens</li>
 *   <li>Fetch user info from provider</li>
 *   <li>Find or create user in our database</li>
 *   <li>Generate our JWT tokens (same as local login)</li>
 * </ol>
 */
public interface OAuth2AuthService {

  /**
   * Authenticate user via Google OAuth2.
   *
   * @param request contains authorization code and redirect URI
   * @return AuthResponse with JWT tokens (same format as local login)
   */
  AuthResponse authenticateGoogle(OAuth2LoginRequest request);

  /**
   * Authenticate user via GitHub OAuth2.
   *
   * @param request contains authorization code and redirect URI
   * @return AuthResponse with JWT tokens (same format as local login)
   */
  AuthResponse authenticateGitHub(OAuth2LoginRequest request);
}
