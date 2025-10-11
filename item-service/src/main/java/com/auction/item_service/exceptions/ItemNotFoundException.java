package com.auction.item_service.exceptions;

/**
 * Exception thrown when an item with the specified ID cannot be found.
 */
public class ItemNotFoundException extends RuntimeException {

  public ItemNotFoundException(String message) {
    super(message);
  }

  public ItemNotFoundException(Long itemId) {
    super("Item not found with ID: " + itemId);
  }
}
