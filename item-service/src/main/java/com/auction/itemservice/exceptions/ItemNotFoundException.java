package com.auction.itemservice.exceptions;

/**
 * Exception thrown when an item with the specified ID cannot be found.
 */
public class ItemNotFoundException extends RuntimeException {

  /**
   * Constructs an ItemNotFoundException with the specified detail message.
   *
   * @param message detail message describing the missing item
   */
  public ItemNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates an ItemNotFoundException with a standardized message containing the provided item ID.
   *
   * @param itemId the ID of the item that was not found
   */
  public ItemNotFoundException(Long itemId) {
    super("Item not found with ID: " + itemId);
  }
}