package com.huawei.monitoring.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.huawei.monitoring.config.RabbitMQConfig;
import com.huawei.monitoring.dto.AlertEvent;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.model.AlertSeverity;
import com.huawei.monitoring.model.MetricType;

/**
 * Alerting rules engine that evaluates incoming metrics against configurable
 * thresholds.
 * 
 * This demonstrates:
 * - Domain-driven design (AlertRule as a value object)
 * - Strategy pattern (different comparison operators)
 * - Thread-safe rule management
 * - Event-driven architecture (integrates with notification service via RabbitMQ)
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();
    private final RabbitTemplate rabbitTemplate;

    public AlertingService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "RabbitTemplate cannot be null");
        // Initialize default rules - in production, these would be from DB/config
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        addRule(Objects.requireNonNull(
                AlertRule.builder()
                        .id("cpu-critical")
                        .name("CPU Critical")
                        .metricType(MetricType.CPU_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(90.0)
                        .severity(AlertSeverity.CRITICAL)
                        .message("CPU usage exceeded 90%")
                        .build(),
                "Built alert rule cannot be null"));

        addRule(Objects.requireNonNull(
                AlertRule.builder()
                        .id("cpu-warning")
                        .name("CPU Warning")
                        .metricType(MetricType.CPU_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(75.0)
                        .severity(AlertSeverity.WARNING)
                        .message("CPU usage exceeded 75%")
                        .build(),
                "Built alert rule cannot be null"));

        addRule(Objects.requireNonNull(
                AlertRule.builder()
                        .id("memory-critical")
                        .name("Memory Critical")
                        .metricType(MetricType.MEMORY_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(95.0)
                        .severity(AlertSeverity.CRITICAL)
                        .message("Memory usage exceeded 95%")
                        .build(),
                "Built alert rule cannot be null"));

        addRule(Objects.requireNonNull(
                AlertRule.builder()
                        .id("temperature-critical")
                        .name("Temperature Critical")
                        .metricType(MetricType.TEMPERATURE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(80.0)
                        .severity(AlertSeverity.CRITICAL)
                        .message("Temperature exceeded safe threshold")
                        .build(),
                "Built alert rule cannot be null"));

        addRule(Objects.requireNonNull(
                AlertRule.builder()
                        .id("signal-weak")
                        .name("Weak Signal")
                        .metricType(MetricType.SIGNAL_STRENGTH)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(-100.0)
                        .severity(AlertSeverity.WARNING)
                        .message("Signal strength below acceptable level")
                        .build(),
                "Built alert rule cannot be null"));
    }

    /**
     * Evaluates a metric against all applicable rules.
     * Returns all rules that were triggered and logs alerts.
     * 
     * @param metric the metric data to evaluate
     * @return list of triggered alert rules (empty if none triggered)
     */
    public List<AlertRule> evaluateMetric(MetricDataDTO metric) {
        if (metric.getMetricType() == null || metric.getValue() == null) {
            return Objects.requireNonNull(List.of(), "Empty list cannot be null");
        }

        List<AlertRule> triggeredRules = rules.values().stream()
                .filter(rule -> rule.getMetricType() == metric.getMetricType())
                .filter(AlertRule::isEnabled)
                .filter(rule -> evaluateRule(Objects.requireNonNull(rule, "Rule cannot be null"),
                        Objects.requireNonNull(metric.getValue(), "Metric value cannot be null")))
                .toList();

        triggeredRules.forEach(rule -> triggerAlert(
                Objects.requireNonNull(rule, "Triggered rule cannot be null"), metric));

        return triggeredRules;
    }

    private boolean evaluateRule(AlertRule rule, Double value) {
        return switch (rule.getOperator()) {
            case GREATER_THAN -> value > rule.getThreshold();
            case GREATER_THAN_OR_EQUAL -> value >= rule.getThreshold();
            case LESS_THAN -> value < rule.getThreshold();
            case LESS_THAN_OR_EQUAL -> value <= rule.getThreshold();
            case EQUALS -> Math.abs(value - rule.getThreshold()) < 0.0001;
        };
    }

    private void triggerAlert(AlertRule rule, MetricDataDTO metric) {
        log.warn("ALERT [{}] {}: {} for station {} (value: {}, threshold: {})",
                rule.getSeverity(),
                rule.getName(),
                rule.getMessage(),
                metric.getStationId(),
                metric.getValue(),
                rule.getThreshold());

        // Send alert event to notification service via RabbitMQ
        try {
            AlertEvent alertEvent = new AlertEvent(
                    rule.getId(),
                    rule.getName(),
                    metric.getStationId(),
                    metric.getStationName(),
                    metric.getMetricType(),
                    metric.getValue(),
                    rule.getThreshold(),
                    rule.getSeverity(),
                    rule.getMessage()
            );
            
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ALERTS_EXCHANGE,
                    RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY,
                    alertEvent
            );
            
            log.debug("Alert event published to RabbitMQ: ruleId={}, stationId={}", 
                    rule.getId(), metric.getStationId());
        } catch (Exception e) {
            log.error("Failed to publish alert event to RabbitMQ: ruleId={}, stationId={}", 
                    rule.getId(), metric.getStationId(), e);
            // Continue execution even if RabbitMQ fails - alert is still logged
        }
    }

    // ========================================
    // Rule Management API
    // ========================================

    public void addRule(AlertRule rule) {
        rules.put(rule.getId(), rule);
        log.info("Added alert rule: {}", rule.getName());
    }

    public void removeRule(String ruleId) {
        AlertRule removed = rules.remove(ruleId);
        if (removed != null) {
            log.info("Removed alert rule: {}", removed.getName());
        }
    }

    public void enableRule(String ruleId) {
        AlertRule rule = rules.get(ruleId);
        if (rule != null) {
            rules.put(ruleId, rule.withEnabled(true));
        }
    }

    public void disableRule(String ruleId) {
        AlertRule rule = rules.get(ruleId);
        if (rule != null) {
            rules.put(ruleId, rule.withEnabled(false));
        }
    }

    public List<AlertRule> getAllRules() {
        return Objects.requireNonNull(
                List.copyOf(rules.values()),
                "Rule list cannot be null");
    }

    public Optional<AlertRule> getRule(String ruleId) {
        return Objects.requireNonNull(
                Optional.ofNullable(rules.get(ruleId)),
                "Optional cannot be null");
    }
}
