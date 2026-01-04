package com.auction.biddingservice;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Performance comparison test for different caching strategies.
 *
 * <p>This test compares three write approaches:
 * <ol>
 *   <li>Database only (current bid storage approach)</li>
 *   <li>Redis only (write-behind caching proposal)</li>
 *   <li>Both database and Redis (write-through caching)</li>
 * </ol>
 *
 * <p>Purpose: Measure actual performance difference to determine if caching optimizations
 * are worth the added complexity at current scale.
 */
@Slf4j
@SpringBootTest
class BidPerformanceTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	/**
	 * Compare write performance: Database vs Redis vs Both.
	 *
	 * <p>Measures time for 100 sequential writes using each approach and logs results.
	 *
	 * <p>Expected results:
	 * <ul>
	 *   <li>Redis only: Fastest (sub-millisecond per write)</li>
	 *   <li>Database only: Moderate (milliseconds per write)</li>
	 *   <li>Both: Slowest (sum of both operations)</li>
	 * </ul>
	 */
	@Test
	void compareWritePerformance() {
		// Note: Using bids table (owned by Bidding Service), not items table (owned by Item Service)
		// Each microservice can only access its own database!

		final int WARMUP_ITERATIONS = 50;
		final int TEST_ITERATIONS = 10000;
		final int ROUNDS = 3; // Run multiple rounds and average

		log.info("=== Warmup Phase (JVM/Connection Pool Initialization) ===");

		// Warmup: Prime connections and JIT compilation
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			jdbcTemplate.update(
				"INSERT INTO bids (item_id, bidder_id, bid_amount, timestamp) VALUES (?, ?, ?, NOW())",
				999L, java.util.UUID.randomUUID(), 100.00
			);
			redisTemplate.opsForValue().set("warmup:key:" + i, "value");
		}
		jdbcTemplate.update("DELETE FROM bids WHERE item_id = 999");
		log.info("Warmup complete - connections and JVM ready");

		// Track results across multiple rounds
		long[] dbTimes = new long[ROUNDS];
		long[] redisTimes = new long[ROUNDS];
		long[] redisPipelinedTimes = new long[ROUNDS];
		long[] bothTimes = new long[ROUNDS];

		for (int round = 0; round < ROUNDS; round++) {
			log.info("Running benchmark round {}/{}", round + 1, ROUNDS);

			// 1. Database-only writes
			long dbStart = System.nanoTime();
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				jdbcTemplate.update(
					"INSERT INTO bids (item_id, bidder_id, bid_amount, timestamp) VALUES (?, ?, ?, NOW())",
					1L + round, // Different itemId per round
					java.util.UUID.randomUUID(),
					365.00 + i
				);
			}
			dbTimes[round] = System.nanoTime() - dbStart;

			// 2. Redis-only writes (sequential - one network call per operation)
			long redisStart = System.nanoTime();
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				redisTemplate.opsForValue().set(
					"test:item:" + (1L + round) + ":price:" + i,
					String.valueOf(365.00 + i)
				);
			}
			redisTimes[round] = System.nanoTime() - redisStart;

			// 2b. Redis with pipelining (batched - single network call for all operations)
			long redisPipelinedStart = System.nanoTime();
			final int currentRound = round;
			redisTemplate.executePipelined(
				(org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
					for (int i = 0; i < TEST_ITERATIONS; i++) {
						connection.stringCommands().set(
							("test:item:" + (1L + currentRound) + ":pipelined:price:" + i).getBytes(),
							String.valueOf(365.00 + i).getBytes()
						);
					}
					return null;
				}
			);
			redisPipelinedTimes[round] = System.nanoTime() - redisPipelinedStart;

			// 3. Write-through (both DB + Redis)
			long bothStart = System.nanoTime();
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				jdbcTemplate.update(
					"INSERT INTO bids (item_id, bidder_id, bid_amount, timestamp) VALUES (?, ?, ?, NOW())",
					10L + round, // Different itemId range
					java.util.UUID.randomUUID(),
					365.00 + i
				);
				redisTemplate.opsForValue().set(
					"test:item:" + (10L + round) + ":price:" + i,
					String.valueOf(365.00 + i)
				);
			}
			bothTimes[round] = System.nanoTime() - bothStart;
		}

		// Calculate averages
		double avgDbMs = average(dbTimes) / 1_000_000.0;
		double avgRedisMs = average(redisTimes) / 1_000_000.0;
		double avgRedisPipelinedMs = average(redisPipelinedTimes) / 1_000_000.0;
		double avgBothMs = average(bothTimes) / 1_000_000.0;

		log.info("");
		log.info("=== Performance Results (averaged over {} rounds, {} writes each) ===", ROUNDS, TEST_ITERATIONS);
		log.info("Database only:             {} ms total, {} ms per write",
			String.format("%.2f", avgDbMs), String.format("%.4f", avgDbMs / TEST_ITERATIONS));
		log.info("Redis (sequential):        {} ms total, {} ms per write",
			String.format("%.2f", avgRedisMs), String.format("%.4f", avgRedisMs / TEST_ITERATIONS));
		log.info("Redis (pipelined):         {} ms total, {} ms per write",
			String.format("%.2f", avgRedisPipelinedMs), String.format("%.4f", avgRedisPipelinedMs / TEST_ITERATIONS));
		log.info("Both (write-through):      {} ms total, {} ms per write",
			String.format("%.2f", avgBothMs), String.format("%.4f", avgBothMs / TEST_ITERATIONS));
		log.info("");

		log.info("=== Analysis ===");
		log.info("Sequential Redis vs Database: {}x {}",
			String.format("%.1f", avgDbMs / avgRedisMs),
			avgRedisMs < avgDbMs ? "faster" : "slower");
		log.info("Pipelined Redis vs Database:  {}x faster",
			String.format("%.1f", avgDbMs / avgRedisPipelinedMs));
		log.info("Pipelined vs Sequential Redis: {}x speedup from batching",
			String.format("%.1f", avgRedisMs / avgRedisPipelinedMs));
		log.info("");

		if (avgRedisPipelinedMs < avgRedisMs * 0.1) {
			log.info("✓ Pipelining gives >10x speedup - this is Redis's TRUE performance!");
		}

		log.info("Write-through overhead: {} ms ({} extra vs DB-only)",
			String.format("%.2f", avgBothMs - avgDbMs),
			String.format("%.1f%%", ((avgBothMs - avgDbMs) / avgDbMs) * 100));

		// Cleanup: Delete all test data
		for (int round = 0; round < ROUNDS; round++) {
			jdbcTemplate.update("DELETE FROM bids WHERE item_id = ?", 1L + round);
			jdbcTemplate.update("DELETE FROM bids WHERE item_id = ?", 10L + round);
		}
	}

	private double average(long[] values) {
		long sum = 0;
		for (long value : values) {
			sum += value;
		}
		return (double) sum / values.length;
	}

	/**
	 * Compare read performance: Database SELECT vs Redis GET vs Pipelined MGET.
	 *
	 * <p>This test reveals why caching matters - reads are far more frequent than writes
	 * in most systems (page views, bid validations, listings).
	 *
	 * <p>Expected results:
	 * <ul>
	 *   <li>Database read: ~1-5ms (B-tree index scan + network)</li>
	 *   <li>Redis GET: ~0.1-0.5ms (hash table lookup)</li>
	 *   <li>Redis pipelined MGET: ~0.01ms per key (batched)</li>
	 * </ul>
	 */
	@Test
	void compareReadPerformance() {
		final int WARMUP_ITERATIONS = 50;
		final int TEST_ITERATIONS = 1000;
		final int ROUNDS = 3;
		final int PIPELINE_BATCH_SIZE = 100;
		final int UNIQUE_ITEMS = 100; // Read 100 different items, 10 times each

		log.info("=== Setting up test data ===");

		// Setup: Insert 100 different items into database (more realistic workload)
		for (int i = 100; i < 100 + UNIQUE_ITEMS; i++) {
			jdbcTemplate.update(
				"INSERT INTO bids (item_id, bidder_id, bid_amount, timestamp) VALUES (?, ?, ?, NOW())",
				(long) i, java.util.UUID.randomUUID(), 365.50 + (i - 100)
			);
		}

		// Setup: Cache all 100 item prices in Redis
		for (int i = 100; i < 100 + UNIQUE_ITEMS; i++) {
			redisTemplate.opsForValue().set("item:" + i + ":price", String.valueOf(365.50 + (i - 100)));
		}

		log.info("=== Warmup Phase (JVM/Connection Pool Initialization) ===");

		// Warmup: Prime connections and JIT compilation (cycle through different items)
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			Long itemId = 100L + (i % UNIQUE_ITEMS);
			jdbcTemplate.queryForObject(
				"SELECT MAX(bid_amount) FROM bids WHERE item_id = ?",
				java.math.BigDecimal.class,
				itemId
			);
			redisTemplate.opsForValue().get("item:" + itemId + ":price");
		}
		log.info("Warmup complete - connections and JVM ready");

		// Track results across multiple rounds
		long[] dbTimes = new long[ROUNDS];
		long[] redisTimes = new long[ROUNDS];
		long[] redisPipelinedTimes = new long[ROUNDS];

		for (int round = 0; round < ROUNDS; round++) {
			log.info("Running benchmark round {}/{}", round + 1, ROUNDS);

			// 1. Database reads: SELECT MAX from different items (10 reads per item × 100 items)
			long dbStart = System.nanoTime();
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				Long itemId = 100L + (i % UNIQUE_ITEMS); // Cycle through 100 different items
				jdbcTemplate.queryForObject(
					"SELECT MAX(bid_amount) FROM bids WHERE item_id = ?",
					java.math.BigDecimal.class,
					itemId
				);
			}
			dbTimes[round] = System.nanoTime() - dbStart;

			// 2. Redis sequential reads: GET different item prices
			long redisStart = System.nanoTime();
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				Long itemId = 100L + (i % UNIQUE_ITEMS); // Cycle through 100 different items
				redisTemplate.opsForValue().get("item:" + itemId + ":price");
			}
			redisTimes[round] = System.nanoTime() - redisStart;

			// 3. Redis pipelined reads: MGET batch of 100 keys, repeat 10 times = 1000 total reads
			long redisPipelinedStart = System.nanoTime();
			for (int batch = 0; batch < TEST_ITERATIONS / PIPELINE_BATCH_SIZE; batch++) {
				redisTemplate.executePipelined(
					(org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
						for (int i = 0; i < PIPELINE_BATCH_SIZE; i++) {
							connection.stringCommands().get(
								("item:" + (100 + i) + ":price").getBytes()
							);
						}
						return null;
					}
				);
			}
			redisPipelinedTimes[round] = System.nanoTime() - redisPipelinedStart;
		}

		// Calculate averages
		double avgDbMs = average(dbTimes) / 1_000_000.0;
		double avgRedisMs = average(redisTimes) / 1_000_000.0;
		double avgRedisPipelinedMs = average(redisPipelinedTimes) / 1_000_000.0;

		log.info("");
		log.info("=== Read Performance Results (averaged over {} rounds, {} reads each) ===", ROUNDS, TEST_ITERATIONS);
		log.info("Database (SELECT MAX):     {} ms total, {} ms per read",
			String.format("%.2f", avgDbMs), String.format("%.4f", avgDbMs / TEST_ITERATIONS));
		log.info("Redis (sequential GET):    {} ms total, {} ms per read",
			String.format("%.2f", avgRedisMs), String.format("%.4f", avgRedisMs / TEST_ITERATIONS));
		log.info("Redis (pipelined MGET):    {} ms total, {} ms per read",
			String.format("%.2f", avgRedisPipelinedMs), String.format("%.4f", avgRedisPipelinedMs / TEST_ITERATIONS));
		log.info("");

		log.info("=== Read Analysis ===");
		log.info("Sequential Redis vs Database: {}x faster",
			String.format("%.1f", avgDbMs / avgRedisMs));
		log.info("Pipelined Redis vs Database:  {}x faster",
			String.format("%.1f", avgDbMs / avgRedisPipelinedMs));
		log.info("Pipelined vs Sequential Redis: {}x speedup from batching",
			String.format("%.1f", avgRedisMs / avgRedisPipelinedMs));
		log.info("");

		// Calculate total time savings at different read/write ratios
		double dbWriteMs = 1.982;  // From write test results
		double redisWriteMs = 3.847; // From write test (both DB + Redis)
		double dbReadMs = avgDbMs / TEST_ITERATIONS;
		double redisReadMs = avgRedisMs / TEST_ITERATIONS;

		log.info("=== Real-World Scenario Analysis ===");
		for (int ratio : new int[]{10, 50, 100, 500}) {
			double dbOnlyTotal = (ratio * dbReadMs) + dbWriteMs;
			double cachedTotal = (ratio * redisReadMs) + redisWriteMs;
			double savings = ((dbOnlyTotal - cachedTotal) / dbOnlyTotal) * 100;

			log.info("Read/Write ratio {}:1 → Caching saves {}% total time ({} vs {} ms)",
				ratio,
				String.format("%.1f", savings),
				String.format("%.2f", cachedTotal),
				String.format("%.2f", dbOnlyTotal)
			);
		}

		// Cleanup: Delete test data
		jdbcTemplate.update("DELETE FROM bids WHERE item_id >= 100 AND item_id < 200");
		for (int i = 100; i < 100 + UNIQUE_ITEMS; i++) {
			redisTemplate.delete("item:" + i + ":price");
		}
		log.info("Test data cleaned up");
	}
}
