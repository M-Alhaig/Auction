package com.auction.biddingservice.services;

import com.auction.biddingservice.dto.BidResponse;
import com.auction.biddingservice.models.Bid;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting between Bid entities and DTOs.
 *
 * <p>Responsibilities: - Entity → DTO (for API responses) - Determines if a bid is the current
 * highest
 * (requires context from service layer)
 *
 * <p>Note: No DTO → Entity mapping needed. Bids are created directly in service layer to ensure
 * proper
 * validation and Redis locking before persistence.
 */
@Component
public class BidMapper {

  /**
   * Convert a Bid entity into a BidResponse DTO.
   *
   * @param bid the Bid entity to convert; may be null
   * @param isCurrentHighest whether this bid is currently the highest for its item
   * @return the corresponding BidResponse, or null if {@code bid} is null
   */
  public BidResponse toBidResponse(Bid bid, boolean isCurrentHighest) {
    if (bid == null) {
      return null;
    }

    return new BidResponse(
        bid.getId(),
        bid.getItemId(),
        bid.getBidderId(),
        bid.getBidAmount(),
        bid.getTimestamp(),
        isCurrentHighest
    );
  }

  /**
   * Convert a Bid entity into a BidResponse marked as the current highest bid.
   *
   * @param bid the Bid entity to convert; may be null
   * @return the corresponding BidResponse with the current-highest flag set, or `null` if {@code bid} is null
   */
  public BidResponse toBidResponseAsHighest(Bid bid) {
    return toBidResponse(bid, true);
  }

  /**
   * Convert a Bid entity to a BidResponse representing a historical (non-highest) bid.
   *
   * @param bid the Bid to convert; may be null
   * @return the corresponding BidResponse with isCurrentHighest set to `false`, or `null` if {@code bid} is null
   */
  public BidResponse toBidResponseAsHistorical(Bid bid) {
    return toBidResponse(bid, false);
  }
}