package com.auction.biddingservice.exceptions;

import java.time.Instant;

public class AuctionEndedException extends RuntimeException {
	public AuctionEndedException(Long itemId, Instant endTime) {
		super("Auction " + itemId + " ended at " + endTime + ". Bidding is closed.");
	}

	public AuctionEndedException(String message) {
		super(message);
	}
}
