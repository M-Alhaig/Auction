package com.auction.biddingservice.client;

import com.auction.biddingservice.dto.ItemResponse;
import com.auction.biddingservice.exceptions.ItemNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import reactor.util.retry.Retry;

/**
 * Client for synchronous HTTP calls to Item Service.
 *
 * <p>This client acts as a <strong>fallback mechanism</strong> when Redis cache misses occur.
 * It's used in two scenarios:
 * <ol>
 *   <li><strong>First bid validation</strong> - When starting price not in cache (AuctionStartedEvent missed or Redis restarted)</li>
 *   <li><strong>Auction status verification</strong> - When auction ended status not in cache</li>
 * </ol>
 *
 * <p><strong>Cache-First Architecture:</strong>
 * <pre>
 * 99% of requests → Redis cache hit → ~1ms response
 * 1% of requests  → Cache miss → API fallback → ~50ms response → Re-cache result
 * </pre>
 *
 * <p><strong>Resilience Patterns:</strong>
 * <ul>
 *   <li>Timeout: 2 seconds (fast-fail to prevent blocking bid processing)</li>
 *   <li>Retry: 2 attempts with exponential backoff (100ms → 200ms for transient failures)</li>
 *   <li>Error handling: 404 → ItemNotFoundException, 5xx → Retry, Timeout → Retry</li>
 * </ul>
 *
 * <p><strong>Why WebClient?</strong> Non-blocking reactive client that's Spring's recommended
 * replacement for RestTemplate. Even though we use {@code .block()} for synchronous calls,
 * WebClient provides better connection pooling and timeout handling.
 */
@Slf4j
@Component
public class ItemServiceClient {

  private final WebClient webClient;
  private final Duration timeout;
  private final int maxRetries;
  private final int minBackoff;

  /**
   * Creates ItemServiceClient with WebClient configured for Item Service endpoint.
   *
   * @param baseUrl    the Item Service base URL (e.g., http://localhost:8082)
   * @param timeout    the request timeout in milliseconds (e.g., 2000ms)
   * @param maxRetries the maximum number of retry attempts (e.g., 2)
   */
  public ItemServiceClient(@Value("${item-service.base-url}") String baseUrl,
      @Value("${item-service.timeout}") Duration timeout,
      @Value("${item-service.max-retries}") int maxRetries,
      @Value("${item-service.min-backoff}") int minBackoff) {

    this.timeout = timeout;
    this.maxRetries = maxRetries;
    this.minBackoff = minBackoff;
    this.webClient = WebClient.builder().baseUrl(baseUrl).build();

    log.info("ItemServiceClient initialized - baseUrl: {}, timeout: {}, maxRetries: {}", baseUrl,
        timeout, maxRetries);
  }

  /**
   * Fetches item details from Item Service by ID.
   *
   * <p><strong>HTTP Method:</strong> GET /api/items/{id}
   *
   * <p><strong>Error Handling:</strong>
   * <ul>
   *   <li>404 NOT_FOUND → Throws ItemNotFoundException (item doesn't exist)</li>
   *   <li>5xx SERVER_ERROR → Retries up to maxRetries times</li>
   *   <li>Timeout → Retries up to maxRetries times</li>
   *   <li>Other errors → Propagates as RuntimeException</li>
   * </ul>
   *
   * <p><strong>Performance:</strong> Typical response time ~20-50ms when Item Service is healthy.
   * On timeout/retry: up to 2s + (retries * backoff).
   *
   * @param itemId the auction item ID
   * @return ItemResponse with full item details (status, startingPrice, endTime, etc.)
   * @throws ItemNotFoundException if item doesn't exist (404)
   * @throws RuntimeException      if Item Service is unavailable after retries
   */
  public ItemResponse getItem(Long itemId) {
    log.debug("Fetching item {} from Item Service (cache miss fallback)", itemId);

    try {
      ItemResponse itemResponse = webClient.get()
          .uri("/api/items/{itemId}", itemId)
          .retrieve()
          .onStatus(HttpStatusCode::is4xxClientError, response -> {
            if (response.statusCode().value() == 404) {
              log.warn("Item {} not found in Item Service (404)", itemId);
              return reactor.core.publisher.Mono.error(new ItemNotFoundException(itemId));
            }
            return reactor.core.publisher.Mono.error(
                new RuntimeException("Client error: " + response.statusCode()));
          })
          .onStatus(HttpStatusCode::is5xxServerError, response -> {
            log.warn("Item Service returned 5xx error: {}", response.statusCode());
            return reactor.core.publisher.Mono.error(
                new RuntimeException("Item Service unavailable: " + response.statusCode()));
          })
          .bodyToMono(ItemResponse.class)
          .timeout(timeout)
          .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(minBackoff))
              .filter(throwable -> !(throwable instanceof ItemNotFoundException))
              .doBeforeRetry(retrySignal -> log.warn(
                  "Retrying Item Service call for item {} (attempt {}/{})", itemId,
                  retrySignal.totalRetries() + 1, maxRetries)))
          .block();

      log.info("Successfully fetched item {} from Item Service - status: {}, startingPrice: {}",
          itemId, itemResponse.status(), itemResponse.startingPrice());

      return itemResponse;

    } catch (Exception e) {
      log.error("Failed to fetch item {} from Item Service after {} retries: {}", itemId,
          maxRetries, e.getMessage());
      throw e;
    }
  }
}
