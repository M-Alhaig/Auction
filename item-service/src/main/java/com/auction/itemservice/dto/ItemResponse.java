package com.auction.itemservice.dto;

import com.auction.itemservice.models.ItemStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for returning auction item details to clients. Includes all item information including
 * system-managed fields (id, status, currentPrice, createdAt, updatedAt).
 * <p>
 * All timestamps are returned in UTC (ISO-8601 format with 'Z' suffix) for timezone-aware
 * operations across distributed services.
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
