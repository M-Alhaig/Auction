package com.auction.item_service.services;

import com.auction.item_service.dto.CreateItemRequest;
import com.auction.item_service.dto.ItemResponse;
import com.auction.item_service.dto.UpdateItemRequest;
import com.auction.item_service.exceptions.ItemNotFoundException;
import com.auction.item_service.exceptions.UnauthorizedException;
import com.auction.item_service.models.Category;
import com.auction.item_service.models.Item;
import com.auction.item_service.models.ItemStatus;
import com.auction.item_service.repositories.CategoryRepository;
import com.auction.item_service.repositories.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Implementation of ItemService for managing auction items.
 * Handles user-facing CRUD operations and queries.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final ItemMapper itemMapper;

    // ==================== CRUD OPERATIONS ====================

    @Override
    public ItemResponse createItem(CreateItemRequest request) {
        // TODO: Implement createItem
        // 1. Validate and fetch categories from categoryRepository
        // 2. Convert request to entity using itemMapper.toEntity()
        // 3. Save entity to itemRepository
        // 4. Convert saved entity to ItemResponse using itemMapper.toItemResponse()
        // 5. Return ItemResponse
        Set<Category> categories = validateAndFetchCategories(request.categoryIds());
        Item item = itemMapper.toEntity(request, categories);
        item = itemRepository.save(item);
        return itemMapper.toItemResponse(item);
    }

    @Override
    public ItemResponse updateItem(Long itemId, UpdateItemRequest request, UUID authenticatedUserId) {
        // TODO: Implement updateItem
        // 1. Find item by ID (throw ItemNotFoundException if not found)
        // 2. Validate ownership (throw UnauthorizedException if sellerId != authenticatedUserId)
        // 3. Validate item status is PENDING (throw IllegalStateException if not)
        // 4. Apply partial updates from request (only non-null fields)
        // 5. If categoryIds provided, validate and fetch categories
        // 6. Save updated entity
        // 7. Convert to ItemResponse and return
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException(itemId));
        validateOwnership(item, authenticatedUserId);
        validateItemIsPending(item);
        updateItemFields(request, item);
        item = itemRepository.save(item);

        return itemMapper.toItemResponse(item);
    }


    @Override
    public void deleteItem(Long itemId, UUID authenticatedUserId) {
        // TODO: Implement deleteItem
        // 1. Find item by ID (throw ItemNotFoundException if not found)
        // 2. Validate ownership (throw UnauthorizedException if sellerId != authenticatedUserId)
        // 3. Validate item status is PENDING (throw IllegalStateException if not)
        // 4. Delete from itemRepository
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException(itemId));
        validateOwnership(item, authenticatedUserId);
        validateItemIsPending(item);
        itemRepository.delete(item);
    }

    // ==================== QUERY OPERATIONS ====================

    @Override
    @Transactional(readOnly = true)
    public ItemResponse getItemById(Long itemId) {
        // 1. Find item by ID (throw ItemNotFoundException if not found)
        // 2. Convert to ItemResponse using itemMapper
        // 3. Return ItemResponse
        return itemRepository.findById(itemId)
                .map(itemMapper::toItemResponse)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> getAllItems(Pageable pageable) {
        // 1. Call itemRepository.findAll(pageable)
        // 2. Map Page<Item> to Page<ItemResponse> using itemMapper
        // 3. Return mapped page
        return itemRepository.findAll(pageable).map(itemMapper::toItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> getItemsByStatus(ItemStatus status, Pageable pageable) {
        // 1. Call itemRepository.findByStatus(status, pageable)
        // 2. Map to Page<ItemResponse>
        // 3. Return mapped page
        return itemRepository.findByStatus(status, pageable).map(itemMapper::toItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> getItemsBySeller(UUID sellerId, Pageable pageable) {
        // 1. Call itemRepository.findBySellerId(sellerId, pageable)
        // 2. Map to Page<ItemResponse>
        // 3. Return mapped page
        return itemRepository.findBySellerId(sellerId, pageable).map(itemMapper::toItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> getItemsBySellerAndStatus(UUID sellerId, ItemStatus status, Pageable pageable) {
        // 1. Call itemRepository.findBySellerIdAndStatus(sellerId, status, pageable)
        // 2. Map to Page<ItemResponse>
        // 3. Return mapped page
        return itemRepository.findBySellerIdAndStatus(sellerId, status, pageable).map(itemMapper::toItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> getActiveAuctionsEndingSoon(Pageable pageable) {
        // TODO: Implement getActiveAuctionsEndingSoon
        // 1. Call itemRepository.findByStatusOrderByEndTimeAsc(ItemStatus.ACTIVE, pageable)
        // 2. Map to Page<ItemResponse>
        // 3. Return mapped page
        return itemRepository.findByStatusOrderByEndTimeAsc(ItemStatus.ACTIVE, pageable).map(itemMapper::toItemResponse);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    // TODO: Add private validation methods
    // - validateOwnership(Item item, UUID userId)
    private void validateOwnership(Item item, UUID userId) {
        if (!item.getSellerId().equals(userId)) {
            throw new UnauthorizedException("User does not own this item");
        }
    }

    // - validateItemIsPending(Item item)
    private void validateItemIsPending(Item item) {
        if (item.getStatus() != ItemStatus.PENDING) {
            throw new IllegalStateException("Item is not in PENDING status");
        }
    }

    // - validateAndFetchCategories(Set<Integer> categoryIds)
    private Set<Category> validateAndFetchCategories(Set<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Set.of();
        }

        Set<Category> categories = categoryRepository.findByIdIn(categoryIds);

        // Validate all requested IDs were found
        if (categories.size() != categoryIds.size()) {
            throw new IllegalArgumentException(
                    "One or more category IDs are invalid. Requested: " + categoryIds.size() +
                            ", Found: " + categories.size()
            );
        }

        return categories;
    }

    private void updateItemFields(UpdateItemRequest request, Item item) {
        if (request.title() != null) {
            item.setTitle(request.title());
        }
        if (request.description() != null) {
            item.setDescription(request.description());
        }
        if (request.startingPrice() != null) {
            item.setStartingPrice(request.startingPrice());
            item.setCurrentPrice(request.startingPrice());
        }
        if (request.categoryIds() != null) {
            Set<Category> categories = validateAndFetchCategories(request.categoryIds());
            item.setCategories(categories);
        }
        if (request.imageUrl() != null) {
            item.setImageUrl(request.imageUrl());
        }
        if (request.startTime() != null) {
            item.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            item.setEndTime(request.endTime());
        }
    }
}
