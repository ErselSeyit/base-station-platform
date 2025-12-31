package com.huawei.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.model.AlertSeverity;
import com.huawei.monitoring.model.MetricType;

/**
 * Tests for AlertingService demonstrating:
 * - Edge case testing
 * - Boundary value analysis
 * - Nested test organization
 * - Behavior-driven naming
 */
class AlertingServiceTest {

    private AlertingService alertingService;

    @BeforeEach
    void setUp() {
        RabbitTemplate rabbitTemplate = Objects.requireNonNull(mock(RabbitTemplate.class));
        alertingService = new AlertingService(rabbitTemplate);
    }

    @Nested
    @DisplayName("Rule Management")
    class RuleManagement {

        @Test
        @DisplayName("Should initialize with default alert rules")
        void defaultRulesAreInitialized() {
            List<AlertRule> rules = alertingService.getAllRules();

            assertFalse(rules.isEmpty(), "Default rules should be initialized");
            assertTrue(rules.stream().anyMatch(r -> r.getId().equals("cpu-critical")),
                    "Should have cpu-critical rule");
            assertTrue(rules.stream().anyMatch(r -> r.getId().equals("memory-critical")),
                    "Should have memory-critical rule");
        }

        @Test
        @DisplayName("Should allow adding custom alert rules")
        void canAddCustomRule() {
            AlertRule customRule = Objects.requireNonNull(
                    AlertRule.builder()
                            .id("custom-rule")
                            .name("Custom Rule")
                            .metricType(MetricType.POWER_CONSUMPTION)
                            .operator(AlertRule.Operator.GREATER_THAN)
                            .threshold(5000.0)
                            .severity(AlertSeverity.WARNING)
                            .message("High power consumption")
                            .build(),
                    "Built alert rule cannot be null");

            alertingService.addRule(customRule);

            AlertRule retrieved = alertingService.getRule("custom-rule").orElse(null);
            assertNotNull(retrieved, "Custom rule should be retrievable after adding");
            assertEquals("Custom Rule", retrieved.getName());
        }

        @Test
        @DisplayName("Should remove rule when requested")
        void canRemoveRule() {
            alertingService.removeRule("cpu-warning");

            assertTrue(alertingService.getRule("cpu-warning").isEmpty(),
                    "Rule should not exist after removal");
        }

        @Test
        @DisplayName("Should toggle rule enabled state")
        void canEnableAndDisableRule() {
            alertingService.disableRule("cpu-critical");
            assertFalse(alertingService.getRule("cpu-critical").orElseThrow().isEnabled(),
                    "Rule should be disabled");

            alertingService.enableRule("cpu-critical");
            assertTrue(alertingService.getRule("cpu-critical").orElseThrow().isEnabled(),
                    "Rule should be enabled");
        }
    }

    @Nested
    @DisplayName("Metric Evaluation")
    class MetricEvaluation {

        @Test
        @DisplayName("Should not trigger alert when metric is below threshold")
        void doesNotTriggerWhenBelowThreshold() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 50.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertTrue(triggered.isEmpty(),
                    "No alerts should trigger when CPU at 50% (below 75% warning threshold)");
        }

        @Test
        @DisplayName("Should not trigger at exact threshold for GREATER_THAN operator")
        void doesNotTriggerAtExactThreshold() {
            // CPU critical rule uses GREATER_THAN 90.0
            MetricDataDTO metricAtThreshold = createMetric(MetricType.CPU_USAGE, 90.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metricAtThreshold);

            // Should trigger warning (>75) but NOT critical (>90, not >=90)
            assertFalse(triggered.stream().anyMatch(r -> r.getId().equals("cpu-critical")),
                    "GREATER_THAN should not trigger at exact threshold value");
            assertTrue(triggered.stream().anyMatch(r -> r.getId().equals("cpu-warning")),
                    "Warning rule should trigger since 90 > 75");
        }

        @Test
        @DisplayName("Should trigger alert when metric exceeds threshold")
        void triggersWhenAboveThreshold() {
            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 95.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertTrue(triggered.stream().anyMatch(r -> r.getId().equals("cpu-critical")),
                    "CPU critical alert should trigger at 95%");
            assertTrue(triggered.stream().anyMatch(r -> r.getId().equals("cpu-warning")),
                    "CPU warning alert should also trigger at 95%");
            assertEquals(2, triggered.size(), "Both CPU rules should trigger");
        }

        @Test
        @DisplayName("Should handle null metric type gracefully")
        void handlesNullMetricTypeGracefully() {
            MetricDataDTO metric = new MetricDataDTO();
            metric.setStationId(1L);
            metric.setValue(100.0);
            metric.setMetricType(null);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertTrue(triggered.isEmpty(), "Should return empty list for null metric type");
        }

        @Test
        @DisplayName("Should handle null value gracefully")
        void handlesNullValueGracefully() {
            MetricDataDTO metric = new MetricDataDTO();
            metric.setStationId(1L);
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(null);

            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertTrue(triggered.isEmpty(), "Should return empty list for null value");
        }

        @Test
        @DisplayName("Should not trigger disabled rules even when threshold exceeded")
        void disabledRulesDoNotTrigger() {
            alertingService.disableRule("cpu-critical");

            MetricDataDTO metric = createMetric(MetricType.CPU_USAGE, 99.0);
            List<AlertRule> triggered = alertingService.evaluateMetric(metric);

            assertFalse(triggered.stream().anyMatch(r -> r.getId().equals("cpu-critical")),
                    "Disabled rule should not trigger");
            assertTrue(triggered.stream().anyMatch(r -> r.getId().equals("cpu-warning")),
                    "Enabled warning rule should still trigger");
        }
    }

    @Nested
    @DisplayName("Operator Behavior")
    class OperatorBehavior {

        @Test
        @DisplayName("LESS_THAN operator should trigger when value is below threshold")
        void lessThanOperatorTriggersWhenBelow() {
            // Signal strength rule uses LESS_THAN -100.0
            MetricDataDTO weakSignal = createMetric(MetricType.SIGNAL_STRENGTH, -105.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(weakSignal);

            assertTrue(triggered.stream().anyMatch(r -> r.getId().equals("signal-weak")),
                    "Weak signal alert should trigger when signal < -100 dBm");
        }

        @Test
        @DisplayName("LESS_THAN operator should not trigger when value is above threshold")
        void lessThanOperatorDoesNotTriggerWhenAbove() {
            MetricDataDTO strongSignal = createMetric(MetricType.SIGNAL_STRENGTH, -50.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(strongSignal);

            assertTrue(triggered.isEmpty(),
                    "No alert should trigger for strong signal (-50 dBm)");
        }

        @Test
        @DisplayName("LESS_THAN operator should not trigger at exact threshold")
        void lessThanOperatorDoesNotTriggerAtExactThreshold() {
            MetricDataDTO signalAtThreshold = createMetric(MetricType.SIGNAL_STRENGTH, -100.0);

            List<AlertRule> triggered = alertingService.evaluateMetric(signalAtThreshold);

            assertTrue(triggered.isEmpty(),
                    "LESS_THAN should not trigger at exact threshold value");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("withThreshold() should create new instance without modifying original")
        void ruleWithThresholdCreatesNewInstance() {
            AlertRule original = alertingService.getRule("cpu-critical").orElseThrow();
            Double originalThreshold = original.getThreshold();

            AlertRule modified = original.withThreshold(99.0);

            assertEquals(originalThreshold, original.getThreshold(),
                    "Original rule threshold should remain unchanged");
            assertEquals(99.0, modified.getThreshold(),
                    "Modified rule should have new threshold");
        }

        @Test
        @DisplayName("withEnabled() should create new instance without modifying original")
        void ruleWithEnabledCreatesNewInstance() {
            AlertRule original = AlertRule.builder()
                    .id("test")
                    .name("Test")
                    .metricType(MetricType.CPU_USAGE)
                    .operator(AlertRule.Operator.GREATER_THAN)
                    .threshold(50.0)
                    .severity(AlertSeverity.INFO)
                    .enabled(true)
                    .build();

            AlertRule disabled = original.withEnabled(false);

            assertTrue(original.isEnabled(), "Original should remain enabled");
            assertFalse(disabled.isEnabled(), "New instance should be disabled");
        }
    }

    private MetricDataDTO createMetric(MetricType type, Double value) {
        MetricDataDTO metric = new MetricDataDTO();
        metric.setStationId(1L);
        metric.setStationName("BS-001");
        metric.setMetricType(type);
        metric.setValue(value);
        return metric;
    }
}
