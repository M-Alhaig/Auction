package com.auction.userservice.services;

import com.auction.events.UserRegisteredEvent;
import com.auction.userservice.client.GitHubOAuth2Client;
import com.auction.userservice.client.GoogleOAuth2Client;
import com.auction.userservice.dto.AuthResponse;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.dto.oauth2.OAuth2LoginRequest;
import com.auction.userservice.dto.oauth2.OAuth2UserInfo;
import com.auction.userservice.events.EventPublisher;
import com.auction.userservice.exceptions.OAuth2AccountLinkedException;
import com.auction.userservice.models.AuthCredential;
import com.auction.userservice.models.AuthProvider;
import com.auction.userservice.models.RefreshToken;
import com.auction.userservice.models.UserProfile;
import com.auction.userservice.repositories.AuthCredentialRepository;
import com.auction.userservice.repositories.RefreshTokenRepository;
import com.auction.userservice.repositories.UserProfileRepository;
import com.auction.userservice.security.JwtTokenProvider;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 authentication service implementation.
 *
 * <p>Handles user authentication via Google and GitHub OAuth2 providers.
 * Returns the same AuthResponse format as local login for consistent frontend handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthServiceImpl implements OAuth2AuthService {

  private final GoogleOAuth2Client googleClient;
  private final GitHubOAuth2Client gitHubClient;
  private final UserProfileRepository userRepository;
  private final AuthCredentialRepository credentialRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;
  private final EventPublisher eventPublisher;

  @Override
  @Transactional
  public AuthResponse authenticateGoogle(OAuth2LoginRequest request) {
    log.debug("Authenticating with Google");

    OAuth2UserInfo userInfo = googleClient.exchangeCodeForUserInfo(request.code(), request.redirectUri());
    UserProfile user = findOrCreateUser(userInfo, AuthProvider.GOOGLE);

    log.info("Google authentication successful: userId={}, email={}", user.getId(), user.getEmail());
    return generateAuthResponse(user);
  }

  @Override
  @Transactional
  public AuthResponse authenticateGitHub(OAuth2LoginRequest request) {
    log.debug("Authenticating with GitHub");

    OAuth2UserInfo userInfo = gitHubClient.exchangeCodeForUserInfo(request.code(), request.redirectUri());
    UserProfile user = findOrCreateUser(userInfo, AuthProvider.GITHUB);

    log.info("GitHub authentication successful: userId={}, email={}", user.getId(), user.getEmail());
    return generateAuthResponse(user);
  }

  /**
   * Find existing user or create new one, linking OAuth credential.
   *
   * <p>Account linking strategy:
   * <ol>
   *   <li>If OAuth providerId already linked → return that user</li>
   *   <li>If email matches existing user → auto-link OAuth credential</li>
   *   <li>Otherwise → create new user with OAuth credential</li>
   * </ol>
   */
  private UserProfile findOrCreateUser(OAuth2UserInfo userInfo, AuthProvider provider) {
    // Check if this OAuth account is already linked
    Optional<AuthCredential> existingCredential = credentialRepository
        .findByProviderAndProviderId(provider, userInfo.providerId());

    if (existingCredential.isPresent()) {
      // OAuth account already linked - return existing user
      UserProfile user = existingCredential.get().getUser();
      user.setLastLoginAt(Instant.now());
      log.debug("Existing {} user logged in: {}", provider, user.getEmail());
      return userRepository.save(user);
    }

    // Check if email matches existing user
    Optional<UserProfile> existingUser = userRepository.findByEmail(userInfo.email());

    if (existingUser.isPresent()) {
      // Link OAuth to existing account
      UserProfile user = existingUser.get();

      // Verify this providerId isn't linked to a DIFFERENT user
      if (credentialRepository.existsByProviderAndProviderId(provider, userInfo.providerId())) {
        throw new OAuth2AccountLinkedException(
            "This " + provider + " account is already linked to another user");
      }

      // Create and add OAuth credential
      AuthCredential credential = AuthCredential.createOAuth(user, provider, userInfo.providerId());
      user.addCredential(credential);

      // Update profile with OAuth info
      user.setLastLoginAt(Instant.now());
      user.setEmailVerified(true); // OAuth provider verified the email

      // Update avatar if not set
      if (user.getAvatarUrl() == null && userInfo.avatarUrl() != null) {
        user.setAvatarUrl(userInfo.avatarUrl());
      }

      log.info("Linked {} to existing user: {}", provider, user.getEmail());
      return userRepository.save(user);
    }

    // Create new user
    return createNewUser(userInfo, provider);
  }

  /**
   * Create a new user from OAuth provider info.
   */
  private UserProfile createNewUser(OAuth2UserInfo userInfo, AuthProvider provider) {
    UserProfile user = UserProfile.builder()
        .email(userInfo.email())
        .displayName(userInfo.name() != null ? userInfo.name() : extractNameFromEmail(userInfo.email()))
        .avatarUrl(userInfo.avatarUrl())
        .emailVerified(userInfo.emailVerified()) // OAuth providers verify email
        .build();

    // Create and add OAuth credential
    AuthCredential credential = AuthCredential.createOAuth(user, provider, userInfo.providerId());
    user.addCredential(credential);

    user = userRepository.save(user);
    log.info("Created new user via {}: userId={}, email={}", provider, user.getId(), user.getEmail());

    // Publish registration event
    eventPublisher.publish(UserRegisteredEvent.create(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRole().name(),
        provider.name()
    ));

    return user;
  }

  /**
   * Extract a display name from email if provider didn't give us one.
   * Example: "john.doe@gmail.com" → "john.doe"
   */
  private String extractNameFromEmail(String email) {
    return email.substring(0, email.indexOf('@'));
  }

  /**
   * Generate auth response with JWT tokens.
   * Same pattern as AuthServiceImpl for consistency.
   */
  private AuthResponse generateAuthResponse(UserProfile user) {
    String accessToken = jwtTokenProvider.generateAccessToken(user);
    String refreshToken = jwtTokenProvider.generateRefreshToken();
    String tokenFamily = jwtTokenProvider.generateTokenFamily();

    // Store hashed refresh token
    RefreshToken storedToken = RefreshToken.builder()
        .user(user)
        .tokenHash(passwordEncoder.encode(refreshToken))
        .tokenFamily(tokenFamily)
        .expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
        .build();
    storedToken = refreshTokenRepository.save(storedToken);

    return AuthResponse.of(
        accessToken,
        storedToken.getTokenId(),
        refreshToken,
        jwtTokenProvider.getAccessTokenExpirationSeconds(),
        UserResponse.fromEntity(user)
    );
  }
}
