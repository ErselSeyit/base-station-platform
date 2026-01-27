package com.huawei.monitoring.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.huawei.common.dto.AlertEvent;
import com.huawei.monitoring.client.DiagnosticClient;
import com.huawei.monitoring.config.RabbitMQConfig;
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
@SuppressWarnings("null") // Builder pattern and stream operations guarantee non-null values
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();
    private final RabbitTemplate rabbitTemplate;
    private final DiagnosticClient diagnosticClient;

    @Autowired(required = false)
    public AlertingService(RabbitTemplate rabbitTemplate, DiagnosticClient diagnosticClient) {
        this.rabbitTemplate = rabbitTemplate; // Optional - will be null if RabbitMQ not configured
        this.diagnosticClient = diagnosticClient; // AI diagnostic service client
        // Initialize default rules - in production, these would be from DB/config
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        // Builder pattern guarantees non-null results, excessive null checks removed
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

        // Send alert event to notification service via RabbitMQ
        if (rabbitTemplate != null) {
            try {
                // Convert enums to strings for cross-service compatibility
                AlertEvent alertEvent = AlertEvent.builder()
                        .alertRuleId(rule.getId())
                        .alertRuleName(rule.getName())
                        .stationId(metric.getStationId())
                        .stationName(metric.getStationName())
                        .metricType(metric.getMetricType() != null ? metric.getMetricType().name() : null)
                        .metricValue(metric.getValue())
                        .threshold(rule.getThreshold())
                        .severity(rule.getSeverity() != null ? rule.getSeverity().name() : null)
                        .message(rule.getMessage())
                        .build();
                
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.ALERTS_EXCHANGE,
                        RabbitMQConfig.ALERT_TRIGGERED_ROUTING_KEY,
                        alertEvent
                );
                
                log.debug("Alert event published to RabbitMQ: ruleId={}, stationId={}",
                        rule.getId(), metric.getStationId());

                // Request AI diagnosis asynchronously (non-blocking)
                requestDiagnosis(alertEvent);
            } catch (AmqpException e) {
                log.error("Failed to publish alert event to RabbitMQ: ruleId={}, stationId={}",
                        rule.getId(), metric.getStationId(), e);
                // Continue execution even if RabbitMQ fails - alert is still logged
            }
        } else {
            log.debug("RabbitMQ not available, alert logged only: ruleId={}, stationId={}",
                    rule.getId(), metric.getStationId());
        }
    }

    /**
     * Request AI diagnosis for an alert asynchronously.
     * Logs the recommended action without blocking the alert flow.
     */
    private void requestDiagnosis(AlertEvent alert) {
        if (diagnosticClient == null || !diagnosticClient.isEnabled()) {
            return;
        }

        diagnosticClient.diagnoseAsync(alert)
                .thenAccept(diagnosis -> {
                    if (diagnosis.isActionable()) {
                        log.info("AI DIAGNOSIS for alert {}: action='{}', confidence={}%, risk={}",
                                alert.getAlertRuleId(),
                                diagnosis.getAction(),
                                Math.round(diagnosis.getConfidence() * 100),
                                diagnosis.getRiskLevel());
                        if (!diagnosis.getCommands().isEmpty()) {
                            log.info("  Recommended commands: {}", diagnosis.getCommands());
                        }
                        log.info("  Expected outcome: {}", diagnosis.getExpectedOutcome());
                    } else {
                        log.debug("No actionable diagnosis for alert {}: {}",
                                alert.getAlertRuleId(), diagnosis.getReasoning());
                    }
                })
                .exceptionally(ex -> {
                    log.debug("Diagnostic request failed for alert {}: {}",
                            alert.getAlertRuleId(), ex.getMessage());
                    return null;
                });
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
        rules.computeIfPresent(ruleId, (key, rule) -> rule.withEnabled(true));
    }

    public void disableRule(String ruleId) {
        rules.computeIfPresent(ruleId, (key, rule) -> rule.withEnabled(false));
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
