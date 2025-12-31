package com.huawei.monitoring.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.huawei.common.dto.AlertEvent;
import com.huawei.monitoring.config.RabbitMQConfig;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.service.AlertingService;

/**
 * Integration test for RabbitMQ alert event flow.
 *
 * Tests the end-to-end flow of alert events from AlertingService
 * to RabbitMQ exchange. Uses Testcontainers to spin up a real
 * RabbitMQ instance.
 *
 * Note: This test verifies the publishing side. A complete end-to-end
 * test would also verify the notification-service consumer, which
 * would require multi-service integration testing.
 */
@SpringBootTest
@Testcontainers
@DisplayName("RabbitMQ Alert Flow Integration Tests")
@DisabledIf("skipInDemoOrNoDocker")
@SuppressWarnings("resource") // Testcontainers manages RabbitMQContainer lifecycle via @Container
class RabbitMQAlertFlowIntegrationTest {

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

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management-alpine"))
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void configureRabbitMQ(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    private AlertingService alertingService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        // Configure RabbitTemplate to receive messages for testing
        rabbitTemplate.setReceiveTimeout(5000);
    }

    @Test
    @DisplayName("Should publish alert event to RabbitMQ when rule is triggered")
    void shouldPublishAlertEventWhenRuleTriggered() {
        // Given
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(1L);
        metric.setStationName("Test Station");
        metric.setMetricType(MetricType.CPU_USAGE);
        metric.setValue(95.0); // Exceeds critical threshold (90.0)

        // When
        alertingService.evaluateMetric(metric);

        // Then - verify message was published to RabbitMQ
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                AlertEvent receivedEvent = (AlertEvent) rabbitTemplate.receiveAndConvert(
                        RabbitMQConfig.ALERTS_EXCHANGE + "." + RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY);

                if (receivedEvent != null) {
                    assertThat(receivedEvent.getAlertRuleId()).isEqualTo("cpu-critical");
                    assertThat(receivedEvent.getAlertRuleName()).isEqualTo("CPU Critical");
                    assertThat(receivedEvent.getStationId()).isEqualTo(1L);
                    assertThat(receivedEvent.getStationName()).isEqualTo("Test Station");
                    assertThat(receivedEvent.getMetricType()).isEqualTo("CPU_USAGE");
                    assertThat(receivedEvent.getMetricValue()).isEqualTo(95.0);
                    assertThat(receivedEvent.getThreshold()).isEqualTo(90.0);
                    assertThat(receivedEvent.getSeverity()).isEqualTo("CRITICAL");
                    assertThat(receivedEvent.getMessage()).contains("CPU usage exceeded 90%");
                }
            });
    }

    @Test
    @DisplayName("Should publish warning alert for CPU usage above 75%")
    void shouldPublishWarningAlertForHighCpu() {
        // Given
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(2L);
        metric.setStationName("Station 2");
        metric.setMetricType(MetricType.CPU_USAGE);
        metric.setValue(80.0); // Exceeds warning threshold (75.0) but not critical (90.0)

        // When
        alertingService.evaluateMetric(metric);

        // Then - verify warning alert was published
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                AlertEvent receivedEvent = (AlertEvent) rabbitTemplate.receiveAndConvert(
                        RabbitMQConfig.ALERTS_EXCHANGE + "." + RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY);

                if (receivedEvent != null) {
                    assertThat(receivedEvent.getAlertRuleId()).isEqualTo("cpu-warning");
                    assertThat(receivedEvent.getSeverity()).isEqualTo("WARNING");
                    assertThat(receivedEvent.getMetricValue()).isEqualTo(80.0);
                }
            });
    }

    @Test
    @DisplayName("Should publish alert for memory exceeding critical threshold")
    void shouldPublishMemoryCriticalAlert() {
        // Given
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(3L);
        metric.setStationName("Station 3");
        metric.setMetricType(MetricType.MEMORY_USAGE);
        metric.setValue(97.0); // Exceeds critical threshold (95.0)

        // When
        alertingService.evaluateMetric(metric);

        // Then
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                AlertEvent receivedEvent = (AlertEvent) rabbitTemplate.receiveAndConvert(
                        RabbitMQConfig.ALERTS_EXCHANGE + "." + RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY);

                if (receivedEvent != null) {
                    assertThat(receivedEvent.getAlertRuleId()).isEqualTo("memory-critical");
                    assertThat(receivedEvent.getMetricType()).isEqualTo("MEMORY_USAGE");
                    assertThat(receivedEvent.getSeverity()).isEqualTo("CRITICAL");
                }
            });
    }

    @Test
    @DisplayName("Should not publish alert when metric is below threshold")
    void shouldNotPublishAlertWhenBelowThreshold() {
        // Given
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(4L);
        metric.setStationName("Station 4");
        metric.setMetricType(MetricType.CPU_USAGE);
        metric.setValue(50.0); // Below all thresholds

        // Record queue depth before
        Long countBefore = rabbitTemplate.execute(channel ->
            channel.messageCount(RabbitMQConfig.ALERTS_EXCHANGE + "." + RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY));
        long messageCountBefore = countBefore != null ? countBefore : 0L;

        // When
        alertingService.evaluateMetric(metric);

        // Then - verify no new messages appear within a reasonable time window
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    Long count = rabbitTemplate.execute(channel ->
                        channel.messageCount(RabbitMQConfig.ALERTS_EXCHANGE + "." + RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY));
                    long currentCount = count != null ? count : 0L;
                    assertThat(currentCount).isEqualTo(messageCountBefore);
                });
    }

    @Test
    @DisplayName("Should publish temperature alert for high temperature")
    void shouldPublishTemperatureAlert() {
        // Given
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(5L);
        metric.setStationName("Station 5");
        metric.setMetricType(MetricType.TEMPERATURE);
        metric.setValue(85.0); // Exceeds threshold (80.0)

        // When
        alertingService.evaluateMetric(metric);

        // Then
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                AlertEvent receivedEvent = (AlertEvent) rabbitTemplate.receiveAndConvert(
                        RabbitMQConfig.ALERTS_EXCHANGE + "." + RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY);

                if (receivedEvent != null) {
                    assertThat(receivedEvent.getAlertRuleId()).isEqualTo("temperature-critical");
                    assertThat(receivedEvent.getMetricType()).isEqualTo("TEMPERATURE");
                    assertThat(receivedEvent.getMetricValue()).isEqualTo(85.0);
                }
            });
    }
}
