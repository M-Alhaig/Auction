package com.auction.item_service.services;

import com.auction.item_service.dto.CategoryResponse;
import com.auction.item_service.dto.CreateItemRequest;
import com.auction.item_service.dto.ItemResponse;
import com.auction.item_service.models.Category;
import com.auction.item_service.models.Item;
import com.auction.item_service.models.ItemStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper component for entity ↔ DTO conversions.
 * Contains pure transformation logic with no business rules.
 * Update logic is handled in the service layer.
 */
@Component
public class ItemMapper {

    /**
     * Convert Item entity to ItemResponse DTO.
     * Maps all fields including relationships (categories).
     *
     * @param item the item entity (must not be null)
     * @return the item response DTO
     */
    public ItemResponse toItemResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getSellerId(),
                item.getTitle(),
                item.getDescription(),
                item.getStartingPrice(),
                item.getCurrentPrice(),
                item.getImageUrl(),
                item.getStatus(),
                item.getStartTime(),
                item.getEndTime(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                toCategoryResponses(item.getCategories())
        );
    }

    /**
     * Convert Category entity to CategoryResponse DTO.
     *
     * @param category the category entity (must not be null)
     * @return the category response DTO
     */
    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName()
        );
    }

    /**
     * Convert a set of Category entities to CategoryResponse DTOs.
     *
     * @param categories set of category entities
     * @return set of category response DTOs (preserves Set semantics)
     */
    public Set<CategoryResponse> toCategoryResponses(Set<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return Set.of();
        }
        return categories.stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toSet());
    }

    /**
     * Convert CreateItemRequest DTO to Item entity.
     * Sets initial values: status = PENDING, currentPrice = startingPrice.
     * Does NOT save to database - just creates the entity.
     *
     * @param request    the creation request DTO
     * @param categories the resolved category entities (already validated)
     * @param sellerId   the seller ID from JWT
     * @return the new item entity (not persisted, ID is null)
     */
    public Item toEntity(CreateItemRequest request, Set<Category> categories, UUID sellerId) {
        Item item = new Item();
        item.setSellerId(sellerId);
        item.setTitle(request.title());
        item.setDescription(request.description());
        item.setStartingPrice(request.startingPrice());
        item.setCurrentPrice(request.startingPrice()); // Initial current price = starting price
        item.setImageUrl(request.imageUrl());
        item.setStatus(ItemStatus.PENDING); // All new items start as PENDING
        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());

        if (categories != null && !categories.isEmpty()) {
            item.setCategories(categories);
        }

        return item;
    }
}
