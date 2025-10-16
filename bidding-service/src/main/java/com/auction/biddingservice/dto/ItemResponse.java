package com.auction.biddingservice.dto;

import com.auction.biddingservice.models.ItemStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for deserializing auction item details from Item Service API responses.
 *
 * <p>This record mirrors the ItemResponse from Item Service to enable synchronous
 * fallback calls when cache misses occur. Bidding Service uses this for:
 * <ul>
 *   <li>First bid validation - Checking starting price and auction status</li>
 *   <li>Cache warming - Populating Redis cache after cache miss</li>
 *   <li>Auction status verification - Fallback when ended auction not in cache</li>
 * </ul>
 *
 * <p>All timestamps are in UTC (ISO-8601 format) for timezone-aware operations
 * across distributed services.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>
 * // Cache miss scenario - fallback to Item Service API
 * ItemResponse item = itemServiceClient.getItem(itemId);
 * if (item.status() == ItemStatus.ACTIVE) {
 *     auctionCacheService.cacheAuctionMetadata(itemId, item.startingPrice(), item.endTime());
 * }
 * </pre>
 */
public record ItemResponse(
	Long id,
	UUID sellerId,
	String title,
	String description,
	BigDecimal startingPrice,
	BigDecimal currentPrice,
	String imageUrl,
	ItemStatus status,
	Instant startTime,
	Instant endTime,
	Instant createdAt,
	Instant updatedAt,
	Set<CategoryResponse> categories
) {

}
