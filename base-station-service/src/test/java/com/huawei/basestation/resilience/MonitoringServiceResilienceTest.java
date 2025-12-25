package com.huawei.basestation.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.huawei.basestation.client.MonitoringServiceClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Chaos/Resilience tests for the MonitoringServiceClient.
 * 
 * These tests verify the system's behavior under failure conditions:
 * - Service is down (connection refused)
 * - Service is slow (timeout)
 * - Service returns errors (5xx)
 * - Circuit breaker opens and closes correctly
 * 
 * Uses WireMock to simulate various failure scenarios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Monitoring Service Resilience Tests")
class MonitoringServiceResilienceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MonitoringServiceClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Start WireMock before Spring context
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        
        registry.add("monitoring.service.url", wireMockServer::baseUrl);
        // Disable Eureka and Redis for tests
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.data.redis.host", () -> "localhost");
        // Use H2 for tests
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        // Reset circuit breaker to closed state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("monitoringService");
        cb.reset();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Nested
    @DisplayName("Normal Operation")
    class NormalOperation {

        @Test
        @DisplayName("Should return metrics when service responds normally")
        void successfulResponse() throws Exception {
            // Given: Monitoring service returns valid response
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cpu\": 45.5, \"memory\": 62.3}")));

            // When
            CompletableFuture<Map<String, Object>> future = client.getLatestMetrics(1L);
            Map<String, Object> result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(result).containsKeys("cpu", "memory");
            assertThat(result.get("cpu")).isEqualTo(45.5);
        }
    }

    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutHandling {

        @Test
        @DisplayName("Should trigger fallback when service is too slow")
        void slowService_TriggersFallback() throws Exception {
            // Given: Monitoring service responds after 5 seconds (timeout is 2s)
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cpu\": 45.5}")
                            .withFixedDelay(5000)));  // 5 second delay

            // When
            CompletableFuture<Map<String, Object>> future = client.getLatestMetrics(1L);
            Map<String, Object> result = future.get(10, TimeUnit.SECONDS);

            // Then: Should get timeout fallback response
            assertThat(result).containsEntry("status", "timeout");
            assertThat(result).containsEntry("stationId", 1L);
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Behavior")
    class CircuitBreakerBehavior {

        @Test
        @DisplayName("Should open circuit after repeated failures")
        void repeatedFailures_OpensCircuit() {
            // Given: Monitoring service returns 500 errors
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("monitoringService");
            
            // Initially circuit should be closed
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // When: Make multiple failing requests (more than minimumNumberOfCalls)
            IntStream.range(0, 10).forEach(i -> {
                try {
                    client.getLatestMetricsSync(1L);
                } catch (Exception ignored) {
                    // Expected to fail
                }
            });

            // Then: Circuit should be open
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> 
                            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN));
        }

        @Test
        @DisplayName("Should return fallback when circuit is open")
        void openCircuit_ReturnsFallback() throws Exception {
            // Given: Force circuit breaker to open state
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("monitoringService");
            circuitBreaker.transitionToOpenState();

            // When: Try to get metrics
            Map<String, Object> result = client.getLatestMetricsSync(1L);

            // Then: Should get fallback response immediately (no actual call made)
            assertThat(result).containsEntry("status", "unavailable");
            assertThat(result).containsEntry("stationId", 1L);
            
            // Verify no requests were made to WireMock
            wireMockServer.verify(0, getRequestedFor(urlPathMatching("/api/v1/metrics/.*")));
        }

        @Test
        @DisplayName("Should recover when service becomes healthy")
        void serviceRecovery_ClosesCircuit() throws Exception {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("monitoringService");
            
            // Given: Circuit is open
            circuitBreaker.transitionToOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Transition to half-open
            circuitBreaker.transitionToHalfOpenState();
            
            // Given: Service is now healthy
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cpu\": 45.5, \"memory\": 62.3}")));

            // When: Make successful requests
            IntStream.range(0, 3).forEach(i -> {
                try {
                    client.getLatestMetricsSync(1L);
                } catch (Exception ignored) {}
            });

            // Then: Circuit should close
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> 
                            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED));
        }
    }

    @Nested
    @DisplayName("Service Unavailable")
    class ServiceUnavailable {

        @Test
        @DisplayName("Should use fallback when service is completely down")
        void serviceDown_UsesFallback() throws Exception {
            // Given: Stop WireMock to simulate service down
            wireMockServer.stop();

            // When
            Map<String, Object> result = client.getLatestMetricsSync(1L);

            // Then: Should get fallback response
            assertThat(result).containsEntry("status", "unavailable");

            // Restart for other tests
            wireMockServer.start();
        }

        @Test
        @DisplayName("Should handle 503 Service Unavailable gracefully")
        void serviceUnavailable503_UsesFallback() throws Exception {
            // Given: Service returns 503
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")));

            // When
            Map<String, Object> result = client.getLatestMetricsSync(1L);

            // Then: Should get fallback response
            assertThat(result).containsEntry("status", "unavailable");
        }
    }

    @Nested
    @DisplayName("Health Check")
    class HealthCheck {

        @Test
        @DisplayName("Should return true when service is healthy")
        void healthyService_ReturnsTrue() {
            // Given
            wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("{\"status\": \"UP\"}")));

            // When
            boolean healthy = client.isMonitoringServiceHealthy();

            // Then
            assertThat(healthy).isTrue();
        }

        @Test
        @DisplayName("Should return false when service is unhealthy")
        void unhealthyService_ReturnsFalse() {
            // Given
            wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse()
                            .withStatus(503)));

            // When
            boolean healthy = client.isMonitoringServiceHealthy();

            // Then
            assertThat(healthy).isFalse();
        }
    }
}

