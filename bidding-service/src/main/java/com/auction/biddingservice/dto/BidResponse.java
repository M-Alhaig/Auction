package com.auction.biddingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for bid information returned to clients.
 * <p>
 * Fields: - id: Unique bid identifier - itemId: The auction item this bid is for - bidderId: UUID
 * of the user who placed the bid - bidAmount: Dollar amount of the bid - timestamp: When the bid
 * was placed (auto-generated) - isCurrentHighest: Whether this bid is currently the winning bid
 * <p>
 * Privacy Note: - In production, you may want to hide bidderId for non-owners to prevent bid
 * sniping strategies. For portfolio demo, we show full details.
 */
public record BidResponse(
    Long id,
    Long itemId,
    UUID bidderId,
    BigDecimal bidAmount,
    LocalDateTime timestamp,
    boolean isCurrentHighest
) {

}
