package com.auction.item_service.dto;

/**
 * DTO for returning category information to clients.
 * Simple representation with just ID and name.
 */
public record CategoryResponse(
        Integer id,
        String name
) {
}
