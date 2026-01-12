package com.auction.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * Centralized role hierarchy configuration for all services.
 *
 * <p>Hierarchy: ADMIN > SELLER > BIDDER
 * <ul>
 *   <li>ADMIN - Full platform access (inherits SELLER + BIDDER)</li>
 *   <li>SELLER - Can create auctions (inherits BIDDER)</li>
 *   <li>BIDDER - Can browse and bid</li>
 * </ul>
 *
 * <p>With this configuration, {@code @PreAuthorize("hasRole('SELLER')")} will
 * automatically pass for ADMIN users without needing {@code hasAnyRole('ADMIN', 'SELLER')}.
 *
 * <p>This bean is auto-detected by Spring Boot and wired into both web security
 * and method security ({@code @PreAuthorize}).
 */
@Configuration
public class RoleHierarchyConfig {

  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role("ADMIN").implies("SELLER")
        .role("SELLER").implies("BIDDER")
        .build();
  }
}
