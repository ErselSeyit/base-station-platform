package com.huawei.basestation.client;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.github.resilience4j.retry.annotation.Retry;

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

    private final RestClient restClient;
    private final String monitoringServiceUrl;

    // Simple cache for fallback
    private volatile Map<String, Object> cachedMetrics = Collections.emptyMap();

    public MonitoringServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${monitoring.service.url:http://localhost:8082}") String monitoringServiceUrl) {
        this.restClient = restClientBuilder
                .baseUrl(monitoringServiceUrl)
                .build();
        this.monitoringServiceUrl = monitoringServiceUrl;
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
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = restClient.get()
                    .uri("/api/v1/metrics/station/{stationId}/latest", stationId)
                    .retrieve()
                    .body(Map.class);
            
            // Cache successful response for fallback
            if (metrics != null) {
                cachedMetrics = metrics;
            }
            
            return metrics != null ? metrics : Collections.emptyMap();
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
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = restClient.get()
                .uri("/api/v1/metrics/station/{stationId}/latest", stationId)
                .retrieve()
                .body(Map.class);
        
        if (metrics != null) {
            cachedMetrics = metrics;
        }
        
        return metrics != null ? metrics : Collections.emptyMap();
    }

    /**
     * Health check for the monitoring service.
     * Uses shorter timeout and no retry since it's just a probe.
     */
    @CircuitBreaker(name = MONITORING_SERVICE, fallbackMethod = "healthCheckFallback")
    public boolean isMonitoringServiceHealthy() {
        try {
            restClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Monitoring service health check failed: {}", e.getMessage());
            throw e;
        }
    }

    // ========================================
    // Fallback Methods
    // ========================================

    /**
     * Fallback when circuit breaker is open or service fails.
     * Returns cached data or empty response.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<Map<String, Object>> getMetricsFallback(Long stationId, Throwable t) {
        log.warn("Circuit breaker fallback for station {}. Reason: {}", stationId, t.getMessage());
        return CompletableFuture.completedFuture(
                cachedMetrics.isEmpty() 
                        ? Map.of("status", "unavailable", "stationId", stationId)
                        : cachedMetrics
        );
    }

    /**
     * Fallback specifically for timeout scenarios.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<Map<String, Object>> getMetricsTimeoutFallback(Long stationId, Throwable t) {
        log.warn("Timeout fallback for station {}. Service too slow.", stationId);
        return CompletableFuture.completedFuture(
                Map.of("status", "timeout", "stationId", stationId, "message", "Service response too slow")
        );
    }

    /**
     * Sync fallback for circuit breaker.
     */
    @SuppressWarnings("unused")
    private Map<String, Object> getMetricsSyncFallback(Long stationId, Throwable t) {
        log.warn("Sync fallback for station {}. Reason: {}", stationId, t.getMessage());
        return Map.of("status", "unavailable", "stationId", stationId);
    }

    /**
     * Health check fallback - service is down.
     */
    @SuppressWarnings("unused")
    private boolean healthCheckFallback(Throwable t) {
        log.warn("Monitoring service is unhealthy: {}", t.getMessage());
        return false;
    }
}

