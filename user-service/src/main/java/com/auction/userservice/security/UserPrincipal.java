package com.auction.userservice.security;

import com.auction.security.AuthConstants;
import com.auction.userservice.models.UserProfile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security UserDetails implementation wrapping UserProfile.
 *
 * <p>Stored in SecurityContext after authentication. Access in controllers via:
 * {@code @AuthenticationPrincipal UserPrincipal principal}
 */
@Getter
public class UserPrincipal implements UserDetails {

  private final UUID id;
  private final String email;
  private final String password;
  private final String displayName;
  private final boolean emailVerified;
  private final boolean enabled;
  private final Collection<? extends GrantedAuthority> authorities;

  private UserPrincipal(UUID id, String email, String password, String displayName,
                        boolean emailVerified, boolean enabled,
                        Collection<? extends GrantedAuthority> authorities) {
    this.id = id;
    this.email = email;
    this.password = password;
    this.displayName = displayName;
    this.emailVerified = emailVerified;
    this.enabled = enabled;
    this.authorities = authorities;
  }

  /**
   * Create from UserProfile entity (used by CustomUserDetailsService).
   * Includes password hash for Spring Security to validate.
   */
  public static UserPrincipal fromUser(UserProfile user, String passwordHash) {
    String authority = AuthConstants.ROLE_PREFIX + user.getRole().name();
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

    return new UserPrincipal(
        user.getId(),
        user.getEmail(),
        passwordHash,
        user.getDisplayName(),
        user.isEmailVerified(),
        user.isEnabled(),
        authorities
    );
  }

  /**
   * Create from JWT claims (used by JwtAuthenticationFilter).
   * No password needed - already authenticated via token.
   */
  public static UserPrincipal fromJwt(UUID id, String email, String role, boolean emailVerified) {
    String authority = AuthConstants.ROLE_PREFIX + role;
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

    return new UserPrincipal(id, email, null, null, emailVerified, true, authorities);
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true; // We don't have account expiration
  }

  @Override
  public boolean isAccountNonLocked() {
    return true; // We don't have account locking
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true; // We don't have password expiration
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
