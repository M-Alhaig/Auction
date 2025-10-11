package com.auction.itemservice.dto;

import com.auction.itemservice.models.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for returning auction item details to clients. Includes all item information including
 * system-managed fields (id, status, currentPrice, createdAt, updatedAt).
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
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Set<CategoryResponse> categories
) {

}
