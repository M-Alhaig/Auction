package com.auction.itemservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for distributed locking.
 *
 * <p>Used for synchronizing currentPrice updates when consuming BidPlacedEvent from multiple
 * Item Service instances. Prevents race conditions where two instances try to update the same
 * item simultaneously.
 *
 * <p>Lock Strategy:
 * <ul>
 *   <li>Lock key pattern: {@code lock:item:price:{itemId}}</li>
 *   <li>Token-based safe release (store UUID, check before delete)</li>
 *   <li>5-second TTL to prevent deadlocks if service crashes</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

  /**
   * Configure RedisTemplate for distributed locking operations.
   *
   * <p>Uses String serializers for both keys and values since we store:
   * <ul>
   *   <li>Keys: {@code lock:item:price:{itemId}}</li>
   *   <li>Values: UUID tokens for safe lock release</li>
   * </ul>
   *
   * @param connectionFactory the Redis connection factory (auto-configured by Spring Boot)
   * @return configured RedisTemplate for locking operations
   */
  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Use String serializers for lock keys and token values
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(stringSerializer);

    template.afterPropertiesSet();
    return template;
  }
}
