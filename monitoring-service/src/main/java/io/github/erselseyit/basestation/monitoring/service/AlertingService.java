package io.github.erselseyit.basestation.monitoring.service;

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

import io.github.erselseyit.basestation.common.constants.DiagnosticConstants;
import io.github.erselseyit.basestation.common.constants.MessagingConstants;
import io.github.erselseyit.basestation.common.dto.AlertEvent;
import io.github.erselseyit.basestation.common.dto.DiagnosticResolutionEvent;
import io.github.erselseyit.basestation.monitoring.client.DiagnosticClient;
import io.github.erselseyit.basestation.monitoring.config.RabbitMQConfig;
import io.github.erselseyit.basestation.monitoring.dto.MetricDataDTO;
import io.github.erselseyit.basestation.monitoring.model.AlertRule;
import io.github.erselseyit.basestation.monitoring.model.AlertSeverity;
import io.github.erselseyit.basestation.monitoring.model.DiagnosticSession;
import io.github.erselseyit.basestation.monitoring.model.DiagnosticStatus;
import io.github.erselseyit.basestation.monitoring.model.MetricType;

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

    // Unit suffix constant (S1192 - avoid duplicated literals)
    private static final String UNIT_DBM = " dBm)";

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();

    // Track active alerts to prevent duplicates: key = "stationId-ruleId"
    private final Map<String, ActiveAlert> activeAlerts = new ConcurrentHashMap<>();

    // Track in-flight diagnoses to prevent duplicate async calls: key = problemId
    private final Set<String> inFlightDiagnoses = ConcurrentHashMap.newKeySet();

    /**
     * Represents an active (unresolved) alert.
     */
    private record ActiveAlert(
            String ruleId,
            Long stationId,
            Instant triggeredAt,
            Double lastValue,
            String problemId
    ) {}
    @Nullable private final RabbitTemplate rabbitTemplate;
    @Nullable private final DiagnosticClient diagnosticClient;
    @Nullable private final DiagnosticSessionService diagnosticSessionService;
    private final io.github.erselseyit.basestation.monitoring.config.AlertThresholdConfig thresholdConfig;

    @Autowired(required = false)
    public AlertingService(@Nullable RabbitTemplate rabbitTemplate,
                          @Nullable DiagnosticClient diagnosticClient,
                          @Nullable DiagnosticSessionService diagnosticSessionService,
                          io.github.erselseyit.basestation.monitoring.config.AlertThresholdConfig thresholdConfig) {
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
                        .message("Signal strength below acceptable level (" + (int) thresholdConfig.getSignalWeak() + UNIT_DBM)
                        .build()));

        // Memory warning
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("memory-warning")
                        .name("Memory Warning")
                        .metricType(MetricType.MEMORY_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getMemoryWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Memory usage exceeded " + (int) thresholdConfig.getMemoryWarning() + "%")
                        .build()));

        // Temperature warning
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("temperature-warning")
                        .name("Temperature Warning")
                        .metricType(MetricType.TEMPERATURE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getTemperatureWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Temperature above warning threshold (" + (int) thresholdConfig.getTemperatureWarning() + "°C)")
                        .build()));

        // BLER (Block Error Rate) thresholds
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("bler-critical")
                        .name("BLER Critical")
                        .metricType(MetricType.INITIAL_BLER)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getBlerCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Block error rate critical (" + (int) thresholdConfig.getBlerCritical() + "%)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("bler-warning")
                        .name("BLER Warning")
                        .metricType(MetricType.INITIAL_BLER)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getBlerWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Block error rate elevated (" + (int) thresholdConfig.getBlerWarning() + "%)")
                        .build()));

        // Battery thresholds - alert when SOC is LOW
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("battery-low")
                        .name("Battery Low")
                        .metricType(MetricType.BATTERY_SOC)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getBatteryLow())
                        .severity(AlertSeverity.WARNING)
                        .message("Battery state of charge low (" + (int) thresholdConfig.getBatteryLow() + "%)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("battery-critical")
                        .name("Battery Critical")
                        .metricType(MetricType.BATTERY_SOC)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getBatteryCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Battery state of charge critical (" + (int) thresholdConfig.getBatteryCritical() + "%)")
                        .build()));

        // Latency thresholds
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("latency-warning")
                        .name("Latency Warning")
                        .metricType(MetricType.LATENCY_PING)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getLatencyWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Network latency elevated (" + (int) thresholdConfig.getLatencyWarning() + "ms)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("latency-critical")
                        .name("Latency Critical")
                        .metricType(MetricType.LATENCY_PING)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getLatencyCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Network latency critical (" + (int) thresholdConfig.getLatencyCritical() + "ms)")
                        .build()));

        // Throughput thresholds - alert when throughput is LOW
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("throughput-low")
                        .name("Throughput Low")
                        .metricType(MetricType.DATA_THROUGHPUT)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getThroughputLow())
                        .severity(AlertSeverity.WARNING)
                        .message("Data throughput below threshold (" + (int) thresholdConfig.getThroughputLow() + " Mbps)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("throughput-critical")
                        .name("Throughput Critical")
                        .metricType(MetricType.DATA_THROUGHPUT)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getThroughputCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Data throughput critically low (" + (int) thresholdConfig.getThroughputCritical() + " Mbps)")
                        .build()));

        // Power consumption thresholds
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("power-high")
                        .name("Power High")
                        .metricType(MetricType.POWER_CONSUMPTION)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getPowerHigh())
                        .severity(AlertSeverity.WARNING)
                        .message("Power consumption high (" + (int) thresholdConfig.getPowerHigh() + "W)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("power-critical")
                        .name("Power Critical")
                        .metricType(MetricType.POWER_CONSUMPTION)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getPowerCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Power consumption critical (" + (int) thresholdConfig.getPowerCritical() + "W)")
                        .build()));

        // Handover success rate - alert when below threshold
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("handover-warning")
                        .name("Handover Warning")
                        .metricType(MetricType.HANDOVER_SUCCESS_RATE)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getHandoverWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Handover success rate below threshold (" + (int) thresholdConfig.getHandoverWarning() + "%)")
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("handover-critical")
                        .name("Handover Critical")
                        .metricType(MetricType.HANDOVER_SUCCESS_RATE)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(thresholdConfig.getHandoverCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Handover success rate critical (" + (int) thresholdConfig.getHandoverCritical() + "%)")
                        .build()));

        // Interference level - alert when above threshold (dBm, higher is worse)
        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("interference-warning")
                        .name("Interference Warning")
                        .metricType(MetricType.INTERFERENCE_LEVEL)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getInterferenceWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Interference level elevated (" + (int) thresholdConfig.getInterferenceWarning() + UNIT_DBM)
                        .build()));

        addRule(Objects.requireNonNull(AlertRule.builder()
                        .id("interference-critical")
                        .name("Interference Critical")
                        .metricType(MetricType.INTERFERENCE_LEVEL)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(thresholdConfig.getInterferenceCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Interference level critical (" + (int) thresholdConfig.getInterferenceCritical() + UNIT_DBM)
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
     * Publishes a resolution event to notification-service so the corresponding
     * notification is marked as RESOLVED in the database.
     */
    private void resolveAlert(String alertKey, MetricDataDTO metric) {
        ActiveAlert resolved = activeAlerts.remove(alertKey);
        if (resolved == null) {
            return;
        }

        AlertRule rule = rules.get(resolved.ruleId());
        String ruleName = rule != null ? rule.getName() : resolved.ruleId();
        log.info("RESOLVED [{}] Alert cleared for station {} - {} (value now: {}, was: {})",
                ruleName,
                resolved.stationId(),
                rule != null ? rule.getMessage() : "condition normalized",
                metric.getValue(),
                resolved.lastValue());

        // Publish resolution event so notification-service marks the notification as resolved
        RabbitTemplate template = this.rabbitTemplate;
        if (template != null && resolved.problemId() != null) {
            try {
                DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                        "alert-auto-resolve",
                        resolved.problemId(),
                        resolved.stationId(),
                        resolved.ruleId(),
                        "alert-auto-resolve");
                template.convertAndSend(
                        RabbitMQConfig.ALERTS_EXCHANGE,
                        MessagingConstants.DIAGNOSTIC_RESOLVED_ROUTING_KEY,
                        event);
                log.info("Published alert resolution event for problemId={}, station={}",
                        resolved.problemId(), resolved.stationId());
            } catch (AmqpException e) {
                log.warn("Failed to publish alert resolution event for problemId={}: {}",
                        resolved.problemId(), e.getMessage());
            }
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

        // Generate problemId early so it can be stored in ActiveAlert for resolution
        String problemId = generateProblemId(rule.getId());

        // Register this alert as active (with problemId for resolution tracking)
        String key = alertKey(stationId, Objects.requireNonNull(rule.getId()));
        activeAlerts.put(Objects.requireNonNull(key), new ActiveAlert(
                Objects.requireNonNull(rule.getId()),
                stationId,
                Objects.requireNonNull(Instant.now()),
                Objects.requireNonNull(metric.getValue()),
                problemId
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
                        .problemId(problemId)
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

        // Use problemId from alert (already generated in triggerAlert)
        String problemId = alert.getProblemId();
        if (problemId == null || problemId.isBlank()) {
            log.warn("Alert has no problemId, skipping diagnosis: {}", alert.getAlertRuleId());
            return;
        }

        // Create or reuse session (deduplication handled in createSession)
        Optional<DiagnosticSession> created = createDiagnosticSession(sessionService, alert, problemId);
        if (created.isEmpty()) {
            return;
        }
        DiagnosticSession session = created.get();

        // Use the actual session's problem ID (may differ if session was reused)
        String actualProblemId = session.getProblemId();

        // Skip diagnosis if session is already past DETECTED state
        if (session.getStatus() != DiagnosticStatus.DETECTED) {
            log.debug("Skipping diagnosis for session {} - already in {} state",
                    actualProblemId, session.getStatus());
            return;
        }

        // Prevent duplicate in-flight async diagnoses for the same problem
        if (!inFlightDiagnoses.add(actualProblemId)) {
            log.debug("Skipping duplicate diagnosis for {} — already in flight", actualProblemId);
            return;
        }

        log.info("Initiating AI diagnosis for session {} (alert={})", actualProblemId, alert.getAlertRuleId());

        client.diagnoseAsync(alert, actualProblemId)
                .thenAccept(diagnosis -> {
                    try {
                        boolean actionable = diagnosis != null && diagnosis.isActionable();
                        log.info("Diagnosis callback for session {}: actionable={}, action='{}', confidence={}",
                                actualProblemId, actionable,
                                diagnosis != null ? diagnosis.getAction() : null,
                                diagnosis != null ? diagnosis.getConfidence() : null);
                        handleDiagnosisResult(
                                Objects.requireNonNull(diagnosis), alert, sessionService, actualProblemId);
                    } catch (Exception e) {
                        log.error("Error in handleDiagnosisResult for {}: {}", actualProblemId, e.getMessage(), e);
                        markSessionFailed(sessionService, actualProblemId, "Handler error: " + e.getMessage());
                    } finally {
                        inFlightDiagnoses.remove(actualProblemId);
                    }
                })
                .exceptionally(ex -> {
                    try {
                        log.error("Diagnostic request failed for alert {} (session={}): {}",
                                alert.getAlertRuleId(), actualProblemId, ex.getMessage(), ex);
                        // Mark session as FAILED to prevent it being stuck in DETECTED state
                        markSessionFailed(sessionService, actualProblemId, "Diagnosis failed: " + ex.getMessage());
                    } finally {
                        inFlightDiagnoses.remove(actualProblemId);
                    }
                    return null;
                });
    }

    /**
     * Marks a diagnostic session as failed when the AI service fails to respond.
     */
    private void markSessionFailed(@Nullable DiagnosticSessionService sessionService,
                                    String problemId, String reason) {
        if (sessionService == null) {
            return;
        }
        try {
            sessionService.markSessionError(problemId, reason);
            log.info("Marked session {} as FAILED due to: {}", problemId, reason);
        } catch (Exception e) {
            log.warn("Failed to mark session {} as failed: {}", problemId, e.getMessage(), e);
        }
    }

    /**
     * Generates a unique problem ID for linking alerts to diagnostic sessions.
     * Format: PROB-{timestamp}-{ruleId}
     */
    private String generateProblemId(String ruleId) {
        return DiagnosticConstants.PROBLEM_ID_PREFIX + System.currentTimeMillis() + "-" + ruleId;
    }

    private Optional<DiagnosticSession> createDiagnosticSession(@Nullable DiagnosticSessionService sessionService,
                                                                 AlertEvent alert, String problemId) {
        if (sessionService == null) {
            return Optional.empty();
        }
        try {
            DiagnosticSession session = sessionService.createSession(alert, problemId);
            log.debug("Created/reused diagnostic session {} for problem {}", session.getId(), problemId);
            return Optional.of(session);
        } catch (Exception e) {
            // Diagnosis is best-effort: alerting must continue without a session.
            log.warn("Failed to create diagnostic session for problem {}", problemId, e);
            return Optional.empty();
        }
    }

    private void handleDiagnosisResult(io.github.erselseyit.basestation.common.dto.DiagnosticResponse diagnosis,
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

    private void logActionableDiagnosis(io.github.erselseyit.basestation.common.dto.DiagnosticResponse diagnosis, AlertEvent alert) {
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
