package com.auction.biddingservice.dto;

/**
 * DTO for category information in Item Service responses.
 *
 * <p>This mirrors the CategoryResponse from Item Service for API response deserialization.
 * Bidding Service doesn't need full category details, but must deserialize complete ItemResponse.
 */
public record CategoryResponse(
	Integer id,
	String name
) {

}
