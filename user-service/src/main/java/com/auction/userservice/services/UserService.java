package com.auction.userservice.services;

import com.auction.userservice.dto.UpdateProfileRequest;
import com.auction.userservice.dto.UserResponse;
import java.util.UUID;

/**
 * Service contract for user profile operations.
 */
public interface UserService {

  /**
   * Get user profile by ID.
   *
   * @param userId the user's ID
   * @return user profile response
   */
  UserResponse getUserById(UUID userId);

  /**
   * Update user profile.
   * Only non-null fields in the request are updated.
   *
   * @param userId the user's ID
   * @param request profile update details
   * @return updated user profile
   */
  UserResponse updateProfile(UUID userId, UpdateProfileRequest request);
}
