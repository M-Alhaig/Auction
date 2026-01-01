package com.auction.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Notification Service.
 *
 * <p><strong>Primary Use Case:</strong> Event Idempotency Tracking
 * <ul>
 *   <li>Prevents duplicate notification creation from redelivered events</li>
 *   <li>Uses three-state machine pattern: processing → completed/failed</li>
 *   <li>Key pattern: "event:state:{eventId}"</li>
 *   <li>TTL: 1 hour (events are unlikely to be redelivered after this)</li>
 * </ul>
 *
 * <p><strong>Why Redis for Idempotency:</strong>
 * <ul>
 *   <li>Atomic operations (Lua scripts) ensure race condition safety</li>
 *   <li>TTL automatically cleans up old state entries</li>
 *   <li>Sub-millisecond latency for high-throughput event processing</li>
 *   <li>Consistent with bidding-service and item-service patterns</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

  /**
   * Create a RedisTemplate for String key-value operations.
   *
   * <p>Used for event idempotency tracking with keys like "event:state:{eventId}"
   * and values like "processing", "completed", "failed".
   *
   * @param connectionFactory Redis connection factory from Spring Boot auto-configuration
   * @return configured RedisTemplate with String serializers
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
