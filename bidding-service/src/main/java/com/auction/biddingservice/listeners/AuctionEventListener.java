package com.auction.biddingservice.listeners;

import com.auction.biddingservice.events.AuctionEndedEvent;
import com.auction.biddingservice.services.AuctionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionEventListener {

	private final AuctionCacheService auctionCacheService;

	private static final String QUEUE_NAME = "AuctionEndedQueue";

	@RabbitListener(queues = "#{auctionEndedQueue.name}")
	public void onAuctionEnded(AuctionEndedEvent event) {
		auctionCacheService.markAuctionEnded(event.data().itemId(), event.data().endTime());
	}
}
