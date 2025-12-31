package com.huawei.basestation.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.huawei.basestation.test.TestApplication;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = {
        TestApplication.class })
@DisplayName("MonitoringServiceClient Fallback Methods Tests")
class MonitoringServiceClientFallbackTest {

    private MonitoringServiceClient client;
    private RestClient.Builder restClientBuilder;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("monitoring.service.url", () -> "http://localhost:8082");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    }

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        client = new MonitoringServiceClient(restClientBuilder, "http://localhost:8082");
    }

    @Test
    @DisplayName("getMetricsFallback should return unavailable status when cache is empty")
    void getMetricsFallback_WithEmptyCache_ReturnsUnavailable() throws ExecutionException, InterruptedException {
        // Test the fallback with empty cache (default state)
        Throwable exception = new RuntimeException("Service unavailable");
        
        CompletableFuture<Map<String, Object>> result = client.getMetricsFallback(1L, exception);
        Map<String, Object> resultMap = result.get();
        
        assertThat(resultMap)
                .containsEntry("status", "unavailable")
                .containsEntry("stationId", 1L);
    }

    @Test
    @DisplayName("getMetricsTimeoutFallback should return timeout status")
    void getMetricsTimeoutFallback_ReturnsTimeoutStatus() throws ExecutionException, InterruptedException {
        Throwable timeoutException = new RuntimeException("Request timeout");
        
        CompletableFuture<Map<String, Object>> result = client.getMetricsTimeoutFallback(1L, timeoutException);
        Map<String, Object> resultMap = result.get();
        
        assertThat(resultMap)
                .containsEntry("status", "timeout")
                .containsEntry("stationId", 1L)
                .containsEntry("message", "Service response too slow - using fallback response");
    }

    @Test
    @DisplayName("getMetricsSyncFallback should return unavailable status")
    void getMetricsSyncFallback_ReturnsUnavailableStatus() {
        Throwable exception = new RuntimeException("Circuit breaker open");
        
        Map<String, Object> result = client.getMetricsSyncFallback(1L, exception);
        
        assertThat(result)
                .containsEntry("status", "unavailable")
                .containsEntry("stationId", 1L);
    }

    @Test
    @DisplayName("healthCheckFallback should return false")
    void healthCheckFallback_ReturnsFalse() {
        Throwable exception = new RuntimeException("Service down");
        
        boolean result = client.healthCheckFallback(exception);
        
        assertThat(result).isFalse();
    }
}
