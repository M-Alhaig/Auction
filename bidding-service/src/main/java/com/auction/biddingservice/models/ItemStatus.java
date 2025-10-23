package com.auction.biddingservice.models;

/**
 * Status of an auction item in its lifecycle.
 *
 * <p>This enum mirrors the ItemStatus from Item Service for API response deserialization.
 * <ul>
 *   <li>PENDING - Auction created but not yet started (before startTime)</li>
 *   <li>ACTIVE - Auction is live and accepting bids (between startTime and endTime)</li>
 *   <li>ENDED - Auction has concluded, no longer accepting bids (after endTime)</li>
 * </ul>
 */
public enum ItemStatus {
	PENDING,
	ACTIVE,
	ENDED
}
