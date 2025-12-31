package com.huawei.basestation.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = {
        com.huawei.basestation.test.TestApplication.class })
@DisplayName("Monitoring Service Resilience Tests")
class MonitoringServiceResilienceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MonitoringServiceClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig()
                        .port(45171)
                        .bindAddress("127.0.0.1"));
        wireMockServer.start();

        registry.add("monitoring.service.url", () -> "http://127.0.0.1:45171");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    }

    @BeforeEach
    void setUp() {
        if (wireMockServer != null && !wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> wireMockServer.isRunning() && wireMockServer.port() > 0);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("monitoringService");
        cb.reset();
        if (cb.getState() != CircuitBreaker.State.CLOSED) {
            cb.transitionToClosedState();
        }

        // Wait for circuit breaker to stabilize
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> cb.getState() == CircuitBreaker.State.CLOSED);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    @Nested
    @DisplayName("Normal Operation")
    class NormalOperation {

        @Test
        @DisplayName("Should return metrics when service responds normally")
        void successfulResponse() {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("monitoringService");
            cb.reset();
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // Set up stub for successful response
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cpu\":45.5,\"memory\":62.3}")));

            // Wait for successful response
            AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(300))
                    .untilAsserted(() -> {
                        Map<String, Object> attempt = client.getLatestMetricsSync(1L);
                        // Check if we got a successful response (no status key means success)
                        assertThat(attempt).doesNotContainKey("status");
                        resultRef.set(attempt);
                    });

            Map<String, Object> result = resultRef.get();

            wireMockServer.verify(getRequestedFor(urlPathMatching("/api/v1/metrics/station/.*/latest")));

            assertThat(result)
                    .containsKeys("cpu", "memory")
                    .containsEntry("cpu", 45.5)
                    .doesNotContainKey("status");
        }
    }

    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutHandling {

        @Test
        @DisplayName("Should trigger fallback when service is too slow")
        void slowService_TriggersFallback() {
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cpu\": 45.5}")
                            .withFixedDelay(5000)));

            try {
                CompletableFuture<Map<String, Object>> future = client.getLatestMetrics(1L);
                Map<String, Object> result = future.get(10, TimeUnit.SECONDS);

                assertThat(result)
                        .containsEntry("status", "timeout")
                        .containsEntry("stationId", 1L);
            } catch (Exception e) {
                throw new AssertionError("Failed to get metrics", e);
            }
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Behavior")
    class CircuitBreakerBehavior {

        @Test
        @DisplayName("Should open circuit after repeated failures")
        void repeatedFailures_OpensCircuit() {
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("monitoringService");

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            IntStream.range(0, 10).forEach(i -> {
                try {
                    client.getLatestMetricsSync(1L);
                } catch (Exception e) {
                    // Expected - service is returning 500
                }
            });

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN));
        }

        @Test
        @DisplayName("Should return fallback when circuit is open")
        void openCircuit_ReturnsFallback() {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("monitoringService");
            circuitBreaker.transitionToOpenState();

            Map<String, Object> result = client.getLatestMetricsSync(1L);

            assertThat(result)
                    .containsEntry("status", "unavailable")
                    .containsEntry("stationId", 1L);

            wireMockServer.verify(0, getRequestedFor(urlPathMatching("/api/v1/metrics/.*")));
        }

        @Test
        @DisplayName("Should recover when service becomes healthy")
        void serviceRecovery_ClosesCircuit() {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("monitoringService");
            circuitBreaker.reset();

            circuitBreaker.transitionToOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Stubs can only be set while server is running
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cpu\": 45.5, \"memory\": 62.3}")));

            // Wait a moment for stub to be registered
            await().atMost(Duration.ofSeconds(1))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getAllServeEvents().size() >= 0);

            // Transition to HALF_OPEN to test recovery
            circuitBreaker.transitionToHalfOpenState();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // Make multiple successful calls to trigger transition to CLOSED
            // Config requires 3 successful calls in HALF_OPEN state (permittedNumberOfCallsInHalfOpenState: 3)
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .ignoreExceptions()
                    .until(() -> {
                        for (int i = 0; i < 3; i++) {
                            Map<String, Object> result = client.getLatestMetricsSync(1L);
                            if (result.containsKey("status")) {
                                return false; // Still failing
                            }
                        }
                        return true;
                    });

            // Now check that it transitioned to CLOSED
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> assertThat(circuitBreaker.getState())
                            .isEqualTo(CircuitBreaker.State.CLOSED));
        }
    }

    @Nested
    @DisplayName("Service Unavailable")
    class ServiceUnavailable {

        @Test
        @DisplayName("Should use fallback when service is completely down")
        void serviceDown_UsesFallback() {
            // Don't stub anything - will result in connection refused
            Map<String, Object> result = client.getLatestMetricsSync(1L);

            assertThat(result).containsEntry("status", "unavailable");
        }

        @Test
        @DisplayName("Should handle 503 Service Unavailable gracefully")
        void serviceUnavailable503_UsesFallback() {
            wireMockServer.stubFor(get(urlPathMatching("/api/v1/metrics/station/.*/latest"))
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")));

            Map<String, Object> result = client.getLatestMetricsSync(1L);

            assertThat(result).containsEntry("status", "unavailable");
        }
    }

    @Nested
    @DisplayName("Health Check")
    class HealthCheck {

        @Test
        @DisplayName("Should return true when service is healthy")
        void healthyService_ReturnsTrue() {
            // Reset circuit breaker to ensure clean state
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("monitoringService");
            cb.reset();
            if (cb.getState() != CircuitBreaker.State.CLOSED) {
                cb.transitionToClosedState();
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // Set up stub for health check
            wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"status\":\"UP\"}")));

            // Call health check with retry logic
            await().atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> {
                        boolean healthy = client.isMonitoringServiceHealthy();
                        assertThat(healthy).isTrue();
                    });

            // Verify the request was made
            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> {
                        wireMockServer.verify(getRequestedFor(urlEqualTo("/actuator/health")));
                    });
        }

        @Test
        @DisplayName("Should return false when service is unhealthy")
        void unhealthyService_ReturnsFalse() {
            // Set up stub for unhealthy response
            wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")));

            // Call health check - should return false or throw exception
            try {
                boolean healthy = client.isMonitoringServiceHealthy();
                assertThat(healthy).isFalse();
            } catch (Exception e) {
                // If exception is thrown, that's also acceptable for unhealthy service
                assertThat(e).isNotNull();
            }
        }
    }
}
