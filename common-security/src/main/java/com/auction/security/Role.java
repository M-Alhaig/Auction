package com.auction.security;

/**
 * User roles for the auction platform.
 *
 * <p>Roles are hierarchical: ADMIN > SELLER > BIDDER.
 * A higher role automatically has all permissions of lower roles.
 *
 * <p>Spring Security's RoleHierarchy bean should be configured to enforce this:
 * <pre>
 * RoleHierarchyImpl.withDefaultRolePrefix()
 *     .role("ADMIN").implies("SELLER")
 *     .role("SELLER").implies("BIDDER")
 *     .build();
 * </pre>
 */
public enum Role {

  /**
   * Default role for new users.
   * Can: Browse items, place bids (if email verified), view own bid history.
   */
  BIDDER(0),

  /**
   * Verified sellers who can create auctions.
   * Can: Everything BIDDER can + create/manage own auctions.
   * Note: Sellers cannot bid on their own items (enforced in business logic).
   */
  SELLER(1),

  /**
   * Platform administrators with full access.
   * Can: Everything + manage users, moderate content, view analytics.
   */
  ADMIN(2);

  private final int level;

  Role(int level) {
    this.level = level;
  }

  /**
   * Check if this role has at least the permissions of the required role.
   *
   * @param required the minimum role required
   * @return true if this role's level is >= required role's level
   */
  public boolean hasAtLeast(Role required) {
    return this.level >= required.level;
  }

  /**
   * Get the numeric level of this role for comparison.
   *
   * @return the role level (higher = more permissions)
   */
  public int getLevel() {
    return level;
  }
}
