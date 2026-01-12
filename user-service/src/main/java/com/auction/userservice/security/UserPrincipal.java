package com.auction.userservice.security;

import com.auction.security.AuthConstants;
import com.auction.security.AuthenticatedUser;
import com.auction.userservice.models.UserProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * User-service specific principal that extends AuthenticatedUser.
 *
 * <p>Adds password field for login validation via DaoAuthenticationProvider.
 * Implements CredentialsContainer to clear password after authentication.
 *
 * <p>For JWT-authenticated requests, use AuthenticatedUser directly (no password needed).
 */
public class UserPrincipal extends AuthenticatedUser implements CredentialsContainer {

  private String password;

  private UserPrincipal(UUID id, String email, String role, boolean emailVerified,
                        boolean enabled, List<GrantedAuthority> authorities, String password) {
    super(id, email, role, emailVerified, enabled, authorities);
    this.password = password;
  }

  /**
   * Create from UserProfile entity (used by CustomUserDetailsService for login).
   * Includes password hash for Spring Security's DaoAuthenticationProvider to validate.
   */
  public static UserPrincipal fromUser(UserProfile user, String passwordHash) {
    String authority = AuthConstants.ROLE_PREFIX + user.getRole().name();
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

    return new UserPrincipal(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.isEmailVerified(),
        user.isEnabled(),
        authorities,
        passwordHash
    );
  }

  @Override
  public String getPassword() {
    return password;
  }

  /**
   * Clear password after authentication (called by Spring Security).
   * Prevents password hash from lingering in SecurityContext.
   */
  @Override
  public void eraseCredentials() {
    this.password = null;
  }
}
