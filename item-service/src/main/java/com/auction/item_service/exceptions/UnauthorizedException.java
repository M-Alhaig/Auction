package com.auction.item_service.exceptions;

/**
 * Exception thrown when a user attempts an operation they're not authorized to perform.
 * Typically used when a user tries to modify an item they don't own.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
