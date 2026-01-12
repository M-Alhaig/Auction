package com.auction.userservice.controllers;

import com.auction.userservice.dto.AuthResponse;
import com.auction.userservice.dto.oauth2.OAuth2LoginRequest;
import com.auction.userservice.services.OAuth2AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for OAuth2 authentication endpoints.
 *
 * <p>Handles the backend part of the OAuth2 authorization code flow:
 * <ol>
 *   <li>Frontend redirects user to provider (Google/GitHub)</li>
 *   <li>Provider redirects back to frontend with authorization code</li>
 *   <li>Frontend sends code to these endpoints</li>
 *   <li>Backend exchanges code for tokens and returns JWT</li>
 * </ol>
 *
 * <p>Both endpoints return the same {@link AuthResponse} format as local login,
 * ensuring consistent frontend handling regardless of authentication method.
 */
@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {

  private final OAuth2AuthService oAuth2AuthService;

  /**
   * Authenticate user via Google OAuth2.
   *
   * <p>Exchange Google authorization code for user info and return JWT tokens.
   * If user doesn't exist, creates account. If email matches existing user,
   * links Google account automatically.
   *
   * @param request authorization code and redirect URI from frontend
   * @return JWT tokens and user info (same format as local login)
   */
  @PostMapping("/google")
  public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody OAuth2LoginRequest request) {
    log.debug("Google OAuth2 login request received");
    AuthResponse response = oAuth2AuthService.authenticateGoogle(request);
    return ResponseEntity.ok(response);
  }

  /**
   * Authenticate user via GitHub OAuth2.
   *
   * <p>Exchange GitHub authorization code for user info and return JWT tokens.
   * If user doesn't exist, creates account. If email matches existing user,
   * links GitHub account automatically.
   *
   * @param request authorization code and redirect URI from frontend
   * @return JWT tokens and user info (same format as local login)
   */
  @PostMapping("/github")
  public ResponseEntity<AuthResponse> githubLogin(@Valid @RequestBody OAuth2LoginRequest request) {
    log.debug("GitHub OAuth2 login request received");
    AuthResponse response = oAuth2AuthService.authenticateGitHub(request);
    return ResponseEntity.ok(response);
  }
}
