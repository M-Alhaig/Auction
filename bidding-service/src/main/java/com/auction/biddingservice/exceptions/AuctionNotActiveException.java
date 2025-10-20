package com.auction.biddingservice.exceptions;

/**
 * Exception thrown when a bid is attempted on an auction that is not active (PENDING status).
 *
 * <p>This exception is thrown by {@link com.auction.biddingservice.services.BidServiceImpl}
 * when the auction status check determines that the auction for the given item has not started yet
 * and is still in PENDING status.
 *
 * <p><strong>HTTP Status Mapping:</strong> This exception is mapped to HTTP 400 BAD_REQUEST
 * by {@link com.auction.biddingservice.exceptions.GlobalExceptionHandler}, indicating that
 * the bid request is invalid because the auction hasn't started yet (client error).
 *
 * <p><strong>Architecture:</strong>
 * <ul>
 *   <li>Item Service publishes AuctionStartedEvent when auction transitions to ACTIVE</li>
 *   <li>Bidding Service consumes event and caches active auction metadata in Redis</li>
 *   <li>Before processing bids, status validation ensures auction is ACTIVE</li>
 *   <li>PENDING auctions (not in active cache) are rejected with this exception</li>
 *   <li>This check happens BEFORE acquiring distributed locks (performance optimization)</li>
 * </ul>
 *
 * <p><strong>Difference from AuctionEndedException:</strong>
 * <ul>
 *   <li>AuctionNotActiveException (400 BAD_REQUEST) - Auction hasn't started, user can retry later</li>
 *   <li>AuctionEndedException (409 CONFLICT) - Auction finished, state conflict, cannot retry</li>
 * </ul>
 */
public class AuctionNotActiveException extends RuntimeException {

	/**
	 * Creates an AuctionNotActiveException with a standardized message including the item ID.
	 *
	 * <p>The exception message will be in the format:
	 * "Auction {itemId} is not active yet. Bidding has not started."
	 *
	 * @param itemId the ID of the auction item that is not active
	 */
	public AuctionNotActiveException(Long itemId) {
		super("Auction " + itemId + " is not active yet. Bidding has not started.");
	}

	/**
	 * Creates an AuctionNotActiveException with a custom message.
	 *
	 * <p>This constructor allows for more flexible error messaging when the standard
	 * format is not suitable or when additional context is needed.
	 *
	 * @param message the custom error message
	 */
	public AuctionNotActiveException(String message) {
		super(message);
	}
}
