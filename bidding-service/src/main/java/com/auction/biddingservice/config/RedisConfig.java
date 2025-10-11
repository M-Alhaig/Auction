package com.auction.biddingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Bidding Service.
 * <p>
 * Primary Use Case: Distributed Locking - Prevents race conditions when multiple users bid on the
 * same item simultaneously - Lock pattern: "lock:item:{itemId}" - Lock timeout: 5 seconds
 * (configurable)
 * <p>
 * Why Redis for Locking: - Atomic operations (SETNX) ensure only one instance acquires the lock -
 * TTL prevents deadlocks if a service instance crashes while holding a lock - Sub-millisecond
 * latency (much faster than database-level locks) - Scales horizontally (Redis Cluster or AWS
 * ElastiCache)
 * <p>
 * Alternative Use Cases (future): - Caching frequently accessed bid data - Rate limiting per user -
 * Idempotency tracking (store processed eventIds with TTL)
 * <p>
 * Production Considerations: - Use Redis Sentinel or Cluster for high availability - Monitor lock
 * acquisition failures (metric: bid_lock_failures) - Set appropriate connection pool size based on
 * expected concurrency
 */
@Configuration
public class RedisConfig {

  /**
   * Configure RedisTemplate for simple key-value operations. Uses String serialization for both
   * keys and values.
   * <p>
   * Design Choice: String-only template for distributed locking. - Lock keys: "lock:item:123" -
   * Lock values: UUID tokens for safe deletion
   *
   * @param connectionFactory injected by Spring Boot auto-configuration
   * @return configured RedisTemplate
   */
  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Use String serializers for human-readable keys/values in Redis CLI
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(stringSerializer);

    template.afterPropertiesSet();
    return template;
  }
}
