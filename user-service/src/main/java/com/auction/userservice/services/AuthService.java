package com.auction.userservice.services;

import com.auction.userservice.dto.AuthResponse;
import com.auction.userservice.dto.LoginRequest;
import com.auction.userservice.dto.RegisterRequest;
import com.auction.userservice.dto.TokenRefreshRequest;
import java.util.UUID;

/**
 * Service contract for authentication operations.
 * Handles user registration, login, token refresh, and logout.
 */
public interface AuthService {

  /**
   * Register a new user with email and password.
   *
   * @param request registration details (email, password, displayName)
   * @return authentication response with tokens and user info
   */
  AuthResponse register(RegisterRequest request);

  /**
   * Authenticate user with email and password.
   *
   * @param request login credentials
   * @return authentication response with tokens and user info
   */
  AuthResponse login(LoginRequest request);

  /**
   * Refresh access token using a valid refresh token.
   * Implements token rotation - old token is revoked, new token issued.
   *
   * @param request the refresh token request
   * @param userId the authenticated user's ID (from JWT)
   * @return authentication response with new tokens
   */
  AuthResponse refreshToken(TokenRefreshRequest request, UUID userId);

  /**
   * Logout user by revoking all their refresh tokens.
   *
   * @param userId the user's ID
   */
  void logout(UUID userId);
}
