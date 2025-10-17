package com.auction.biddingservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event consumed when an auction transitions from PENDING to ACTIVE status in Item Service.
 *
 * <p>This event mirrors the AuctionStartedEvent published by Item Service for deserialization.
 *
 * <p><strong>Purpose in Bidding Service:</strong>
 * <ul>
 *   <li>Cache auction metadata (startingPrice + endTime) in Redis for first bid validation</li>
 *   <li>Enable fast bid validation without calling Item Service API (cache-first architecture)</li>
 *   <li>Calculate dynamic TTL based on auction duration (cache expires when auction ends)</li>
 * </ul>
 *
 * <p><strong>Event Envelope Pattern:</strong>
 * <ul>
 *   <li>eventId: UUID for idempotency (deduplication via Redis lock)</li>
 *   <li>eventType: "AuctionStartedEvent" for routing/filtering</li>
 *   <li>timestamp: When Item Service published the event</li>
 *   <li>data: The auction details payload</li>
 * </ul>
 *
 * <p><strong>Routing Key:</strong> "item.auction-started"
 *
 * <p><strong>Consumer:</strong> {@link com.auction.biddingservice.listeners.AuctionStartedEventListener}
 */
public record AuctionStartedEvent(
	String eventId,
	String eventType,
	Instant timestamp,
	AuctionStartedData data
) {

	/**
	 * Payload data for AuctionStartedEvent.
	 *
	 * <p><strong>NOTE:</strong> The endTime field is required for calculating dynamic cache TTL.
	 * If Item Service's AuctionStartedEvent doesn't include endTime, it must be added to enable
	 * proper cache expiration (cache should expire exactly when auction ends, not arbitrarily).
	 */
	public record AuctionStartedData(
		Long itemId,
		UUID sellerId,
		String title,
		Instant startTime,
		Instant endTime,         // Required for dynamic TTL calculation
		BigDecimal startingPrice
	) {
	}
}
