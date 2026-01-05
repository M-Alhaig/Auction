package com.auction.userservice.security;

import com.auction.security.AuthConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter - runs once per request.
 *
 * <p>Extracts JWT from Authorization header, validates it,
 * and sets SecurityContext so Spring Security knows who's authenticated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractToken(request);

    if (token != null && jwtTokenProvider.validateToken(token)) {
      try {
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);
        boolean emailVerified = jwtTokenProvider.isEmailVerifiedFromToken(token);

        UserPrincipal principal = UserPrincipal.fromJwt(userId, email, role, emailVerified);

        // 3-arg constructor = authenticated token (null = no credentials needed, JWT already validated)
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user: {} with role: {}", email, role);

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
