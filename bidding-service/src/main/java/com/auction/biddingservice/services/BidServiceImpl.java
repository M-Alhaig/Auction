package com.auction.biddingservice.services;

import com.auction.biddingservice.client.ItemServiceClient;
import com.auction.biddingservice.dto.BidResponse;
import com.auction.biddingservice.dto.ItemResponse;
import com.auction.biddingservice.dto.PlaceBidRequest;
import com.auction.biddingservice.events.BidPlacedEvent;
import com.auction.biddingservice.events.EventPublisher;
import com.auction.biddingservice.events.UserOutbidEvent;
import com.auction.biddingservice.exceptions.AuctionEndedException;
import com.auction.biddingservice.exceptions.AuctionNotActiveException;
import com.auction.biddingservice.exceptions.BidLockException;
import com.auction.biddingservice.exceptions.InvalidBidException;
import com.auction.biddingservice.models.Bid;
import com.auction.biddingservice.models.ItemStatus;
import com.auction.biddingservice.repositories.BidRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
  private final AuctionCacheService auctionCacheService;
  private final ItemServiceClient itemServiceClient;

  private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
  private static final String LOCK_KEY_PREFIX = "lock:item:";

  /**
   * Processes a bid for an item: validates against the current highest bid, persists the new bid,
   * and publishes related events.
   *
   *
   * @param request  contains the target `itemId` and the `bidAmount` for the new bid
   * @param bidderId identifier of the user placing the bid
   * @return the newly persisted bid represented as the current highest `BidResponse`
   * @throws InvalidBidException if the bid amount is not greater than the current highest bid
   * @throws BidLockException    if a distributed lock for the item cannot be acquired
   */
  @Override
  public BidResponse placeBid(PlaceBidRequest request, UUID bidderId) {
    Long itemId = request.itemId();
    BigDecimal bidAmount = request.bidAmount();
    log.debug("placeBid - itemId: {}, bidderId: {}, bidAmount: {}", itemId, bidderId, bidAmount);

    // Multi-layered auction status check with cache-first + API fallback
    validateAuctionStatus(itemId);

    return executeWithLock(itemId, () -> {
      Optional<Bid> highestBidOpt = bidRepository.findFirstByItemIdOrderByBidAmountDesc(itemId);

      // Perform validation against the previous bid or starting price.
      if (highestBidOpt.isPresent()) {
        Bid highestBid = highestBidOpt.get();
        if (bidAmount.compareTo(highestBid.getBidAmount()) <= 0) {
          throw new InvalidBidException(
              "Bid amount must be greater than the current highest bid of "
                  + highestBid.getBidAmount());
        }
      } else {
        // First bid - validate against starting price using cache-first approach
        validateFirstBid(itemId, bidAmount);
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

  /**
   * Retrieve paginated bid history for an item, marking which entry is the current highest bid.
   *
   * @param itemId   the item's identifier
   * @param pageable pagination and sorting information
   * @return a page of BidResponse objects for the item; each response has `isCurrentHighest` set to
   * `true` for the current highest bid and `false` otherwise
   */
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
    Long highestBidId = bidRepository.findFirstByItemIdOrderByBidAmountDesc(itemId).map(Bid::getId)
        .orElse(null);

    return bids.map(bid -> bidMapper.toBidResponse(bid, bid.getId().equals(highestBidId)));
  }

  /**
   * Retrieve the current highest bid for the given item.
   *
   * @param itemId the identifier of the item to query
   * @return the highest bid for the item as a `BidResponse`, or `null` if no bids exist
   */
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
    return bidRepository.findFirstByItemIdOrderByBidAmountDesc(itemId)
        .map(bidMapper::toBidResponseAsHighest).orElse(null);
  }

  /**
   * Retrieve a bidder's paginated bid history.
   *
   * <p>Returns historical bid entries for the given bidder; each entry is a historical view and
   * has `isCurrentHighest` set to `false` (this method does not reflect current standing).
   *
   * @param bidderId            the UUID of the bidder whose bids to fetch
   * @param authenticatedUserId the UUID of the authenticated user
   * @param pageable            pagination and sorting information
   * @return a page of `BidResponse` objects representing the bidder's historical bids
   */
  @Override
  @Transactional(readOnly = true)
  public Page<BidResponse> getUserBids(UUID bidderId, UUID authenticatedUserId, Pageable pageable) {
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

    if (!bidderId.equals(authenticatedUserId)) {
      //	  TODO: throw a security exception
    }

    log.debug("getUserBids - bidderId: {}", bidderId);
    Page<Bid> bids = bidRepository.findByBidderId(bidderId, pageable);
    return bids.map(bidMapper::toBidResponseAsHistorical);
  }

  /**
   * Retrieve a bidder's paginated bids for a specific item, marking which bid is currently the
   * highest.
   *
   * @param itemId              the identifier of the item
   * @param bidderId            the identifier of the bidder whose bids to retrieve
   * @param authenticatedUserId the UUID of the authenticated user
   * @param pageable            pagination and sorting information
   * @return a page of BidResponse objects for the given item and bidder; each entry has
   * `isCurrentHighest` set to `true` when that bid is the current highest for the item, `false`
   * otherwise
   */
  @Override
  @Transactional(readOnly = true)
  public Page<BidResponse> getUserBidsForItem(Long itemId, UUID bidderId, UUID authenticatedUserId,
      Pageable pageable) {
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

    if (!bidderId.equals(authenticatedUserId)) {
      //	  TODO: throw a security exception
    }

    log.debug("getUserBidsForItem - itemId: {}, bidderId: {}", itemId, bidderId);
    Page<Bid> bids = bidRepository.findByItemIdAndBidderId(itemId, bidderId, pageable);
    Long highestBidId = bidRepository.findFirstByItemIdOrderByBidAmountDesc(itemId).map(Bid::getId)
        .orElse(null);

    return bids.map(bid -> bidMapper.toBidResponse(bid, bid.getId().equals(highestBidId)));
  }

  /**
   * Count the number of bids placed on the specified item.
   *
   * @return the number of bids for the given item ID
   */
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

  /**
   * Retrieve the distinct item IDs on which the specified bidder has placed bids.
   *
   * @param bidderId the UUID of the bidder
   * @return a list of distinct item IDs the bidder has placed bids on; empty if none
   */
  @Override
  @Transactional(readOnly = true)
  public List<Long> getItemsUserHasBidOn(UUID bidderId, UUID authenticatedUserId) {
    //
    // Steps:
    // 1. Log query (debug level)
    // 2. Query: findDistinctItemIdsByBidderId(bidderId) → List<Long>
    // 3. Return list

    if (!bidderId.equals(authenticatedUserId)) {
      //	  TODO: throw a security exception
    }

    log.debug("getItemsUserHasBidOn - bidderId: {}", bidderId);
    List<Long> itemIds = bidRepository.findDistinctItemIdsByBidderId(bidderId);
    log.debug("getItemsUserHasBidOn - itemIds: {}", itemIds);
    return itemIds;
  }

  // ==================== VALIDATION HELPERS ====================

  /**
   * Validates the first bid on an item using cache-first strategy with API fallback.
   *
   * <p><strong>Cache-First Architecture:</strong>
   * <pre>
   * 1. Fast Path (99% cases): Check Redis cache for starting price (~1ms)
   * 2. Slow Path (1% cases): Call Item Service API if cache miss (~50ms)
   * 3. Cache Warming: Store fetched metadata in Redis for future bids
   * </pre>
   *
   * <p><strong>Validation Rules:</strong>
   * <ul>
   *   <li>Item must exist (404 from API → InvalidBidException)</li>
   *   <li>Item must be ACTIVE (not PENDING or ENDED)</li>
   *   <li>Bid amount must be >= starting price</li>
   * </ul>
   *
   * @param itemId the auction item ID
   * @param bidAmount the bid amount to validate
   * @throws InvalidBidException if validation fails
   */
  private void validateFirstBid(Long itemId, BigDecimal bidAmount) {
    log.debug("validateFirstBid - checking starting price for itemId: {}", itemId);

    // FAST PATH: Try cache first
    BigDecimal startingPrice = auctionCacheService.getStartingPrice(itemId);

    if (startingPrice == null) {
      // CACHE MISS: Fallback to Item Service API
      log.info("Starting price cache miss for itemId: {} - fetching from Item Service", itemId);

      ItemResponse item = itemServiceClient.getItem(itemId);

      // Validate item status
      if (item.status() != ItemStatus.ACTIVE) {
        log.warn("First bid rejected - itemId: {} has status: {}, expected: ACTIVE",
            itemId, item.status());
        throw new InvalidBidException(
            "Cannot bid on this item. Item status: " + item.status());
      }

      startingPrice = item.startingPrice();

      // Cache for future bids (warm the cache)
      auctionCacheService.cacheAuctionMetadata(itemId, startingPrice, item.endTime(), item.status());
      log.info("Cached auction metadata from API fallback - itemId: {}, startingPrice: {}, status: {}",
          itemId, startingPrice, item.status());
    }

    // Validate bid amount
    if (bidAmount.compareTo(startingPrice) < 0) {
      log.warn("First bid rejected - itemId: {}, bidAmount: {} < startingPrice: {}",
          itemId, bidAmount, startingPrice);
      throw new InvalidBidException(
          "Bid amount must be at least the starting price of " + startingPrice);
    }

    log.debug("validateFirstBid - passed for itemId: {}, bidAmount: {} >= startingPrice: {}",
        itemId, bidAmount, startingPrice);
  }

  /**
   * Validates that an auction is ACTIVE using a cache-first strategy with API fallback.
   *
   * <p><strong>Validation Flow:</strong>
   * <pre>
   * Step 1: Check metadata cache for status (~1ms)
   *   - ACTIVE → Allow bid ✅
   *   - ENDED → Throw AuctionEndedException (409 CONFLICT)
   *   - PENDING → Throw AuctionNotActiveException (400 BAD_REQUEST)
   *
   * Step 2: Cache miss → Fallback to Item Service API (~50ms)
   *   - Fetch item details and status
   *   - Cache the metadata with status
   *   - Apply same status validation as above
   * </pre>
   *
   * <p><strong>Security:</strong>
   * This is a fail-closed system. If status cannot be determined (cache miss),
   * the method falls back to the authoritative Item Service API. It NEVER assumes
   * a status, preventing bids on PENDING or ENDED auctions due to stale/missing cache.
   *
   * <p><strong>Status Meanings:</strong>
   * <ul>
   *   <li>ACTIVE - Auction accepting bids (normal flow)</li>
   *   <li>PENDING - Auction not started yet (400 BAD_REQUEST - client can retry later)</li>
   *   <li>ENDED - Auction closed (409 CONFLICT - state conflict, cannot retry)</li>
   * </ul>
   *
   * @param itemId the auction item ID to validate
   * @throws AuctionNotActiveException if the auction is PENDING (400 BAD_REQUEST)
   * @throws AuctionEndedException if the auction is ENDED (409 CONFLICT)
   */
  private void validateAuctionStatus(Long itemId) {
    // Step 1: Check metadata cache for status
    ItemStatus status = auctionCacheService.getStatus(itemId);

    if (status != null) {
      // Cache HIT - validate status
      log.debug("validateAuctionStatus - cache hit - itemId: {}, status: {}", itemId, status);

      if (status == ItemStatus.ACTIVE) {
        return; // ✅ Auction is active, proceed with bid
      } else if (status == ItemStatus.ENDED) {
        Instant endTime = auctionCacheService.getAuctionEndTime(itemId);
        log.warn("Bid rejected - Auction ENDED (cached) - itemId: {}, endTime: {}", itemId, endTime);
        throw new AuctionEndedException(itemId, endTime);
      } else if (status == ItemStatus.PENDING) {
        log.warn("Bid rejected - Auction PENDING (cached) - itemId: {}", itemId);
        throw new AuctionNotActiveException(itemId);
      }
    }

    // Step 2: Cache MISS - fallback to Item Service API
    log.info("Auction status cache miss for itemId: {} - fetching from Item Service", itemId);

    ItemResponse item = itemServiceClient.getItem(itemId);

    // Cache the fetched metadata with status
    auctionCacheService.cacheAuctionMetadata(itemId, item.startingPrice(), item.endTime(), item.status());
    log.info("Cached auction metadata from API - itemId: {}, status: {}", itemId, item.status());

    // Validate status from API response
    if (item.status() == ItemStatus.ACTIVE) {
      log.debug("validateAuctionStatus - passed (API) - itemId: {}, status: ACTIVE", itemId);
      return; // ✅ Auction is active, proceed with bid
    } else if (item.status() == ItemStatus.ENDED) {
      // Also cache in ended-flag cache for faster future lookups
      auctionCacheService.markAuctionEnded(itemId, item.endTime());
      log.warn("Bid rejected - Auction ENDED (API) - itemId: {}, endTime: {}", itemId, item.endTime());
      throw new AuctionEndedException(itemId, item.endTime());
    } else if (item.status() == ItemStatus.PENDING) {
      log.warn("Bid rejected - Auction PENDING (API) - itemId: {}", itemId);
      throw new AuctionNotActiveException(itemId);
    }

    // Unknown status - should never happen, but fail closed
    log.error("Unknown auction status from API - itemId: {}, status: {}", itemId, item.status());
    throw new IllegalStateException("Unknown auction status: " + item.status());
  }

  // ==================== REDIS LOCK HELPER ====================

  /**
   * Execute an operation while holding a Redis-backed distributed lock for the given item.
   *
   * <p>The method acquires a per-item lock (key "lock:item:{itemId}") with a 5-second
   * expiration, runs the provided operation if the lock is obtained, and releases the lock only if
   * the release token matches the one used to acquire it.
   *
   * @param itemId    the ID of the item to lock
   * @param operation the operation to execute while the lock is held
   * @param <T>       the operation's return type
   * @return the result of the operation
   * @throws BidLockException if the lock cannot be acquired
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
   * Publish a BidPlacedEvent for the given bid.
   *
   * <p>Creates and publishes an event that contains the bid's item ID, bidder ID, amount, and
   * timestamp so other services can react (for example: Item Service to update the current price
   * and Notification Service to push real-time updates).
   *
   * @param bid the persisted bid to announce
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
    log.debug("Published BidPlacedEvent - bidId: {}, itemId: {}, eventId: {}", bid.getId(),
        bid.getItemId(), event.eventId());
  }

  /**
   * Publish an event notifying that a user has been outbid on an item.
   * <p>
   * Creates and publishes a UserOutbidEvent containing the item ID, the previous highest bidder's
   * ID, the new bidder's ID, and the new bid amount.
   *
   * @param previousHighestBid the bid that was previously the highest for the item
   * @param newBid             the bid that became the new highest for the item
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
