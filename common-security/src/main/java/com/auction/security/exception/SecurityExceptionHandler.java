package com.auction.security.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Handles security exceptions in the filter chain (before reaching controllers).
 *
 * <p>Implements both:
 * <ul>
 *   <li>{@link AuthenticationEntryPoint} - 401 when no/invalid token</li>
 *   <li>{@link AccessDeniedHandler} - 403 when authenticated but insufficient permissions</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(HttpServletRequest request,
                       HttpServletResponse response,
                       AuthenticationException ex) throws IOException {
    log.warn("Unauthorized request to {}: {}", request.getRequestURI(), ex.getMessage());

    writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
        "Authentication required", request.getRequestURI());
  }

  @Override
  public void handle(HttpServletRequest request,
                     HttpServletResponse response,
                     AccessDeniedException ex) throws IOException {
    log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());

    writeErrorResponse(response, HttpStatus.FORBIDDEN, "Forbidden",
        "You don't have permission to access this resource", request.getRequestURI());
  }

  private void writeErrorResponse(HttpServletResponse response, HttpStatus status,
                                  String error, String message, String path) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    ErrorResponse errorResponse = new ErrorResponse(status.value(), error, message, path);
    objectMapper.writeValue(response.getOutputStream(), errorResponse);
  }
}
