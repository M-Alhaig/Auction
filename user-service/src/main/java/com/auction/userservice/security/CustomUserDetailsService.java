package com.auction.userservice.security;

import com.auction.userservice.models.AuthCredential;
import com.auction.userservice.models.AuthProvider;
import com.auction.userservice.models.UserProfile;
import com.auction.userservice.repositories.AuthCredentialRepository;
import com.auction.userservice.repositories.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads user details for Spring Security authentication.
 *
 * <p>Called by AuthenticationManager during login to:
 * <ol>
 *   <li>Find user by email</li>
 *   <li>Load their LOCAL credential (password hash)</li>
 *   <li>Return UserPrincipal for Spring to validate password</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserProfileRepository userProfileRepository;
  private final AuthCredentialRepository authCredentialRepository;

  /**
   * Load user by email for authentication.
   *
   * @param email the user's email (used as username)
   * @return UserPrincipal with password hash for Spring to validate
   * @throws UsernameNotFoundException if user not found or no LOCAL credential
   */
  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    UserProfile user = userProfileRepository.findByEmailAndEnabledTrue(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

    AuthCredential localCredential = authCredentialRepository
        .findByUserAndProvider(user, AuthProvider.LOCAL)
        .orElseThrow(() -> new UsernameNotFoundException(
            "No password set for user: " + email + ". Try logging in with Google or GitHub."));

    return UserPrincipal.fromUser(user, localCredential.getPasswordHash());
  }
}
