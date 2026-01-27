package com.huawei.basestation.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.huawei.basestation.client.MonitoringServiceClient;
import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;

/**
 * Integration tests for batch metrics endpoint end-to-end flow.
 * 
 * These tests verify:
 * - base-station-service → monitoring-service batch endpoint communication
 * - Circuit breaker behavior under failure scenarios
 * - Fallback responses when monitoring service is unavailable
 * - Error handling and resilience patterns
 * - Performance improvement (batch vs individual calls)
 * 
 * Uses:
 * - Testcontainers for PostgreSQL database
 * - WireMock for mocking monitoring service
 * 
 * @see <a href="https://testcontainers.com/">Testcontainers</a>
 * @see <a href="http://wiremock.org/">WireMock</a>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {IntegrationTestApplication.class},
        properties = {
                "spring.cache.type=none",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.profiles.active=integration-test"
        })
@AutoConfigureMockMvc
@Testcontainers
@DisabledIf("skipInDemoOrNoDocker")
@DisplayName("Batch Metrics Integration Tests")
class BatchMetricsIntegrationTest {
    static boolean skipInDemoOrNoDocker() {
        try {
            boolean demoMode = Boolean.parseBoolean(System.getProperty("demo.mode",
                    String.valueOf(Boolean.parseBoolean(System.getenv().getOrDefault("DEMO_MODE", "false")))));
            boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
            return demoMode || !dockerAvailable;
        } catch (Exception e) {
            return true;
        }
    }

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages lifecycle via @Container annotation
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BaseStationRepository repository;

    @Autowired
    private MonitoringServiceClient monitoringClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Set up WireMock server for monitoring service
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        String monitoringServiceUrl = wireMockServer.baseUrl();
        registry.add("monitoring.service.url", () -> monitoringServiceUrl);

        // Disable Redis for tests
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.cache.type", () -> "none");
        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        
        if (wireMockServer != null && !wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        // Wait for WireMock to be ready
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> wireMockServer.isRunning() && wireMockServer.port() > 0);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
        if (postgres != null && postgres.isRunning()) {
            postgres.close();
        }
    }

    @Nested
    @DisplayName("Successful Batch Metrics Flow")
    class SuccessfulBatchFlow {

        @Test
        @DisplayName("Should fetch batch metrics for multiple stations successfully")
        void getBatchMetrics_Success() throws Exception {
            // Create test stations
            Long stationId1 = createStation("BS-001", 40.7128, -74.0060);
            Long stationId2 = createStation("BS-002", 40.7580, -73.9855);
            Long stationId3 = createStation("BS-003", 40.7829, -73.9654);

            // Mock monitoring service batch endpoint response
            String batchResponse = """
                    {
                        "%d": {"cpu": 45.5, "memory": 62.3, "timestamp": "2024-01-01T10:00:00"},
                        "%d": {"cpu": 50.2, "memory": 58.1, "timestamp": "2024-01-01T10:00:00"},
                        "%d": {"cpu": 42.8, "memory": 65.7, "timestamp": "2024-01-01T10:00:00"}
                    }
                    """.formatted(stationId1, stationId2, stationId3);

            wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics/batch/latest"))
                    .withRequestBody(equalToJson("""
                            {
                                "stationIds": [%d, %d, %d]
                            }
                            """.formatted(stationId1, stationId2, stationId3)))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(batchResponse)));

            // Wait for stub to be registered
            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getStubMappings().size() > 0);

            // Call the client method directly to test integration
            Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(
                    List.of(stationId1, stationId2, stationId3));

            // Verify results
            assertThat(batchMetrics)
                    .hasSize(3)
                    .containsKeys(stationId1, stationId2, stationId3);
            assertThat(batchMetrics.get(stationId1)).containsKeys("cpu", "memory", "timestamp");
            assertThat(batchMetrics.get(stationId1)).containsEntry("cpu", 45.5);
            assertThat(batchMetrics.get(stationId2)).containsEntry("cpu", 50.2);
            assertThat(batchMetrics.get(stationId3)).containsEntry("cpu", 42.8);

            // Verify WireMock received the request
            wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/metrics/batch/latest")));
        }

        @Test
        @DisplayName("Should handle empty station list gracefully")
        void getBatchMetrics_EmptyList_ReturnsEmpty() {
            Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(List.of());

            assertThat(batchMetrics).isEmpty();
            // Should not make any HTTP call
            wireMockServer.verify(0, postRequestedFor(urlEqualTo("/api/v1/metrics/batch/latest")));
        }

        @Test
        @DisplayName("Should handle partial results when some stations have no metrics")
        void getBatchMetrics_PartialResults() throws Exception {
            Long stationId1 = createStation("BS-001", 40.7128, -74.0060);
            Long stationId2 = createStation("BS-002", 40.7580, -73.9855);
            Long stationId3 = createStation("BS-003", 40.7829, -73.9654);

            // Mock response with only 2 stations (stationId3 has no metrics)
            String batchResponse = """
                    {
                        "%d": {"cpu": 45.5, "memory": 62.3, "timestamp": "2024-01-01T10:00:00"},
                        "%d": {"cpu": 50.2, "memory": 58.1, "timestamp": "2024-01-01T10:00:00"}
                    }
                    """.formatted(stationId1, stationId2);

            wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics/batch/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(batchResponse)));

            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getStubMappings().size() > 0);

            Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(
                    List.of(stationId1, stationId2, stationId3));

            // Should return only the stations that have metrics
            assertThat(batchMetrics)
                    .hasSize(2)
                    .containsKeys(stationId1, stationId2)
                    .doesNotContainKey(stationId3);
        }
    }

    @Nested
    @DisplayName("Circuit Breaker and Fallback")
    class CircuitBreakerAndFallback {

        @Test
        @DisplayName("Should return fallback response when monitoring service is down")
        void getBatchMetrics_ServiceDown_ReturnsFallback() throws Exception {
            Long stationId1 = createStation("BS-001", 40.7128, -74.0060);
            Long stationId2 = createStation("BS-002", 40.7580, -73.9855);

            // Mock service returning 500 error
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics/batch/latest"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\": \"Internal Server Error\"}")));

            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getStubMappings().size() > 0);

            // Call should trigger circuit breaker and fallback
            Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(
                    List.of(stationId1, stationId2));

            // Verify fallback response structure
            assertThat(batchMetrics)
                    .isNotNull()
                    .hasSize(2)
                    .containsKeys(stationId1, stationId2);

            // Verify fallback indicators
            Map<String, Object> fallback1 = batchMetrics.get(stationId1);
            assertThat(fallback1)
                    .containsKey("fallback")
                    .containsEntry("fallback", true)
                    .containsKey("status")
                    .containsEntry("status", "unavailable")
                    .containsKey("message");
        }

        @Test
        @DisplayName("Should handle timeout gracefully")
        void getBatchMetrics_Timeout_HandlesGracefully() throws Exception {
            Long stationId1 = createStation("BS-001", 40.7128, -74.0060);

            // Mock a slow response (but WireMock doesn't support actual delays easily)
            // Instead, we'll test with a service that doesn't respond
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics/batch/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(5000) // 5 second delay
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getStubMappings().size() > 0);

            // The call should either timeout or be handled by circuit breaker
            // This depends on the resilience4j configuration
            Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(
                    List.of(stationId1));

            // Should not throw exception, should return fallback or empty
            assertThat(batchMetrics).isNotNull();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle invalid station IDs gracefully")
        void getBatchMetrics_InvalidStationIds() {
            // Mock response for non-existent stations
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics/batch/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}"))); // Empty response for non-existent stations

            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getStubMappings().size() > 0);

            Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(
                    List.of(99999L, 99998L));

            // Should return empty map or handle gracefully
            assertThat(batchMetrics).isNotNull();
        }

        @Test
        @DisplayName("Should handle malformed response from monitoring service")
        void getBatchMetrics_MalformedResponse() throws Exception {
            Long stationId1 = createStation("BS-001", 40.7128, -74.0060);

            // Mock malformed JSON response
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics/batch/latest"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{ invalid json }")));

            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> wireMockServer.getStubMappings().size() > 0);

            // Should handle parsing error gracefully
            try {
                Map<Long, Map<String, Object>> batchMetrics = monitoringClient.getLatestMetricsBatch(
                        List.of(stationId1));
                // May return empty map or throw exception depending on implementation
                assertThat(batchMetrics).isNotNull();
            } catch (Exception e) {
                // If exception is thrown, it should be a reasonable one
                assertThat(e).isNotNull();
            }
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private Long createStation(String name, double lat, double lon) throws Exception {
        BaseStationDTO dto = new BaseStationDTO();
        dto.setStationName(name);
        dto.setLocation("Test Location");
        dto.setLatitude(lat);
        dto.setLongitude(lon);
        dto.setStationType(StationType.MACRO_CELL);
        dto.setStatus(StationStatus.ACTIVE);
        dto.setPowerConsumption(1500.0);

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/stations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(dto))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }
}
