package com.huawei.monitoring.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.huawei.common.constants.DiagnosticConstants;
import com.huawei.common.dto.AlertEvent;
import com.huawei.monitoring.client.DiagnosticClient;
import com.huawei.monitoring.config.RabbitMQConfig;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.model.AlertSeverity;
import com.huawei.monitoring.model.DiagnosticSession;
import com.huawei.monitoring.model.DiagnosticStatus;
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

    // Track active alerts to prevent duplicates: key = "stationId-ruleId"
    private final Map<String, ActiveAlert> activeAlerts = new ConcurrentHashMap<>();

    /**
     * Represents an active (unresolved) alert.
     */
    private record ActiveAlert(
            String ruleId,
            Long stationId,
            Instant triggeredAt,
            Double lastValue
    ) {}
    @Nullable private final RabbitTemplate rabbitTemplate;
    @Nullable private final DiagnosticClient diagnosticClient;
    @Nullable private final DiagnosticSessionService diagnosticSessionService;
    private final com.huawei.monitoring.config.AlertThresholdConfig thresholdConfig;

    @Autowired(required = false)
    public AlertingService(@Nullable RabbitTemplate rabbitTemplate,
                          @Nullable DiagnosticClient diagnosticClient,
                          @Nullable DiagnosticSessionService diagnosticSessionService,
                          com.huawei.monitoring.config.AlertThresholdConfig thresholdConfig) {
        this.rabbitTemplate = rabbitTemplate; // Optional - will be null if RabbitMQ not configured
        this.diagnosticClient = diagnosticClient; // AI diagnostic service client
        this.diagnosticSessionService = diagnosticSessionService; // Session tracking for learning
        this.thresholdConfig = thresholdConfig;
        // Initialize default rules from externalized config
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("cpu-critical")
                        .name("CPU Critical")
                        .metricType(MetricType.CPU_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getCpuCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("CPU usage exceeded " + (int) thresholdConfig.getCpuCritical() + "%")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("cpu-warning")
                        .name("CPU Warning")
                        .metricType(MetricType.CPU_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getCpuWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("CPU usage exceeded " + (int) thresholdConfig.getCpuWarning() + "%")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("memory-critical")
                        .name("Memory Critical")
                        .metricType(MetricType.MEMORY_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getMemoryCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Memory usage exceeded " + (int) thresholdConfig.getMemoryCritical() + "%")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("temperature-critical")
                        .name("Temperature Critical")
                        .metricType(MetricType.TEMPERATURE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getTemperatureCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Temperature exceeded safe threshold (" + (int) thresholdConfig.getTemperatureCritical() + "°C)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("signal-weak")
                        .name("Weak Signal")
                        .metricType(MetricType.SIGNAL_STRENGTH)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getSignalWeak())
                        .severity(AlertSeverity.WARNING)
                        .message("Signal strength below acceptable level (" + (int) thresholdConfig.getSignalWeak() + " dBm)")
                        .build()));
    }

    /**
     * Evaluates a metric against all applicable rules with deduplication.
     * Only triggers new alerts for conditions that weren't already active.
     * Automatically resolves alerts when conditions return to normal.
     *
     * @param metric the metric data to evaluate (must not be null)
     * @return list of triggered alert rules (empty list if none triggered, never null)
     */
    public List<AlertRule> evaluateMetric(MetricDataDTO metric) {
        // Return empty list for invalid input - follows "return empty, not null" pattern
        MetricType metricType = metric.getMetricType();
        Double value = metric.getValue();
        Long stationId = metric.getStationId();
        if (metricType == null || value == null || stationId == null) {
            return Objects.requireNonNull(List.of());
        }

        // Find all rules that match this metric type
        List<AlertRule> applicableRules = Objects.requireNonNull(rules.values().stream()
                .filter(rule -> Objects.requireNonNull(rule).getMetricType() == metricType)
                .filter(AlertRule::isEnabled)
                .toList());

        // Check for resolved alerts (condition no longer met)
        Set<String> resolvedAlertKeys = activeAlerts.keySet().stream()
                .filter(key -> key.startsWith(stationId + "-"))
                .filter(key -> {
                    ActiveAlert alert = activeAlerts.get(key);
                    return alert != null && applicableRules.stream()
                            .filter(rule -> rule.getId().equals(alert.ruleId()))
                            .anyMatch(rule -> !evaluateRule(Objects.requireNonNull(rule), value));
                })
                .collect(Collectors.toSet());

        // Resolve cleared alerts
        resolvedAlertKeys.forEach(key -> resolveAlert(Objects.requireNonNull(key), metric));

        // Find newly triggered rules (not already active)
        List<AlertRule> newlyTriggeredRules = Objects.requireNonNull(applicableRules.stream()
                .filter(rule -> evaluateRule(Objects.requireNonNull(rule), value))
                .filter(rule -> !isAlertActive(stationId, Objects.requireNonNull(rule.getId())))
                .toList());

        // Trigger only new alerts
        newlyTriggeredRules.forEach(rule -> triggerAlert(Objects.requireNonNull(rule), metric));

        return newlyTriggeredRules;
    }

    /**
     * Generates a unique key for an alert based on station and rule.
     */
    private String alertKey(Long stationId, String ruleId) {
        return Objects.requireNonNull(stationId + "-" + ruleId);
    }

    /**
     * Checks if an alert is already active for this station and rule.
     */
    private boolean isAlertActive(Long stationId, String ruleId) {
        return activeAlerts.containsKey(Objects.requireNonNull(alertKey(stationId, ruleId)));
    }

    /**
     * Resolves an active alert when the condition returns to normal.
     */
    private void resolveAlert(String alertKey, MetricDataDTO metric) {
        ActiveAlert resolved = activeAlerts.remove(alertKey);
        if (resolved != null) {
            AlertRule rule = rules.get(resolved.ruleId());
            String ruleName = rule != null ? rule.getName() : resolved.ruleId();
            log.info("RESOLVED [{}] Alert cleared for station {} - {} (value now: {}, was: {})",
                    ruleName,
                    resolved.stationId(),
                    rule != null ? rule.getMessage() : "condition normalized",
                    metric.getValue(),
                    resolved.lastValue());
        }
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
        Long stationId = metric.getStationId();
        if (stationId == null) {
            return;
        }

        // Register this alert as active
        String key = alertKey(stationId, Objects.requireNonNull(rule.getId()));
        activeAlerts.put(Objects.requireNonNull(key), new ActiveAlert(
                Objects.requireNonNull(rule.getId()),
                stationId,
                Objects.requireNonNull(Instant.now()),
                Objects.requireNonNull(metric.getValue())
        ));

        log.warn("ALERT [{}] {}: {} for station {} (value: {}, threshold: {})",
                rule.getSeverity(),
                rule.getName(),
                rule.getMessage(),
                stationId,
                metric.getValue(),
                rule.getThreshold());

        // Capture nullable field in local variable to satisfy null safety checker
        RabbitTemplate template = this.rabbitTemplate;

        // Send alert event to notification service via RabbitMQ
        if (template != null) {
            try {
                // Convert enums to strings for cross-service compatibility
                // Use Optional.map() instead of ternary null checks
                AlertEvent alertEvent = Objects.requireNonNull(AlertEvent.builder()
                        .alertRuleId(rule.getId())
                        .alertRuleName(rule.getName())
                        .stationId(metric.getStationId())
                        .stationName(metric.getStationName())
                        .metricType(Optional.ofNullable(metric.getMetricType())
                                .map(MetricType::name)
                                .orElse(null))
                        .metricValue(metric.getValue())
                        .threshold(rule.getThreshold())
                        .severity(Optional.ofNullable(rule.getSeverity())
                                .map(AlertSeverity::name)
                                .orElse(null))
                        .message(rule.getMessage())
                        .build());

                template.convertAndSend(
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
     * Creates or reuses a diagnostic session for learning and logs the recommended action.
     * Skips diagnosis if an existing session is already being processed (DIAGNOSED or later).
     */
    private void requestDiagnosis(AlertEvent alert) {
        // Capture nullable fields in local variables to satisfy null safety checker
        DiagnosticClient client = this.diagnosticClient;
        DiagnosticSessionService sessionService = this.diagnosticSessionService;

        if (client == null || !client.isEnabled()) {
            return;
        }

        // Create or reuse session (deduplication handled in createSession)
        String problemId = generateProblemId(alert);
        DiagnosticSession session = createDiagnosticSession(sessionService, alert, problemId);

        if (session == null) {
            return;
        }

        // Use the actual session's problem ID (may differ if session was reused)
        String actualProblemId = session.getProblemId();

        // Skip diagnosis if session is already past DETECTED state
        if (session.getStatus() != DiagnosticStatus.DETECTED) {
            log.debug("Skipping diagnosis for session {} - already in {} state",
                    actualProblemId, session.getStatus());
            return;
        }

        client.diagnoseAsync(alert, actualProblemId)
                .thenAccept(diagnosis -> {
                    try {
                        handleDiagnosisResult(
                                Objects.requireNonNull(diagnosis), alert, sessionService, actualProblemId);
                    } catch (Exception e) {
                        log.error("Error in handleDiagnosisResult for {}: {}", actualProblemId, e.getMessage(), e);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Diagnostic request failed for alert {}: {}",
                            alert.getAlertRuleId(), ex.getMessage(), ex);
                    return null;
                });
    }

    private String generateProblemId(AlertEvent alert) {
        return DiagnosticConstants.PROBLEM_ID_PREFIX + System.currentTimeMillis() + "-" + alert.getAlertRuleId();
    }

    @Nullable
    private DiagnosticSession createDiagnosticSession(@Nullable DiagnosticSessionService sessionService,
                                                       AlertEvent alert, String problemId) {
        if (sessionService == null) {
            return null;
        }
        try {
            DiagnosticSession session = sessionService.createSession(alert, problemId);
            log.debug("Created/reused diagnostic session {} for problem {}", session.getId(), problemId);
            return session;
        } catch (Exception e) {
            log.warn("Failed to create diagnostic session: {}", e.getMessage());
            return null;
        }
    }

    private void handleDiagnosisResult(com.huawei.common.dto.DiagnosticResponse diagnosis,
                                        AlertEvent alert,
                                        @Nullable DiagnosticSessionService sessionService,
                                        String problemId) {
        if (sessionService != null && diagnosis.isActionable()) {
            sessionService.recordDiagnosis(problemId, diagnosis);
        }

        if (diagnosis.isActionable()) {
            logActionableDiagnosis(diagnosis, alert);
        } else {
            log.debug("No actionable diagnosis for alert {}: {}",
                    alert.getAlertRuleId(), diagnosis.getReasoning());
        }
    }

    private void logActionableDiagnosis(com.huawei.common.dto.DiagnosticResponse diagnosis, AlertEvent alert) {
        Double confidence = diagnosis.getConfidence();
        log.info("AI DIAGNOSIS for alert {}: action='{}', confidence={}%, risk={}",
                alert.getAlertRuleId(),
                diagnosis.getAction(),
                confidence != null ? Math.round(confidence * 100) : 0,
                diagnosis.getRiskLevel());

        List<String> commands = diagnosis.getCommands();
        if (commands != null && !commands.isEmpty()) {
            log.info("  Recommended commands: {}", commands);
        }
        log.info("  Expected outcome: {}", diagnosis.getExpectedOutcome());
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

    /**
     * Returns all alert rules.
     * Contract: Always returns a list (empty if none), never null.
     */
    public List<AlertRule> getAllRules() {
        return Objects.requireNonNull(List.copyOf(rules.values()));
    }

    /**
     * Returns a rule by ID wrapped in Optional.
     * Contract: Always returns Optional (empty if not found), never null.
     */
    public Optional<AlertRule> getRule(String ruleId) {
        return Objects.requireNonNull(Optional.ofNullable(rules.get(ruleId)));
    }
}
