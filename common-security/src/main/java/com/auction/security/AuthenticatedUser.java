package com.auction.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Base authenticated user principal for JWT-based authentication.
 *
 * <p>Contains only authorization-essential claims from JWT:
 * <ul>
 *   <li>id - user identity</li>
 *   <li>email - user identity</li>
 *   <li>role - authorization</li>
 *   <li>emailVerified - authorization</li>
 *   <li>enabled - account status</li>
 * </ul>
 *
 * <p>Used by downstream services (item, bidding, notification) after JWT validation.
 * Access in controllers via: {@code @AuthenticationPrincipal AuthenticatedUser user}
 *
 * <p>User-service extends this with UserPrincipal to add password for login flow.
 */
@Getter
public class AuthenticatedUser implements UserDetails {

  private final UUID id;
  private final String email;
  private final String role;
  private final boolean emailVerified;
  private final boolean enabled;
  private final Collection<? extends GrantedAuthority> authorities;

  protected AuthenticatedUser(UUID id, String email, String role, boolean emailVerified,
                              boolean enabled, Collection<? extends GrantedAuthority> authorities) {
    this.id = id;
    this.email = email;
    this.role = role;
    this.emailVerified = emailVerified;
    this.enabled = enabled;
    this.authorities = authorities;
  }

  /**
   * Create from JWT claims (used by JwtAuthenticationFilter after token validation).
   */
  public static AuthenticatedUser fromJwt(UUID id, String email, String role,
                                          boolean emailVerified, boolean enabled) {
    String authority = AuthConstants.ROLE_PREFIX + role;
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

    return new AuthenticatedUser(id, email, role, emailVerified, enabled, authorities);
  }

  @Override
  public String getPassword() {
    return null; // No password - JWT already validated
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
