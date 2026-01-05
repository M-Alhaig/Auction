package com.auction.userservice.repositories;

import com.auction.userservice.models.AuthCredential;
import com.auction.userservice.models.AuthProvider;
import com.auction.userservice.models.UserProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AuthCredential entity operations.
 *
 * <p>Supports multiple authentication methods per user.
 * Used for both local (email/password) and OAuth (Google, GitHub) authentication.
 */
@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, Long> {

  /**
   * Find a credential by OAuth provider and provider ID.
   * Used to look up existing users during OAuth login.
   *
   * <p>Example: Find user by their Google ID during Google Sign-In.
   *
   * @param provider the OAuth provider (GOOGLE, GITHUB)
   * @param providerId the provider's unique user identifier
   * @return the credential if found
   */
  Optional<AuthCredential> findByProviderAndProviderId(AuthProvider provider, String providerId);

  /**
   * Find a user's credential for a specific provider.
   * Used to check if user already has a linked auth method.
   *
   * <p>Example: Check if user already has Google linked before allowing link.
   *
   * @param user the user profile
   * @param provider the auth provider to check
   * @return the credential if exists
   */
  Optional<AuthCredential> findByUserAndProvider(UserProfile user, AuthProvider provider);

  /**
   * Get all credentials for a user.
   * Used to show linked authentication methods in settings.
   *
   * @param user the user profile
   * @return list of all credentials
   */
  List<AuthCredential> findByUser(UserProfile user);

  /**
   * Check if a credential exists for the given provider and provider ID.
   * Used to check for existing OAuth accounts before linking.
   *
   * @param provider the OAuth provider
   * @param providerId the provider's user ID
   * @return true if credential exists
   */
  boolean existsByProviderAndProviderId(AuthProvider provider, String providerId);
}
