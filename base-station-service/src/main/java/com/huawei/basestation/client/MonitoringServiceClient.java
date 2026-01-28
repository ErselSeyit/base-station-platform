package com.huawei.basestation.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

/**
 * Client for calling the Monitoring Service with resilience patterns.
 * 
 * Demonstrates:
 * - Circuit Breaker: Fails fast when monitoring service is down
 * - Time Limiter: Prevents hanging when service is slow
 * - Retry: Automatic retry for transient failures
 * - Fallback: Returns cached/default data when service unavailable
 */
@Component
public class MonitoringServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MonitoringServiceClient.class);
    private static final String MONITORING_SERVICE = "monitoringService";
    private static final String KEY_STATUS = "status";
    private static final String KEY_STATION_ID = "stationId";
    private static final String KEY_FALLBACK = "fallback";
    private static final String KEY_MESSAGE = "message";
    private static final String STATUS_UNAVAILABLE = "unavailable";
    private static final String MSG_SERVICE_UNAVAILABLE = "Service unavailable - using fallback response";
    @NonNull
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
            new ParameterizedTypeReference<Map<String, Object>>() {};
    @NonNull
    private static final ParameterizedTypeReference<Map<Long, Map<String, Object>>> BATCH_MAP_TYPE_REF =
            new ParameterizedTypeReference<Map<Long, Map<String, Object>>>() {};

    private final RestClient restClient;
    private final String monitoringServiceUrl;

    // Thread-safe cache for fallback
    private final AtomicReference<Map<String, Object>> cachedMetrics =
            new AtomicReference<>(Collections.emptyMap());

    public MonitoringServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${monitoring.service.url:http://localhost:8082}") @NonNull String monitoringServiceUrl) {
        this.monitoringServiceUrl = Objects.requireNonNull(monitoringServiceUrl,
                "Monitoring service URL must not be null");
        this.restClient = restClientBuilder
                .baseUrl(this.monitoringServiceUrl)
                .build();
    }

    /**
     * Fetches latest metrics for a station with full resilience protection.
     * 
     * Circuit Breaker: Opens after 5 failures, waits 10s before trying again
     * Timeout: Fails if response takes longer than 2 seconds
     * Retry: Retries up to 3 times with exponential backoff
     */
    @CircuitBreaker(name = MONITORING_SERVICE, fallbackMethod = "getMetricsFallback")
    @TimeLimiter(name = MONITORING_SERVICE, fallbackMethod = "getMetricsTimeoutFallback")
    @Retry(name = MONITORING_SERVICE)
    public CompletableFuture<Map<String, Object>> getLatestMetrics(Long stationId) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Fetching metrics for station {} from {}", stationId, monitoringServiceUrl);

            Map<String, Object> metrics = restClient.get()
                    .uri("/api/v1/metrics/station/{stationId}/latest", stationId)
                    .retrieve()
                    .body(MAP_TYPE_REF);

            // Cache successful response for fallback
            if (metrics != null) {
                cachedMetrics.set(metrics);
            }

            // Return empty map instead of null - follows "return empty, not null" pattern
            return Objects.requireNonNullElse(metrics, Collections.emptyMap());
        });
    }

    /**
     * Fetches metrics synchronously with circuit breaker protection only.
     * Useful for batch operations where we don't need async.
     */
    @CircuitBreaker(name = MONITORING_SERVICE, fallbackMethod = "getMetricsSyncFallback")
    @Retry(name = MONITORING_SERVICE)
    public Map<String, Object> getLatestMetricsSync(Long stationId) {
        log.debug("Fetching metrics synchronously for station {}", stationId);

        Map<String, Object> metrics = restClient.get()
                .uri("/api/v1/metrics/station/{stationId}/latest", stationId)
                .retrieve()
                .body(MAP_TYPE_REF);

        if (metrics != null) {
            cachedMetrics.set(metrics);
        }

        // Return empty map instead of null - follows "return empty, not null" pattern
        return Objects.requireNonNullElse(metrics, Collections.emptyMap());
    }

    /**
     * Batch fetch of latest metrics for multiple stations.
     * This method prevents N+1 query problems by fetching all metrics in a single HTTP call.
     * 
     * <p>Performance: Instead of making N HTTP calls (one per station), this makes 1 HTTP call.
     * For 100 stations, this reduces from 100 calls to 1 call (100x improvement).
     * 
     * <p>Example usage:
     * <pre>
     * List&lt;Long&gt; stationIds = List.of(1L, 2L, 3L);
     * Map&lt;Long, Map&lt;String, Object&gt;&gt; metrics = client.getLatestMetricsBatch(stationIds);
     * </pre>
     * 
     * @param stationIds list of station IDs to fetch metrics for (must not be null, may be empty)
     * @return map of stationId -> latest metrics map (never null, may be empty)
     */
    @CircuitBreaker(name = MONITORING_SERVICE, fallbackMethod = "getMetricsBatchFallback")
    @Retry(name = MONITORING_SERVICE)
    public Map<Long, Map<String, Object>> getLatestMetricsBatch(List<Long> stationIds) {
        Objects.requireNonNull(stationIds, "Station IDs list cannot be null");

        if (stationIds.isEmpty()) {
            log.debug("Empty station IDs list, returning empty map");
            return Collections.emptyMap();
        }

        log.debug("Fetching batch metrics for {} stations from {}", stationIds.size(), monitoringServiceUrl);

        // Prepare request body
        Map<String, Object> requestBody = Objects.requireNonNull(Map.of("stationIds", stationIds));

        Map<Long, Map<String, Object>> batchMetrics = restClient.post()
                .uri("/api/v1/metrics/batch/latest")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(requestBody)
                .retrieve()
                .body(BATCH_MAP_TYPE_REF);

        if (batchMetrics != null && !batchMetrics.isEmpty()) {
            // Update cache with first available metrics for fallback
            Map<String, Object> firstMetric = batchMetrics.values().iterator().next();
            if (firstMetric != null) {
                cachedMetrics.set(firstMetric);
            }
            log.debug("Successfully fetched batch metrics for {} stations", batchMetrics.size());
        }

        // Return empty map instead of null - follows "return empty, not null" pattern
        return Objects.requireNonNullElse(batchMetrics, Collections.emptyMap());
    }

    /**
     * Health check for the monitoring service.
     * Uses shorter timeout and no retry since it's just a probe.
     */
    @CircuitBreaker(name = MONITORING_SERVICE, fallbackMethod = "healthCheckFallback")
    public boolean isMonitoringServiceHealthy() {
        restClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    // ========================================
    // Fallback Methods
    // ========================================

    /**
     * Fallback when circuit breaker is open or service fails.
     * Returns cached data or empty response with clear fallback indicator.
     */
    CompletableFuture<Map<String, Object>> getMetricsFallback(Long stationId, Throwable t) {
        log.warn("Circuit breaker fallback for station {}. Reason: {}", stationId, t.getMessage());
        Map<String, Object> cached = cachedMetrics.get();
        if (cached.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of(
                    KEY_STATUS, STATUS_UNAVAILABLE,
                    KEY_STATION_ID, stationId,
                    KEY_FALLBACK, true,
                    KEY_MESSAGE, MSG_SERVICE_UNAVAILABLE
            ));
        }
        // Add fallback indicator to cached metrics
        Map<String, Object> fallbackResponse = new HashMap<>(cached);
        fallbackResponse.put(KEY_FALLBACK, true);
        fallbackResponse.put("source", "cached");
        return CompletableFuture.completedFuture(fallbackResponse);
    }

    /**
     * Fallback specifically for timeout scenarios.
     */
    CompletableFuture<Map<String, Object>> getMetricsTimeoutFallback(Long stationId, Throwable t) {
        log.warn("Timeout fallback for station {}. Reason: {}", stationId, t.getMessage());
        return CompletableFuture.completedFuture(Map.of(
                KEY_STATUS, "timeout",
                KEY_STATION_ID, stationId,
                KEY_FALLBACK, true,
                KEY_MESSAGE, "Service response too slow - using fallback response"
        ));
    }

    /**
     * Sync fallback for circuit breaker.
     */
    Map<String, Object> getMetricsSyncFallback(Long stationId, Throwable t) {
        log.warn("Sync fallback for station {}. Reason: {}", stationId, t.getMessage());
        return Map.of(
                KEY_STATUS, STATUS_UNAVAILABLE,
                KEY_STATION_ID, stationId,
                KEY_FALLBACK, true,
                KEY_MESSAGE, MSG_SERVICE_UNAVAILABLE
        );
    }

    /**
     * Fallback for batch metrics when circuit breaker is open or service fails.
     * Returns empty map or cached data.
     */
    Map<Long, Map<String, Object>> getMetricsBatchFallback(List<Long> stationIds, Throwable t) {
        log.warn("Batch metrics fallback for {} stations. Reason: {}", stationIds.size(), t.getMessage());

        // Return fallback response for all requested stations with clear indicators
        Map<Long, Map<String, Object>> fallbackMap = new HashMap<>();
        for (Long stationId : stationIds) {
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put(KEY_STATUS, STATUS_UNAVAILABLE);
            fallbackResponse.put(KEY_STATION_ID, stationId);
            fallbackResponse.put(KEY_FALLBACK, true);

            Map<String, Object> cached = cachedMetrics.get();
            if (!cached.isEmpty()) {
                fallbackResponse.put(KEY_MESSAGE, "Service unavailable - using cached data");
                fallbackResponse.put("source", "cached");
            } else {
                fallbackResponse.put(KEY_MESSAGE, MSG_SERVICE_UNAVAILABLE);
            }

            fallbackMap.put(stationId, fallbackResponse);
        }
        return fallbackMap;
    }

    /**
     * Health check fallback - service is down.
     */
    boolean healthCheckFallback(Throwable t) {
        log.warn("Monitoring service is unhealthy: {}", t.getMessage());
        return false;
    }
}
