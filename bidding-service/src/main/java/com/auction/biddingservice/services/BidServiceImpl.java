package com.auction.biddingservice.services;

import com.auction.biddingservice.dto.BidResponse;
import com.auction.biddingservice.dto.PlaceBidRequest;
import com.auction.biddingservice.events.BidPlacedEvent;
import com.auction.biddingservice.events.EventPublisher;
import com.auction.biddingservice.events.UserOutbidEvent;
import com.auction.biddingservice.exceptions.BidLockException;
import com.auction.biddingservice.exceptions.BidNotFoundException;
import com.auction.biddingservice.exceptions.InvalidBidException;
import com.auction.biddingservice.models.Bid;
import com.auction.biddingservice.repositories.BidRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of BidService with Redis distributed locking for concurrency control.
 *
 * <p>Redis Lock Strategy:
 * <ul>
 *   <li>Lock key pattern: "lock:item:{itemId}"</li>
 *   <li>Lock timeout: 5 seconds (prevents deadlock if service crashes)</li>
 *   <li>Lock value: Random UUID token (for safe deletion)</li>
 *   <li>Atomic operation: Redis SETNX (SET if Not eXists)</li>
 * </ul>
 *
 * <p>Why Redis over Database Locks:
 * <ul>
 *   <li>Sub-millisecond latency vs. database row locks</li>
 *   <li>Works across multiple service instances (distributed)</li>
 *   <li>Automatic expiration prevents deadlocks</li>
 *   <li>No impact on a database connection pool</li>
 * </ul>
 *
 * <p>Fallback: {@link Bid} entity has @Version for optimistic locking if Redis is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BidServiceImpl implements BidService {

  private final BidRepository bidRepository;
  private final BidMapper bidMapper;
  private final EventPublisher eventPublisher;
  private final RedisTemplate<String, String> redisTemplate;

  private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
  private static final String LOCK_KEY_PREFIX = "lock:item:";

  @Override
  public BidResponse placeBid(PlaceBidRequest request, UUID bidderId) {
    Long itemId = request.itemId();
    BigDecimal bidAmount = request.bidAmount();
    log.debug("placeBid - itemId: {}, bidderId: {}, bidAmount: {}", itemId, bidderId, bidAmount);

    return executeWithLock(itemId, () -> {
      Optional<Bid> highestBidOpt = bidRepository.findFirstByItemIdOrderByBidAmountDesc(
          itemId);

      // Perform validation against the previous bid or starting price.
      if (highestBidOpt.isPresent()) {
        Bid highestBid = highestBidOpt.get();
        if (bidAmount.compareTo(highestBid.getBidAmount()) <= 0) {
          throw new InvalidBidException(
              "Bid amount must be greater than the current highest bid of "
                  + highestBid.getBidAmount());
        }
      } else {
        // TODO: Validate against the item's starting price by calling Item Service.
        // This is required for the first bid on an item.
      }

      // All validation passed, so we can create and save the new bid.
      Bid newBid = new Bid();
      newBid.setItemId(itemId);
      newBid.setBidderId(bidderId);
      newBid.setBidAmount(bidAmount);
      newBid = bidRepository.save(newBid);

      log.info("placeBid - success for itemId: {}, bidderId: {}, bidAmount: {}", itemId, bidderId,
          bidAmount);

      // Publish events
      publishBidPlacedEvent(newBid);
      if (highestBidOpt.isPresent()) {
        Bid highestBid = highestBidOpt.get();
        if (!highestBid.getBidderId().equals(bidderId)) {
          publishUserOutbidEvent(highestBid, newBid);
        }
      }

      return bidMapper.toBidResponseAsHighest(newBid);
    });
  }

  @Override
  @Transactional(readOnly = true)
  public Page<BidResponse> getBidHistory(Long itemId, Pageable pageable) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: findByItemId(itemId, pageable) → Page<Bid>
    // 3. Query highest bid once: findFirstByItemIdOrderByBidAmountDesc(itemId) → Optional<Bid>
    // 4. Extract highestBidId from Optional (or null if no bids)
    // 5. Map Page<Bid> → Page<BidResponse>:
    //    - For each bid: check if bid.getId().equals(highestBidId)
    //    - Call bidMapper.toBidResponse(bid, isCurrentHighest)
    // 6. Return mapped page
    log.debug("getBidHistory - itemId: {}", itemId);
    Page<Bid> bids = bidRepository.findByItemId(itemId, pageable);
    Long highestBidId = bidRepository.findFirstByItemIdOrderByBidAmountDesc(itemId)
        .orElseThrow(() -> new BidNotFoundException("No bids found for item " + itemId)).getId();

    return bids.map(bid -> bidMapper.toBidResponse(bid, bid.getId().equals(highestBidId)));
  }

  @Override
  @Transactional(readOnly = true)
  public BidResponse getHighestBid(Long itemId) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: findFirstByItemIdOrderByBidAmountDesc(itemId) → Optional<Bid>
    // 3. If present: return bidMapper.toBidResponseAsHighest(bid)
    // 4. Else: return null
    log.debug("getHighestBid - itemId: {}", itemId);
    Bid bid = bidRepository.findFirstByItemIdOrderByBidAmountDesc(itemId)
        .orElseThrow(() -> new BidNotFoundException("No bids found for item " + itemId));
    return bidMapper.toBidResponseAsHighest(bid);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<BidResponse> getUserBids(UUID bidderId, Pageable pageable) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: findByBidderId(bidderId, pageable) → Page<Bid>
    // 3. Map Page<Bid> → Page<BidResponse>:
    //    - For each bid: call bidMapper.toBidResponseAsHistorical(bid)
    //    - Set isCurrentHighest=false for ALL (performance over accuracy)
    // 4. Return mapped page
    //
    // Note: This is a simple history view, not "current standing" dashboard
    log.debug("getUserBids - bidderId: {}", bidderId);
    Page<Bid> bids = bidRepository.findByBidderId(bidderId, pageable);
    return bids.map(bidMapper::toBidResponseAsHistorical);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<BidResponse> getUserBidsForItem(Long itemId, UUID bidderId, Pageable pageable) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: findByItemIdAndBidderId(itemId, bidderId, pageable) → Page<Bid>
    // 3. Query highest bid once: findFirstByItemIdOrderByBidAmountDesc(itemId) → Optional<Bid>
    // 4. Extract highestBidId from Optional (or null if no bids)
    // 5. Map Page<Bid> → Page<BidResponse>:
    //    - For each bid: check if bid.getId().equals(highestBidId)
    //    - Call bidMapper.toBidResponse(bid, isCurrentHighest)
    // 6. Return mapped page
    log.debug("getUserBidsForItem - itemId: {}, bidderId: {}", itemId, bidderId);
    Page<Bid> bids = bidRepository.findByItemIdAndBidderId(itemId, bidderId, pageable);
    return bids.map(bidMapper::toBidResponseAsHistorical);
  }

  @Override
  @Transactional(readOnly = true)
  public long countBids(Long itemId) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: countByItemId(itemId) → long
    // 3. Return count
    log.debug("countBids - itemId: {}", itemId);
    long count = bidRepository.countByItemId(itemId);
    log.debug("countBids - count: {}", count);
    return count;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> getItemsUserHasBidOn(UUID bidderId) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: findDistinctItemIdsByBidderId(bidderId) → List<Long>
    // 3. Return list
    log.debug("getItemsUserHasBidOn - bidderId: {}", bidderId);
    List<Long> itemIds = bidRepository.findDistinctItemIdsByBidderId(bidderId);
    log.debug("getItemsUserHasBidOn - itemIds: {}", itemIds);
    return itemIds;
  }

  // ==================== REDIS LOCK HELPER ====================

  /**
   * Executes a bidding operation within a Redis distributed lock.
   *
   * <p>Lock Behavior:
   * <ul>
   *   <li>Acquires lock with key "lock:item:{itemId}" and random UUID token</li>
   *   <li>Lock expires after 5 seconds (prevents deadlock if service crashes)</li>
   *   <li>If lock cannot be acquired → throws BidLockException</li>
   *   <li>Finally block ensures safe lock release (checks token matches)</li>
   * </ul>
   *
   * @param itemId    the item ID to lock
   * @param operation the operation to execute while holding the lock
   * @param <T>       the return type of the operation
   * @return the result of the operation
   * @throws BidLockException if lock cannot be acquired
   */
  private <T> T executeWithLock(Long itemId, Supplier<T> operation) {
    String lockKey = LOCK_KEY_PREFIX + itemId;
    String lockToken = UUID.randomUUID().toString();

    // Atomically try to acquire the lock and check the immediate result.
    Boolean lockAcquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockToken, LOCK_TIMEOUT);

    // If the lock is not acquired (meaning the key already existed), throw an exception.
    if (!Boolean.TRUE.equals(lockAcquired)) {
      log.warn("executeWithLock - failed to acquire lock for itemId: {}", itemId);
      throw new BidLockException("Another bid is being processed. Please retry shortly.");
    }

    try {
      log.debug("executeWithLock - lock acquired for itemId: {}", itemId);
      return operation.get();
    } finally {
      // Release the lock using a non-atomic 'check-then-delete'.
      // This avoids Lua but has a small potential race condition.
      String currentToken = redisTemplate.opsForValue().get(lockKey);
      if (lockToken.equals(currentToken)) {
        redisTemplate.delete(lockKey);
        log.debug("executeWithLock - lock released for itemId: {}", itemId);
      } else {
        log.warn(
            "executeWithLock - lock expired or was re-acquired by another process. Did not release. itemId: {}",
            itemId);
      }
    }
  }

  // ==================== EVENT PUBLISHING HELPERS ====================

  /**
   * Publishes BidPlacedEvent to message queue.
   *
   * <p>Consumed by:
   * <ul>
   *   <li>Item Service: Updates currentPrice for the auction</li>
   *   <li>Notification Service: Pushes real-time bid update to WebSocket clients</li>
   * </ul>
   */
  private void publishBidPlacedEvent(Bid bid) {
    //
    // Steps:
    // 1. Create event: BidPlacedEvent.create(bid.getItemId(), bid.getBidderId(), bid.getBidAmount(), bid.getTimestamp())
    // 2. Publish: eventPublisher.publish(event)
    // 3. Log (debug level) with bidId, itemId, and eventId
    BidPlacedEvent event = BidPlacedEvent.create(bid.getItemId(), bid.getBidderId(),
        bid.getBidAmount(), bid.getTimestamp());

    eventPublisher.publish(event);
  }

  /**
   * Publishes UserOutbidEvent to message queue when a user is no longer the highest bidder.
   *
   * <p>Consumed by:
   * <ul>
   *   <li>Notification Service: Sends WebSocket notification and/or email to outbid user</li>
   * </ul>
   */
  private void publishUserOutbidEvent(Bid previousHighestBid, Bid newBid) {
    //
    // Steps:
    // 1. Create event: UserOutbidEvent.create(newBid.getItemId(), previousHighestBid.getBidderId(), newBid.getBidderId(), newBid.getBidAmount())
    // 2. Publish: eventPublisher.publish(event)
    // 3. Log (debug level) with itemId, outbid userId, new bidderId, and eventId
    UserOutbidEvent event = UserOutbidEvent.create(newBid.getItemId(),
        previousHighestBid.getBidderId(), newBid.getBidderId(), newBid.getBidAmount());
    eventPublisher.publish(event);
    log.debug(
        "publishUserOutbidEvent - itemId: {}, outbid userId: {}, new bidderId: {}, eventId: {}",
        event.data().itemId(), event.data().oldBidderId(), event.data().newBidderId(),
        event.eventId());
  }
}
