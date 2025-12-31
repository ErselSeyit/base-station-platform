package com.huawei.monitoring.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StationRateLimiter.
 */
class StationRateLimiterTest {

    private StationRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new StationRateLimiter();
        // Set a low limit for easier testing
        ReflectionTestUtils.setField(rateLimiter, "requestsPerMinute", 5);
    }

    @Test
    void allowRequest_WithinLimit_ShouldReturnTrue() {
        // Given
        Long stationId = 1L;

        // When
        boolean allowed = rateLimiter.allowRequest(stationId);

        // Then
        assertTrue(allowed, "First request should be allowed");
        assertEquals(1, rateLimiter.getCurrentCount(stationId));
    }

    @Test
    void allowRequest_ExceedsLimit_ShouldReturnFalse() {
        // Given
        Long stationId = 1L;
        int limit = rateLimiter.getRequestsPerMinute();

        // When - make requests up to the limit
        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimiter.allowRequest(stationId),
                    "Request " + (i + 1) + " should be allowed");
        }

        // Then - next request should be rejected
        assertFalse(rateLimiter.allowRequest(stationId),
                "Request exceeding limit should be rejected");
        assertEquals(limit + 1, rateLimiter.getCurrentCount(stationId));
    }

    @Test
    void allowRequest_DifferentStations_ShouldTrackSeparately() {
        // Given
        Long station1 = 1L;
        Long station2 = 2L;
        int limit = rateLimiter.getRequestsPerMinute();

        // When - exhaust station1's quota
        for (int i = 0; i < limit; i++) {
            rateLimiter.allowRequest(station1);
        }

        // Then - station1 should be rate limited
        assertFalse(rateLimiter.allowRequest(station1),
                "Station 1 should be rate limited");

        // But station2 should still be allowed
        assertTrue(rateLimiter.allowRequest(station2),
                "Station 2 should not be rate limited");

        assertEquals(limit + 1, rateLimiter.getCurrentCount(station1));
        assertEquals(1, rateLimiter.getCurrentCount(station2));
    }

    @Test
    void allowRequest_NullStationId_ShouldAlwaysReturnTrue() {
        // When
        boolean allowed = rateLimiter.allowRequest(null);

        // Then
        assertTrue(allowed, "Null station ID should always be allowed");
        assertEquals(0, rateLimiter.getCurrentCount(null));
    }

    @Test
    void resetCounters_ShouldClearAllCounters() {
        // Given
        Long station1 = 1L;
        Long station2 = 2L;

        rateLimiter.allowRequest(station1);
        rateLimiter.allowRequest(station1);
        rateLimiter.allowRequest(station2);

        assertEquals(2, rateLimiter.getCurrentCount(station1));
        assertEquals(1, rateLimiter.getCurrentCount(station2));

        // When
        rateLimiter.resetCounters();

        // Then
        assertEquals(0, rateLimiter.getCurrentCount(station1));
        assertEquals(0, rateLimiter.getCurrentCount(station2));

        // And new requests should be allowed
        assertTrue(rateLimiter.allowRequest(station1));
        assertTrue(rateLimiter.allowRequest(station2));
    }

    @Test
    void getCurrentCount_NonExistentStation_ShouldReturnZero() {
        // Given
        Long stationId = 999L;

        // When
        int count = rateLimiter.getCurrentCount(stationId);

        // Then
        assertEquals(0, count);
    }

    @Test
    void getRequestsPerMinute_ShouldReturnConfiguredLimit() {
        // When
        int limit = rateLimiter.getRequestsPerMinute();

        // Then
        assertEquals(5, limit);
    }

    @Test
    void allowRequest_ConcurrentRequests_ShouldHandleCorrectly() throws InterruptedException {
        // Given
        Long stationId = 1L;
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // When - simulate concurrent requests from multiple threads
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 2; j++) {
                    rateLimiter.allowRequest(stationId);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - total count should be accurate (10 threads * 2 requests = 20)
        assertEquals(20, rateLimiter.getCurrentCount(stationId),
                "Concurrent requests should be counted accurately");
    }
}
