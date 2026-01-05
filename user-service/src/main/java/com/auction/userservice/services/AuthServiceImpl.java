package com.auction.userservice.services;

import com.auction.userservice.dto.AuthResponse;
import com.auction.userservice.dto.LoginRequest;
import com.auction.userservice.dto.RegisterRequest;
import com.auction.userservice.dto.TokenRefreshRequest;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.exceptions.EmailAlreadyExistsException;
import com.auction.userservice.exceptions.InvalidCredentialsException;
import com.auction.userservice.exceptions.TokenExpiredException;
import com.auction.userservice.exceptions.TokenReusedException;
import com.auction.userservice.models.AuthCredential;
import com.auction.userservice.models.AuthProvider;
import com.auction.userservice.models.RefreshToken;
import com.auction.userservice.models.UserProfile;
import com.auction.userservice.repositories.RefreshTokenRepository;
import com.auction.userservice.repositories.UserProfileRepository;
import com.auction.userservice.security.JwtTokenProvider;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

  private final UserProfileRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;

  @Override
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyExistsException(request.email());
    }

    // Create user profile
    UserProfile user = UserProfile.builder()
        .email(request.email())
        .displayName(request.displayName())
        .build();

    // Create LOCAL auth credential with hashed password
    AuthCredential credential = AuthCredential.builder()
        .provider(AuthProvider.LOCAL)
        .passwordHash(passwordEncoder.encode(request.password()))
        .build();

    // Add credential to user - cascade will persist it
    user.addCredential(credential);
    user = userRepository.save(user);

    log.info("User registered: {}", user.getEmail());

    return generateAuthResponse(user);
  }

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.email(), request.password())
      );
    } catch (BadCredentialsException e) {
      throw new InvalidCredentialsException();
    }

    UserProfile user = userRepository.findByEmailAndEnabledTrue(request.email())
        .orElseThrow(InvalidCredentialsException::new);

    // Update last login timestamp
    user.setLastLoginAt(Instant.now());
    userRepository.save(user);

    log.info("User logged in: {}", user.getEmail());

    return generateAuthResponse(user);
  }

  @Override
  @Transactional
  public AuthResponse refreshToken(TokenRefreshRequest request, UUID userId) {
    UserProfile user = userRepository.findById(userId)
        .orElseThrow(InvalidCredentialsException::new);

    // Find valid token by matching the hash
    RefreshToken storedToken = refreshTokenRepository.findActiveTokensByUserId(userId).stream()
        .filter(token -> passwordEncoder.matches(request.refreshToken(), token.getTokenHash()))
        .findFirst()
        .orElseThrow(() -> new TokenExpiredException("Refresh token is invalid or expired"));

    // Check if token was already used (reuse detection)
    if (storedToken.isRevoked()) {
      // Token reuse detected - revoke entire family
      refreshTokenRepository.revokeByTokenFamily(storedToken.getTokenFamily());
      log.warn("Token reuse detected for user {}, family {}", userId, storedToken.getTokenFamily());
      throw new TokenReusedException("Token has already been used. All sessions revoked for security.");
    }

    // Revoke the current token (rotation)
    storedToken.setRevoked(true);
    refreshTokenRepository.save(storedToken);

    log.info("Token refreshed for user: {}", user.getEmail());

    // Generate new tokens in the same family
    return generateAuthResponse(user, storedToken.getTokenFamily());
  }

  @Override
  @Transactional
  public void logout(UUID userId) {
    int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);
    log.info("Logged out user {}, revoked {} tokens", userId, revokedCount);
  }

  /**
   * Generate auth response with new token family.
   */
  private AuthResponse generateAuthResponse(UserProfile user) {
    return generateAuthResponse(user, jwtTokenProvider.generateTokenFamily());
  }

  /**
   * Generate auth response with specified token family (for rotation).
   */
  private AuthResponse generateAuthResponse(UserProfile user, String tokenFamily) {
    String accessToken = jwtTokenProvider.generateAccessToken(user);
    String refreshToken = jwtTokenProvider.generateRefreshToken();

    // Store hashed refresh token
    RefreshToken storedToken = RefreshToken.builder()
        .user(user)
        .tokenHash(passwordEncoder.encode(refreshToken))
        .tokenFamily(tokenFamily)
        .expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
        .build();
    refreshTokenRepository.save(storedToken);

    return AuthResponse.of(
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenExpirationSeconds(),
        UserResponse.fromEntity(user)
    );
  }
}
