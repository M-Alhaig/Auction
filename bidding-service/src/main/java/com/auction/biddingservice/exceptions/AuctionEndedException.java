package com.auction.biddingservice.exceptions;

import java.time.Instant;

/**
 * Exception thrown when a bid is attempted on an auction that has already ended.
 *
 * <p>This exception is thrown by {@link com.auction.biddingservice.services.BidServiceImpl}
 * when the auction-ended cache check (via Redis) determines that the auction for the given
 * item has already ended and is no longer accepting bids.
 *
 * <p><strong>HTTP Status Mapping:</strong> This exception is mapped to HTTP 409 CONFLICT
 * by {@link com.auction.biddingservice.exceptions.GlobalExceptionHandler}, indicating that
 * the bid request conflicts with the current state of the resource (auction is closed).
 *
 * <p><strong>Architecture:</strong>
 * <ul>
 *   <li>Item Service publishes AuctionEndedEvent when scheduler changes status to ENDED</li>
 *   <li>Bidding Service consumes event and caches ended auction IDs in Redis (7-day TTL)</li>
 *   <li>Before processing bids, fast Redis lookup prevents bids on ended auctions</li>
 *   <li>This check happens BEFORE acquiring distributed locks (performance optimization)</li>
 * </ul>
 */
public class AuctionEndedException extends RuntimeException {

	/**
	 * Creates an AuctionEndedException with a standardized message including the item ID and end time.
	 *
	 * <p>This constructor is used when the auction end time is known from the Redis cache.
	 * The exception message will be in the format:
	 * "Auction {itemId} ended at {endTime}. Bidding is closed."
	 *
	 * @param itemId  the ID of the auction item that has ended
	 * @param endTime the instant when the auction ended (from cache), or null if unavailable
	 */
	public AuctionEndedException(Long itemId, Instant endTime) {
		super("Auction " + itemId + " ended at " + endTime + ". Bidding is closed.");
	}

	/**
	 * Creates an AuctionEndedException with a custom message.
	 *
	 * <p>This constructor allows for more flexible error messaging when the standard
	 * format is not suitable or when additional context is needed.
	 *
	 * @param message the custom error message
	 */
	public AuctionEndedException(String message) {
		super(message);
	}
}
