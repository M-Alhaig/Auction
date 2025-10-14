package com.auction.itemservice.scheduler;

import com.auction.itemservice.services.ItemLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for auction lifecycle management.
 *
 * <p>Uses ShedLock to ensure only one instance executes each job at a time, preventing race conditions
 * in multi-instance deployments.
 *
 * <p>Schedule Strategy: Uses fixedDelay instead of fixedRate to prevent task queue buildup. -
 * fixedDelay: Waits for a previous execution to complete, then waits 60 seconds before the next run -
 * This prevents resource exhaustion if batch processing takes longer than the interval
 *
 * <p>Schedule intervals: - Auction start: 60 seconds after completion - Auction end: 60 seconds after
 * completion
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

	private final ItemLifecycleService itemLifecycleService;

	/**
	 * Starts pending auctions whose configured start time has been reached.
	 *
	 * <p>Scheduled with a 60-second fixed delay and protected by a distributed lock named
	 * "startPendingAuctions" (lockAtMostFor = "50s", lockAtLeastFor = "10s") to ensure only one
	 * application instance processes the batch and to bound lock duration.
	 *
	 * <p>Default lock duration is set to 50 seconds to accommodate the longest expected task
	 * duration with a safety margin for potential delays.
	 */
	@Scheduled(fixedDelay = 60000) // 60 seconds after the previous execution completes
	@SchedulerLock(name = "startPendingAuctions", lockAtMostFor = "50s", lockAtLeastFor = "10s")
	public void startPendingAuctions() {
		log.debug("Scheduler triggered: startPendingAuctions");

		try {
			int count = itemLifecycleService.batchStartPendingAuctions();

			if (count > 0) {
				log.info("Scheduler completed: startPendingAuctions - {} auctions started", count);
			}
		} catch (Exception e) {
			log.error("Scheduler failed: startPendingAuctions - error: {}", e.getMessage(), e);
		}
	}

	/**
	 * End active auctions whose configured end time has been reached.
	 *
	 * <p>Scheduled with a 60-second fixed delay and protected by a distributed lock named
	 * "endActiveAuctions" (lockAtMostFor = "50s", lockAtLeastFor = "10s") to ensure only one
	 * application instance processes the batch and to bound lock duration.
	 *
	 * <p>Default lock duration is set to 50 seconds to accommodate the longest expected task
	 * duration with a safety margin for potential delays.
	 */
	@Scheduled(fixedDelay = 60000) // 60 seconds after the previous execution completes
	@SchedulerLock(name = "endExpiredAuctions", lockAtMostFor = "50s", lockAtLeastFor = "10s")
	public void endExpiredAuctions() {
		log.debug("Scheduler triggered: endExpiredAuctions");

		try {
			int count = itemLifecycleService.batchEndExpiredAuctions();

			if (count > 0) {
				log.info("Scheduler completed: endExpiredAuctions - {} auctions ended", count);
			}
		} catch (Exception e) {
			log.error("Scheduler failed: endExpiredAuctions - error: {}", e.getMessage(), e);
		}
	}
}
