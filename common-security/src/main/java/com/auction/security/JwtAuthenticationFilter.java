package com.auction.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter for downstream services.
 *
 * <p>Extracts JWT from Authorization header, validates it using JwtTokenValidator,
 * and sets SecurityContext with AuthenticatedUser principal.
 *
 * <p>This filter runs once per request and is shared across all services
 * that need JWT authentication (item, bidding, notification).
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenValidator jwtTokenValidator;

  public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
    this.jwtTokenValidator = jwtTokenValidator;
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractToken(request);

    if (token != null && jwtTokenValidator.validateToken(token)) {
      try {
        UUID userId = jwtTokenValidator.getUserIdFromToken(token);
        String email = jwtTokenValidator.getEmailFromToken(token);
        String role = jwtTokenValidator.getRoleFromToken(token);
        boolean emailVerified = jwtTokenValidator.isEmailVerifiedFromToken(token);
        boolean enabled = jwtTokenValidator.isEnabledFromToken(token);

        AuthenticatedUser principal = AuthenticatedUser.fromJwt(userId, email, role, emailVerified, enabled);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user: {} with role: {}, enabled: {}", email, role, enabled);

      } catch (Exception ex) {
        log.warn("Failed to set authentication from token: {}", ex.getMessage());
      }
    }

    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader(AuthConstants.AUTH_HEADER);

    if (StringUtils.hasText(header) && header.startsWith(AuthConstants.BEARER_PREFIX)) {
      return header.substring(AuthConstants.BEARER_PREFIX.length());
    }

    return null;
  }
}
