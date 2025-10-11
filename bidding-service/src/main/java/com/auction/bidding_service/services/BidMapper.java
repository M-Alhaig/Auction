package com.auction.bidding_service.services;

import com.auction.bidding_service.dto.BidResponse;
import com.auction.bidding_service.models.Bid;
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
   * Convert Bid entity to BidResponse DTO.
   *
   * @param bid              The bid entity from database
   * @param isCurrentHighest Whether this bid is currently the highest for the item
   * @return BidResponse DTO for API response
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
   * Convenience method for converting a bid that is definitely the current highest. Used when we
   * know for certain this bid is the winner (e.g., just placed successfully).
   */
  public BidResponse toBidResponseAsHighest(Bid bid) {
    return toBidResponse(bid, true);
  }

  /**
   * Convenience method for converting a bid that is NOT the current highest. Used for historical
   * bids in bid history queries.
   */
  public BidResponse toBidResponseAsHistorical(Bid bid) {
    return toBidResponse(bid, false);
  }
}
