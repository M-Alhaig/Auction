package com.auction.biddingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for bid information returned to clients.
 * <p>
 * Fields: - id: Unique bid identifier - itemId: The auction item this bid is for - bidderId: UUID
 * of the user who placed the bid - bidAmount: Dollar amount of the bid - timestamp: When the bid
 * was placed in UTC (auto-generated) - isCurrentHighest: Whether this bid is currently the winning bid
 * <p>
 * Privacy Note: - In production, you may want to hide bidderId for non-owners to prevent bid
 * sniping strategies. For portfolio demo, we show full details.
 * <p>
 * Timestamp Format: Returns ISO-8601 UTC timestamp (e.g., "2025-01-15T14:30:00Z") for consistent
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
