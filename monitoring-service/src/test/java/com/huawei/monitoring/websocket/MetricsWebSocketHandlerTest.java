package com.huawei.monitoring.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;

/**
 * Integration tests for MetricsWebSocketHandler.
 *
 * Tests WebSocket connection management and message broadcasting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("MetricsWebSocketHandler Tests")
@DisabledIf("skipInDemoOrNoDocker")
class MetricsWebSocketHandlerTest {

    /**
     * Skip integration tests if demo mode is enabled or Docker is not available.
     * Demo mode allows running tests without external dependencies.
     */
    static boolean skipInDemoOrNoDocker() {
        // Check for demo mode via system property or environment variable
        String demoMode = System.getProperty("demo.mode", System.getenv().getOrDefault("DEMO_MODE", "false"));
        if (Boolean.parseBoolean(demoMode)) {
            return true;
        }
        
        // Check if Docker is available
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            Process p = pb.start();
            return p.waitFor() != 0;
        } catch (Exception e) {
            return true; // Docker not available
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MetricsWebSocketHandler metricsHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private StandardWebSocketClient client;
    private List<WebSocketSession> openSessions;

    @BeforeEach
    void setUp() {
        client = new StandardWebSocketClient();
        openSessions = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        // Close all open sessions
        for (WebSocketSession session : openSessions) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        openSessions.clear();
    }

    @Test
    @DisplayName("Should connect to WebSocket endpoint successfully")
    void shouldConnectSuccessfully() throws Exception {
        // Given
        String wsUrl = "ws://localhost:" + port + "/ws/metrics";
        TestWebSocketHandler testHandler = new TestWebSocketHandler();

        // When
        WebSocketSession session = client.execute(testHandler, wsUrl).get(5, TimeUnit.SECONDS);
        openSessions.add(session);

        // Then
        assertThat(session.isOpen()).isTrue();
        assertThat(metricsHandler.getActiveConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle multiple concurrent WebSocket connections")
    void shouldHandleMultipleConnections() throws Exception {
        // Given
        String wsUrl = "ws://localhost:" + port + "/ws/metrics";
        int numberOfClients = 5;
        List<CompletableFuture<WebSocketSession>> futures = new ArrayList<>();

        // When - connect multiple clients
        for (int i = 0; i < numberOfClients; i++) {
            TestWebSocketHandler handler = new TestWebSocketHandler();
            CompletableFuture<WebSocketSession> future = client.execute(handler, wsUrl);
            futures.add(future);
        }

        // Wait for all connections
        for (CompletableFuture<WebSocketSession> future : futures) {
            WebSocketSession session = future.get(5, TimeUnit.SECONDS);
            openSessions.add(session);
        }

        // Then
        assertThat(metricsHandler.getActiveConnections()).isEqualTo(numberOfClients);
        assertThat(openSessions)
                .hasSize(numberOfClients)
                .allMatch(WebSocketSession::isOpen);
    }

    @Test
    @DisplayName("Should broadcast metric to all connected clients")
    void shouldBroadcastMetricToAllClients() throws Exception {
        // Given
        String wsUrl = "ws://localhost:" + port + "/ws/metrics";
        int numberOfClients = 3;
        List<TestWebSocketHandler> handlers = new ArrayList<>();

        // Connect multiple clients
        for (int i = 0; i < numberOfClients; i++) {
            TestWebSocketHandler handler = new TestWebSocketHandler();
            handlers.add(handler);
            WebSocketSession session = client.execute(handler, wsUrl).get(5, TimeUnit.SECONDS);
            openSessions.add(session);
        }

        // Create test metric
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(1L);
        metric.setStationName("Test Station");
        metric.setMetricType(MetricType.CPU_USAGE);
        metric.setValue(75.5);

        // When - broadcast metric
        metricsHandler.broadcastMetric(metric);

        // Then - all clients should receive the message
        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                for (TestWebSocketHandler handler : handlers) {
                    assertThat(handler.getReceivedMessages()).hasSize(1);

                    String receivedJson = handler.getReceivedMessages().get(0);
                    MetricDataDTO receivedMetric = objectMapper.readValue(receivedJson, MetricDataDTO.class);

                    assertThat(receivedMetric.getStationId()).isEqualTo(1L);
                    assertThat(receivedMetric.getStationName()).isEqualTo("Test Station");
                    assertThat(receivedMetric.getMetricType()).isEqualTo(MetricType.CPU_USAGE);
                    assertThat(receivedMetric.getValue()).isEqualTo(75.5);
                }
            });
    }

    @Test
    @DisplayName("Should decrease connection count when client disconnects")
    void shouldDecreaseConnectionCountOnDisconnect() throws Exception {
        // Given
        String wsUrl = "ws://localhost:" + port + "/ws/metrics";
        TestWebSocketHandler handler = new TestWebSocketHandler();
        WebSocketSession session = client.execute(handler, wsUrl).get(5, TimeUnit.SECONDS);
        openSessions.add(session);

        assertThat(metricsHandler.getActiveConnections()).isEqualTo(1);

        // When - disconnect
        session.close();

        // Then - connection count should decrease
        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() ->
                assertThat(metricsHandler.getActiveConnections()).isZero()
            );
    }

    @Test
    @DisplayName("Should not broadcast when no clients are connected")
    void shouldNotBroadcastWithNoClients() {
        // Given
        assertThat(metricsHandler.getActiveConnections()).isZero();

        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(1L);
        metric.setMetricType(MetricType.MEMORY_USAGE);
        metric.setValue(50.0);

        // When - broadcast with no connected clients
        metricsHandler.broadcastMetric(metric);

        // Then - no exception should be thrown
        assertThat(metricsHandler.getActiveConnections()).isZero();
    }

    @Test
    @DisplayName("Should handle multiple broadcasts to same clients")
    void shouldHandleMultipleBroadcasts() throws Exception {
        // Given
        String wsUrl = "ws://localhost:" + port + "/ws/metrics";
        TestWebSocketHandler handler = new TestWebSocketHandler();
        WebSocketSession session = client.execute(handler, wsUrl).get(5, TimeUnit.SECONDS);
        openSessions.add(session);

        // When - broadcast multiple metrics
        for (int i = 1; i <= 5; i++) {
            MetricDataDTO metric = new MetricDataDTO();
            metric.setStationId((long) i);
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(50.0 + i);
            metricsHandler.broadcastMetric(metric);
        }

        // Then - client should receive all messages
        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                assertThat(handler.getReceivedMessages()).hasSize(5);

                for (int i = 0; i < 5; i++) {
                    MetricDataDTO receivedMetric = objectMapper.readValue(
                        handler.getReceivedMessages().get(i),
                        MetricDataDTO.class
                    );
                    assertThat(receivedMetric.getStationId()).isEqualTo((long) (i + 1));
                    assertThat(receivedMetric.getValue()).isEqualTo(51.0 + i);
                }
            });
    }

    @Test
    @DisplayName("Should broadcast different metric types correctly")
    void shouldBroadcastDifferentMetricTypes() throws Exception {
        // Given
        String wsUrl = "ws://localhost:" + port + "/ws/metrics";
        TestWebSocketHandler handler = new TestWebSocketHandler();
        WebSocketSession session = client.execute(handler, wsUrl).get(5, TimeUnit.SECONDS);
        openSessions.add(session);

        MetricType[] metricTypes = {
            MetricType.CPU_USAGE,
            MetricType.MEMORY_USAGE,
            MetricType.TEMPERATURE,
            MetricType.SIGNAL_STRENGTH,
            MetricType.DATA_THROUGHPUT
        };

        // When - broadcast different metric types
        for (int i = 0; i < metricTypes.length; i++) {
            MetricDataDTO metric = new MetricDataDTO();
            metric.setStationId(1L);
            metric.setMetricType(metricTypes[i]);
            metric.setValue(50.0 + i);
            metricsHandler.broadcastMetric(metric);
        }

        // Then - verify all metric types were received
        await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                assertThat(handler.getReceivedMessages()).hasSize(metricTypes.length);

                for (int i = 0; i < metricTypes.length; i++) {
                    MetricDataDTO receivedMetric = objectMapper.readValue(
                        handler.getReceivedMessages().get(i),
                        MetricDataDTO.class
                    );
                    assertThat(receivedMetric.getMetricType()).isEqualTo(metricTypes[i]);
                }
            });
    }

    /**
     * Test WebSocket handler that captures received messages.
     */
    private static class TestWebSocketHandler extends TextWebSocketHandler {
        private final List<String> receivedMessages = new ArrayList<>();

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
            receivedMessages.add(message.getPayload());
        }

        public List<String> getReceivedMessages() {
            return receivedMessages;
        }
    }
}
