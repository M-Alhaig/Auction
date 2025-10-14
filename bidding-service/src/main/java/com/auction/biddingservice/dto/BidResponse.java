package com.auction.biddingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for bid information returned to clients.
 *
 * <p>Fields:
 * <ul>
 *   <li>id: Unique bid identifier</li>
 *   <li>itemId: The auction item this bid is for</li>
 *   <li>bidderId: UUID of the user who placed the bid</li>
 *   <li>bidAmount: Dollar amount of the bid</li>
 *   <li>timestamp: When the bid was placed (auto-generated)</li>
 *   <li>isCurrentHighest: Whether this bid is currently the winning bid</li>
 * </ul>
 *
 * <p>Privacy Note: - In production, you may want to hide bidderId for non-owners to prevent bid
 * sniping strategies. For a portfolio demo, we show full details.
 *
 * <p>Timestamp Format: Returns ISO-8601 UTC timestamp (e.g., "2025-01-15T14:30:00Z") for consistent
 * timezone handling across distributed services.
 */
public record BidResponse(
    Long id,
    Long itemId,
    UUID bidderId,
    BigDecimal bidAmount,
    Instant timestamp,
    boolean isCurrentHighest
) {

}
