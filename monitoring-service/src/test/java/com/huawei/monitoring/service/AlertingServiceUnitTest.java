package com.huawei.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.model.MetricType;

/**
 * Unit tests for AlertingService that don't require Docker.
 *
 * These tests use mocks for external dependencies (RabbitMQ, DiagnosticClient)
 * and can run in any environment including CI/CD without Docker.
 */
@DisplayName("AlertingService Unit Tests")
@SuppressWarnings("null") // Intentionally passing null to test graceful handling
class AlertingServiceUnitTest {

    private AlertingService alertingService;
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        // DiagnosticClient is null - AlertingService handles this gracefully
        alertingService = new AlertingService(rabbitTemplate, null);
    }

    @Nested
    @DisplayName("Alert Evaluation")
    class AlertEvaluation {

        @Test
        @DisplayName("Should trigger critical alert when CPU exceeds 90%")
        void shouldTriggerCriticalAlertForHighCpu() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 95.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).anyMatch(r -> r.getId().equals("cpu-critical"));
            // Both cpu-critical and cpu-warning trigger at 95%
            verify(rabbitTemplate, org.mockito.Mockito.atLeastOnce()).convertAndSend(anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Should trigger warning alert when CPU exceeds 75%")
        void shouldTriggerWarningAlertForModerateCpu() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 80.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).anyMatch(r -> r.getId().equals("cpu-warning"));
        }

        @Test
        @DisplayName("Should not trigger alert when CPU is below thresholds")
        void shouldNotTriggerAlertForNormalCpu() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 50.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).isEmpty();
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("Should trigger memory critical alert at 95%")
        void shouldTriggerMemoryCriticalAlert() {
            MetricDataDTO metric = createMetric(MetricType.MEMORY_USAGE, 97.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).anyMatch(r -> r.getId().equals("memory-critical"));
        }

        @Test
        @DisplayName("Should handle null metric value gracefully")
        void shouldHandleNullMetricValue() {
            MetricDataDTO metric = new MetricDataDTO();
            metric.setStationId(1L);
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(null);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).isEmpty();
        }

        @Test
        @DisplayName("Should handle null metric type gracefully")
        void shouldHandleNullMetricType() {
            MetricDataDTO metric = new MetricDataDTO();
            metric.setStationId(1L);
            metric.setValue(100.0);
            metric.setMetricType(null);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).isEmpty();
        }
    }

    @Nested
    @DisplayName("Rule Management")
    class RuleManagement {

        @Test
        @DisplayName("Should have default rules initialized")
        void shouldHaveDefaultRules() {
            List<AlertRule> rules = alertingService.getAllRules();

            assertThat(rules)
                    .isNotEmpty()
                    .extracting(AlertRule::getId)
                    .contains("cpu-critical", "cpu-warning", "memory-critical");
        }

        @Test
        @DisplayName("Should disable rule correctly")
        void shouldDisableRule() {
            alertingService.disableRule("cpu-critical");

            AlertRule rule = alertingService.getRule("cpu-critical").orElseThrow();
            assertThat(rule.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should not trigger disabled rules")
        void shouldNotTriggerDisabledRules() {
            alertingService.disableRule("cpu-critical");
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 95.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered)
                    .extracting(AlertRule::getId)
                    .doesNotContain("cpu-critical")
                    .contains("cpu-warning"); // Warning should still trigger
        }
    }

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        @Test
        @DisplayName("Should not trigger at exact threshold for GREATER_THAN")
        void shouldNotTriggerAtExactThreshold() {
            // CPU critical threshold is 90.0 with GREATER_THAN
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 90.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).noneMatch(r -> r.getId().equals("cpu-critical"));
        }

        @Test
        @DisplayName("Should trigger just above threshold")
        void shouldTriggerJustAboveThreshold() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 90.01);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).anyMatch(r -> r.getId().equals("cpu-critical"));
        }

        @Test
        @DisplayName("Should handle zero value")
        void shouldHandleZeroValue() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 0.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).isEmpty();
        }

        @Test
        @DisplayName("Should handle negative signal strength")
        void shouldHandleNegativeSignalStrength() {
            MetricDataDTO metric = createMetric(MetricType.SIGNAL_STRENGTH, -105.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertThat(triggered).anyMatch(r -> r.getId().equals("signal-weak"));
        }
    }

    private MetricDataDTO createMetric(MetricType type, Double value) {
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(1L);
        metric.setStationName("Test Station");
        metric.setMetricType(type);
        metric.setValue(value);
        return metric;
    }
}
