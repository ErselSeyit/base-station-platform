package com.huawei.monitoring.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-station rate limiter to prevent individual stations from flooding the system.
 *
 * Uses a sliding window approach with automatic cleanup.
 */
@Component
public class StationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(StationRateLimiter.class);

    private final ConcurrentHashMap<Long, AtomicInteger> stationCounters = new ConcurrentHashMap<>();

    @Value("${monitoring.rate-limit.per-station:100}")
    private int requestsPerMinute;

    /**
     * Checks if a request from the given station should be allowed.
     *
     * @param stationId the station ID
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest(Long stationId) {
        if (stationId == null) {
            return true; // Allow if no station ID
        }

        AtomicInteger counter = stationCounters.computeIfAbsent(stationId, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        if (currentCount > requestsPerMinute) {
            log.warn("Rate limit exceeded for station {}: {} requests in current window (limit: {})",
                    stationId, currentCount, requestsPerMinute);
            return false;
        }

        if (currentCount == requestsPerMinute) {
            log.info("Station {} approaching rate limit: {}/{} requests",
                    stationId, currentCount, requestsPerMinute);
        }

        return true;
    }

    /**
     * Gets the current request count for a station.
     *
     * @param stationId the station ID
     * @return current count
     */
    public int getCurrentCount(Long stationId) {
        if (stationId == null) {
            return 0;
        }
        AtomicInteger counter = stationCounters.get(stationId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Resets all rate limit counters.
     * Scheduled to run every minute to implement a sliding window.
     */
    @Scheduled(fixedRate = 60000) // Reset every minute
    public void resetCounters() {
        int stationsTracked = stationCounters.size();
        stationCounters.clear();
        if (stationsTracked > 0) {
            log.debug("Reset rate limit counters for {} stations", stationsTracked);
        }
    }

    /**
     * Gets the configured rate limit.
     *
     * @return requests per minute limit
     */
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
}
