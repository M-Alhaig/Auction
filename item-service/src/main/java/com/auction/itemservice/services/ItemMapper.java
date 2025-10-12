package com.auction.itemservice.services;

import com.auction.itemservice.dto.CategoryResponse;
import com.auction.itemservice.dto.CreateItemRequest;
import com.auction.itemservice.dto.ItemResponse;
import com.auction.itemservice.models.Category;
import com.auction.itemservice.models.Item;
import com.auction.itemservice.models.ItemStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper component for entity ↔ DTO conversions. Contains pure transformation logic with no
 * business rules. Update logic is handled in the service layer.
 */
@Component
public class ItemMapper {

  /**
   * Convert Item entity to ItemResponse DTO. Maps all fields including relationships (categories).
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
   * Create a response object representing the given category.
   *
   * @param category the category entity; must not be null
   * @return the response containing the category's id and name
   */
  public CategoryResponse toCategoryResponse(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getName()
    );
  }

  /**
   * Converts a set of Category entities to a set of CategoryResponse DTOs.
   *
   * @param categories the categories to convert; may be null or empty
   * @return a set of CategoryResponse DTOs, or an empty set if {@code categories} is null or empty
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
   * Create a new Item entity from a CreateItemRequest, seller ID, and optional categories.
   *
   * The returned Item is initialized with status set to ItemStatus.PENDING and currentPrice
   * set equal to the provided startingPrice. Categories are assigned only if a non-empty set
   * is provided; the entity is not persisted (ID remains null).
   *
   * @param request    the creation request DTO containing item fields
   * @param categories resolved Category entities to assign to the item; may be null or empty
   * @param sellerId   the UUID of the seller (typically extracted from JWT)
   * @return           a new Item entity populated from the request (not persisted, ID is null)
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