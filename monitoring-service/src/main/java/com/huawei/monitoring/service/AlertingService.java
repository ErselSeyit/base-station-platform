package com.huawei.monitoring.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.model.AlertSeverity;
import com.huawei.monitoring.model.MetricType;

/**
 * Alerting rules engine that evaluates incoming metrics against configurable thresholds.
 * 
 * This demonstrates:
 * - Domain-driven design (AlertRule as a value object)
 * - Strategy pattern (different comparison operators)
 * - Thread-safe rule management
 * - Event-driven architecture (would integrate with notification service)
 */
@Service
@SuppressWarnings("null") // Suppress Eclipse null-analysis warnings for Spring-managed beans
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();

    public AlertingService() {
        // Initialize default rules - in production, these would be from DB/config
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        addRule(AlertRule.builder()
                .id("cpu-critical")
                .name("CPU Critical")
                .metricType(MetricType.CPU_USAGE)
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(90.0)
                .severity(AlertSeverity.CRITICAL)
                .message("CPU usage exceeded 90%")
                .build());

        addRule(AlertRule.builder()
                .id("cpu-warning")
                .name("CPU Warning")
                .metricType(MetricType.CPU_USAGE)
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(75.0)
                .severity(AlertSeverity.WARNING)
                .message("CPU usage exceeded 75%")
                .build());

        addRule(AlertRule.builder()
                .id("memory-critical")
                .name("Memory Critical")
                .metricType(MetricType.MEMORY_USAGE)
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(95.0)
                .severity(AlertSeverity.CRITICAL)
                .message("Memory usage exceeded 95%")
                .build());

        addRule(AlertRule.builder()
                .id("temperature-critical")
                .name("Temperature Critical")
                .metricType(MetricType.TEMPERATURE)
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(80.0)
                .severity(AlertSeverity.CRITICAL)
                .message("Temperature exceeded safe threshold")
                .build());

        addRule(AlertRule.builder()
                .id("signal-weak")
                .name("Weak Signal")
                .metricType(MetricType.SIGNAL_STRENGTH)
                .operator(AlertRule.Operator.LESS_THAN)
                .threshold(-100.0)
                .severity(AlertSeverity.WARNING)
                .message("Signal strength below acceptable level")
                .build());
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
            return List.of();
        }

        List<AlertRule> triggeredRules = rules.values().stream()
                .filter(rule -> rule.getMetricType() == metric.getMetricType())
                .filter(AlertRule::isEnabled)
                .filter(rule -> evaluateRule(rule, metric.getValue()))
                .toList();

        triggeredRules.forEach(rule -> triggerAlert(rule, metric));
        
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

        // In production: send to notification service via RabbitMQ
        // rabbitTemplate.convertAndSend("alerts.exchange", "alert.triggered", alertEvent);
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
        return List.copyOf(rules.values());
    }

    public Optional<AlertRule> getRule(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }
}

