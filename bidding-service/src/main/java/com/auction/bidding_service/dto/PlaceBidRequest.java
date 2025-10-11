package com.auction.bidding_service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO for placing a new bid on an auction item.
 * <p>
 * NOTE: bidderId is NOT included in this DTO - it is extracted from the authenticated user's JWT
 * token or X-Auth-Id header to prevent impersonation attacks. Clients cannot specify who is placing
 * the bid.
 * <p>
 * Validation Rules: - itemId must be provided (which auction to bid on) - bidAmount must be at
 * least $0.01 - bidAmount must have at most 10 integer digits and 2 decimal places
 * <p>
 * Additional validation (enforced in service layer): - Bid amount must be higher than current
 * highest bid - Auction must be in ACTIVE status - Bidder cannot bid on their own auction
 */
public record PlaceBidRequest(
    @NotNull(message = "Item ID is required")
    Long itemId,

    @NotNull(message = "Bid amount is required")
    @DecimalMin(value = "0.01", message = "Bid amount must be at least $0.01")
    @Digits(integer = 10, fraction = 2, message = "Bid amount must have at most 10 integer digits and 2 decimal places")
    BigDecimal bidAmount
) {

}
