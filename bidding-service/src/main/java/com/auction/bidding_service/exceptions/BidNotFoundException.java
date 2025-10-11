package com.auction.bidding_service.exceptions;

/**
 * Exception thrown when a specific bid cannot be found.
 *
 * Used in queries where a bid ID is provided but doesn't exist.
 *
 * HTTP Status: 404 NOT FOUND
 */
public class BidNotFoundException extends RuntimeException {
    public BidNotFoundException(Long bidId) {
        super("Bid with ID " + bidId + " not found");
    }

    public BidNotFoundException(String message) {
        super(message);
    }
}
