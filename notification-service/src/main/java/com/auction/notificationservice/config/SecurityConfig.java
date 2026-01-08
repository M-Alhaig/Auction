package com.auction.notificationservice.config;

import com.auction.security.JwtAuthenticationFilter;
import com.auction.security.JwtTokenValidator;
import com.auction.security.exception.SecurityExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Notification Service.
 *
 * <p>Stateless JWT authentication using shared common-security components.
 * WebSocket endpoints use separate authentication via handshake interceptor.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Value("${jwt.secret}")
  private String jwtSecret;

  private static final String[] PUBLIC_ENDPOINTS = {
      "/actuator/health",
      "/actuator/info",
      "/ws/**",           // WebSocket handshake (auth handled separately)
      "/websocket-test.html"
  };

  @Bean
  public JwtTokenValidator jwtTokenValidator() {
    return new JwtTokenValidator(jwtSecret);
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
    return new JwtAuthenticationFilter(jwtTokenValidator);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  JwtAuthenticationFilter jwtAuthenticationFilter,
                                                  SecurityExceptionHandler securityExceptionHandler) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
            .anyRequest().authenticated()
        )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(securityExceptionHandler)
            .accessDeniedHandler(securityExceptionHandler)
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .cors(Customizer.withDefaults())
        .build();
  }
}
