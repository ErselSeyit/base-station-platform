package com.huawei.basestation.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * Resilience configuration including circuit breaker and bulkhead patterns.
 *
 * Bulkheads isolate thread pools to prevent cascade failures:
 * - diagnostic: For AI diagnostic service calls (isolated pool)
 * - external: For external API calls (separate isolated pool)
 * - default: For general inter-service calls
 */
@Configuration
public class CircuitBreakerConfiguration {

	@Bean
	public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
		return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
				.timeLimiterConfig(TimeLimiterConfig.custom()
						.timeoutDuration(Duration.ofSeconds(4))
						.build())
				.circuitBreakerConfig(CircuitBreakerConfig.custom()
						.slidingWindowSize(10)
						.failureRateThreshold(50)
						.waitDurationInOpenState(Duration.ofSeconds(10))
						.permittedNumberOfCallsInHalfOpenState(3)
						.build())
				.build());
	}

	/**
	 * Semaphore-based bulkhead registry for limiting concurrent calls.
	 * Provides isolation between different service call types.
	 */
	@Bean
	public BulkheadRegistry bulkheadRegistry() {
		// Diagnostic service calls - larger pool for AI processing
		BulkheadConfig diagnosticConfig = BulkheadConfig.custom()
				.maxConcurrentCalls(10)
				.maxWaitDuration(Duration.ofMillis(500))
				.build();

		// External API calls - smaller pool, shorter wait
		BulkheadConfig externalConfig = BulkheadConfig.custom()
				.maxConcurrentCalls(5)
				.maxWaitDuration(Duration.ofMillis(100))
				.build();

		// Default for inter-service calls
		BulkheadConfig defaultConfig = BulkheadConfig.custom()
				.maxConcurrentCalls(20)
				.maxWaitDuration(Duration.ofMillis(200))
				.build();

		return BulkheadRegistry.of(Map.of(
				"default", defaultConfig,
				"diagnostic", diagnosticConfig,
				"external", externalConfig
		));
	}

	/**
	 * Thread pool bulkhead for complete isolation of async operations.
	 * Each pool has dedicated threads that cannot affect other pools.
	 */
	@Bean
	public ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry() {
		// Diagnostic service - dedicated thread pool
		ThreadPoolBulkheadConfig diagnosticPoolConfig = ThreadPoolBulkheadConfig.custom()
				.maxThreadPoolSize(10)
				.coreThreadPoolSize(5)
				.queueCapacity(100)
				.keepAliveDuration(Duration.ofSeconds(60))
				.build();

		// External calls - smaller dedicated pool
		ThreadPoolBulkheadConfig externalPoolConfig = ThreadPoolBulkheadConfig.custom()
				.maxThreadPoolSize(5)
				.coreThreadPoolSize(2)
				.queueCapacity(50)
				.keepAliveDuration(Duration.ofSeconds(30))
				.build();

		// Default pool for general async operations
		ThreadPoolBulkheadConfig defaultPoolConfig = ThreadPoolBulkheadConfig.custom()
				.maxThreadPoolSize(15)
				.coreThreadPoolSize(8)
				.queueCapacity(150)
				.keepAliveDuration(Duration.ofSeconds(60))
				.build();

		return ThreadPoolBulkheadRegistry.of(Map.of(
				"default", defaultPoolConfig,
				"diagnostic", diagnosticPoolConfig,
				"external", externalPoolConfig
		));
	}
}
