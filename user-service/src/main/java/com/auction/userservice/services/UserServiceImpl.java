package com.auction.userservice.services;

import com.auction.events.UserUpdatedEvent;
import com.auction.userservice.dto.UpdateProfileRequest;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.events.EventPublisher;
import com.auction.userservice.exceptions.UserNotFoundException;
import com.auction.userservice.models.UserProfile;
import com.auction.userservice.repositories.UserProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

  private final UserProfileRepository userRepository;
  private final EventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public UserResponse getUserById(UUID userId) {
    UserProfile user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    return UserResponse.fromEntity(user);
  }

  @Override
  @Transactional
  public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
    UserProfile user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    // Only update non-null fields
    if (request.displayName() != null) {
      user.setDisplayName(request.displayName());
    }
    if (request.avatarUrl() != null) {
      user.setAvatarUrl(request.avatarUrl());
    }

    user = userRepository.save(user);

    log.info("Profile updated for user: {}", user.getEmail());

    // Publish profile update event
    eventPublisher.publish(UserUpdatedEvent.create(
        user.getId(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getRole().name()
    ));

    return UserResponse.fromEntity(user);
  }
}
