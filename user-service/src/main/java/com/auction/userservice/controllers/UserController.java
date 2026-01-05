package com.auction.userservice.controllers;

import com.auction.userservice.dto.UpdateProfileRequest;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.security.UserPrincipal;
import com.auction.userservice.services.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  /**
   * Get current authenticated user's profile.
   */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UserResponse> getCurrentUser(
      @AuthenticationPrincipal UserPrincipal principal) {
    UserResponse response = userService.getUserById(principal.getId());
    return ResponseEntity.ok(response);
  }

  /**
   * Update current user's profile.
   */
  @PatchMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UserResponse> updateProfile(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request) {
    UserResponse response = userService.updateProfile(principal.getId(), request);
    return ResponseEntity.ok(response);
  }

  /**
   * Get public user profile by ID.
   */
  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
    UserResponse response = userService.getUserById(id);
    return ResponseEntity.ok(response);
  }
}
