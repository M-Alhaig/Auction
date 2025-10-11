package com.auction.item_service.scheduler;

import com.auction.item_service.services.ItemLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for auction lifecycle management.
 * <p>
 * Uses ShedLock to ensure only one instance executes each job at a time, preventing race conditions
 * in multi-instance deployments.
 * <p>
 * Schedule Strategy: Uses fixedDelay instead of fixedRate to prevent task queue buildup.
 * - fixedDelay: Waits for previous execution to complete, then waits 60 seconds before next run
 * - This prevents resource exhaustion if batch processing takes longer than the interval
 * <p>
 * Schedule intervals: - Auction start: 60 seconds after completion - Auction end: 60 seconds after
 * completion
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

  private final ItemLifecycleService itemLifecycleService;

  /**
   * Scheduled job to start pending auctions whose start time has arrived.
   * <p>
   * Runs 60 seconds after previous execution completes. Lock ensures only one instance
   * processes this batch.
   * <p>
   * Lock configuration: - lockAtMostFor: 50 seconds (prevents deadlock if instance crashes) -
   * lockAtLeastFor: 10 seconds (prevents too-frequent execution)
   */
  @Scheduled(fixedDelay = 60000) // 60 seconds after previous execution completes
  @SchedulerLock(
      name = "startPendingAuctions",
      lockAtMostFor = "50s",
      lockAtLeastFor = "10s"
  )
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
   * Scheduled job to end active auctions whose end time has passed.
   * <p>
   * Runs 60 seconds after previous execution completes. Lock ensures only one instance
   * processes this batch.
   * <p>
   * Lock configuration: - lockAtMostFor: 50 seconds (prevents deadlock if instance crashes) -
   * lockAtLeastFor: 10 seconds (prevents too-frequent execution)
   */
  @Scheduled(fixedDelay = 60000) // 60 seconds after previous execution completes
  @SchedulerLock(
      name = "endExpiredAuctions",
      lockAtMostFor = "50s",
      lockAtLeastFor = "10s"
  )
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
