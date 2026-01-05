package com.auction.userservice.controllers;

import com.auction.userservice.dto.AuthResponse;
import com.auction.userservice.dto.LoginRequest;
import com.auction.userservice.dto.MessageResponse;
import com.auction.userservice.dto.RegisterRequest;
import com.auction.userservice.dto.TokenRefreshRequest;
import com.auction.userservice.security.UserPrincipal;
import com.auction.userservice.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<AuthResponse> refresh(
      @Valid @RequestBody TokenRefreshRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    AuthResponse response = authService.refreshToken(request, principal.getId());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal UserPrincipal principal) {
    authService.logout(principal.getId());
    return ResponseEntity.ok(MessageResponse.of("Logged out successfully"));
  }
}
