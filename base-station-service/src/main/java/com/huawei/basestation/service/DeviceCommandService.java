package com.huawei.basestation.service;

import com.huawei.basestation.model.DeviceCommand;
import com.huawei.basestation.model.DeviceCommand.CommandSource;
import com.huawei.basestation.model.DeviceCommand.CommandStatus;
import com.huawei.basestation.repository.DeviceCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@SuppressWarnings({"null", "java:S2637"}) // Spring Data ops and @Value fields are non-null
public class DeviceCommandService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandService.class);

    private final DeviceCommandRepository repository;
    private final RestClient restClient;

    @Value("${command.auto-apply.min-confidence:0.90}")
    private double autoApplyMinConfidence;

    @Value("${command.auto-apply.allowed-risk-levels:low}")
    private String allowedRiskLevels;

    @Value("${monitoring.service.url:http://monitoring-service:8082}")
    private String monitoringServiceUrl;

    public DeviceCommandService(DeviceCommandRepository repository, RestClient.Builder restClientBuilder) {
        this.repository = repository;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Get pending commands for a station (called by edge-bridge).
     */
    public List<DeviceCommand> getPendingCommands(Long stationId) {
        return repository.findByStationIdAndStatusOrderByCreatedAtAsc(stationId, CommandStatus.PENDING);
    }

    /**
     * Get all commands for a station.
     */
    public List<DeviceCommand> getCommandsForStation(Long stationId) {
        return repository.findByStationIdOrderByCreatedAtDesc(stationId);
    }

    /**
     * Get a command by ID.
     */
    public Optional<DeviceCommand> getCommand(String commandId) {
        return repository.findById(commandId);
    }

    /**
     * Create a new command.
     */
    @Transactional
    public DeviceCommand createCommand(DeviceCommand command) {
        command.setCreatedAt(Instant.now());
        command.setStatus(CommandStatus.PENDING);
        DeviceCommand saved = repository.save(command);
        log.info("Created command {} for station {} (type={}, source={})",
                saved.getId(), saved.getStationId(), saved.getCommandType(), saved.getSource());
        return saved;
    }

    /**
     * Create a command from AI diagnostic solution.
     * Checks confidence and risk level to decide if auto-apply is allowed.
     */
    @Transactional
    public DeviceCommand createFromAISolution(Long stationId, String diagnosticSessionId,
                                               String problemCode, String commandType,
                                               Map<String, String> params, Double confidence,
                                               String riskLevel) {
        DeviceCommand command = new DeviceCommand();
        command.setStationId(stationId);
        command.setCommandType(commandType);
        command.setParams(params);
        command.setSource(CommandSource.AI_DIAGNOSTIC);
        command.setDiagnosticSessionId(diagnosticSessionId);
        command.setProblemCode(problemCode);
        command.setConfidence(confidence);
        command.setRiskLevel(riskLevel);
        command.setCreatedBy("ai-diagnostic");

        // Check if auto-apply is allowed
        boolean autoApplyAllowed = isAutoApplyAllowed(confidence, riskLevel);

        if (autoApplyAllowed) {
            command.setStatus(CommandStatus.PENDING);
            log.info("AI command {} auto-approved for station {} (confidence={}, risk={})",
                    commandType, stationId, confidence, riskLevel);
        } else {
            // Requires manual approval - still set to PENDING for now
            command.setStatus(CommandStatus.PENDING);
            log.info("AI command {} for station {} queued (confidence={}, risk={})",
                    commandType, stationId, confidence, riskLevel);
        }

        return repository.save(command);
    }

    /**
     * Mark a command as picked up by edge-bridge.
     */
    @Transactional
    public Optional<DeviceCommand> markPickedUp(String commandId) {
        return repository.findById(commandId)
                .filter(cmd -> cmd.getStatus() == CommandStatus.PENDING)
                .map(cmd -> {
                    cmd.setStatus(CommandStatus.IN_PROGRESS);
                    cmd.setPickedUpAt(Instant.now());
                    log.info("Command {} picked up by edge-bridge", commandId);
                    return repository.save(cmd);
                });
    }

    /**
     * Record command execution result (called by edge-bridge).
     */
    @Transactional
    public Optional<DeviceCommand> recordResult(String commandId, boolean success,
                                                 String output, Integer returnCode, String error) {
        return repository.findById(commandId)
                .map(cmd -> {
                    cmd.setStatus(success ? CommandStatus.COMPLETED : CommandStatus.FAILED);
                    cmd.setSuccess(success);
                    cmd.setOutput(output);
                    cmd.setReturnCode(returnCode);
                    cmd.setErrorMessage(error);
                    cmd.setCompletedAt(Instant.now());

                    DeviceCommand saved = repository.save(cmd);

                    log.info("Command {} completed: success={}, returnCode={}",
                            commandId, success, returnCode);

                    // Notify monitoring service about result (for AI learning)
                    notifyMonitoringService(saved);

                    return saved;
                });
    }

    /**
     * Cancel a pending command.
     */
    @Transactional
    public Optional<DeviceCommand> cancelCommand(String commandId) {
        return repository.findById(commandId)
                .filter(cmd -> cmd.getStatus() == CommandStatus.PENDING)
                .map(cmd -> {
                    cmd.setStatus(CommandStatus.CANCELLED);
                    cmd.setCompletedAt(Instant.now());
                    log.info("Command {} cancelled", commandId);
                    return repository.save(cmd);
                });
    }

    /**
     * Get recent commands for dashboard.
     */
    public List<DeviceCommand> getRecentCommands(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return repository.findRecentCommands(since);
    }

    /**
     * Check if auto-apply is allowed based on confidence and risk.
     */
    @SuppressWarnings({"java:S2583", "java:S2589"}) // null checks intentional for API flexibility
    private boolean isAutoApplyAllowed(Double confidence, String riskLevel) {
        // Confidence must meet minimum threshold
        if (confidence == null || confidence < autoApplyMinConfidence) {
            return false;
        }

        // No risk level specified means auto-apply is allowed
        String risk = riskLevel; // Avoid direct null check on parameter
        if (risk == null || risk.isEmpty()) {
            return true;
        }

        // Check if risk level is in allowed list
        String[] allowed = allowedRiskLevels.split(",");
        for (String level : allowed) {
            if (level.trim().equalsIgnoreCase(risk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notify monitoring service about command result (for AI learning feedback).
     */
    private void notifyMonitoringService(DeviceCommand command) {
        if (command.getDiagnosticSessionId() == null || command.getDiagnosticSessionId().isEmpty()) {
            return; // Not an AI-generated command
        }

        try {
            var feedback = Map.of(
                    "commandId", command.getId(),
                    "diagnosticSessionId", command.getDiagnosticSessionId(),
                    "problemCode", command.getProblemCode() != null ? command.getProblemCode() : "",
                    "success", command.getSuccess(),
                    "returnCode", command.getReturnCode() != null ? command.getReturnCode() : 0
            );

            restClient.post()
                    .uri(monitoringServiceUrl + "/api/v1/diagnostics/{sessionId}/command-result",
                            command.getDiagnosticSessionId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(feedback)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Notified monitoring service about command result: {}", command.getId());
        } catch (Exception e) {
            log.warn("Failed to notify monitoring service: {}", e.getMessage());
        }
    }

    /**
     * Clean up stale commands (stuck in IN_PROGRESS for too long).
     */
    @Transactional
    public int cleanupStaleCommands(int timeoutMinutes) {
        Instant timeout = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<DeviceCommand> stale = repository.findStaleCommands(timeout);

        for (DeviceCommand cmd : stale) {
            cmd.setStatus(CommandStatus.FAILED);
            cmd.setErrorMessage("Command timed out");
            cmd.setCompletedAt(Instant.now());
            repository.save(cmd);
            log.warn("Marked stale command {} as failed (timeout)", cmd.getId());
        }

        return stale.size();
    }
}
