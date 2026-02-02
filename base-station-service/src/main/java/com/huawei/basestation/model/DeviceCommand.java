package com.huawei.basestation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a command to be executed on a device.
 * Commands can be created by AI diagnostics, operators, or automated systems.
 */
@Entity
@Table(name = "device_commands")
public class DeviceCommand {

    public enum CommandStatus {
        PENDING,      // Waiting to be picked up by edge-bridge
        IN_PROGRESS,  // Being executed
        COMPLETED,    // Successfully executed
        FAILED,       // Execution failed
        CANCELLED     // Cancelled before execution
    }

    public enum CommandSource {
        AI_DIAGNOSTIC,  // Created by AI diagnostic system
        OPERATOR,       // Created by human operator
        AUTOMATED,      // Created by automated rules
        SYSTEM          // System-generated
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private Long stationId;

    @Column(nullable = false)
    private String commandType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "command_params", joinColumns = @JoinColumn(name = "command_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    private Map<String, String> params = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status = CommandStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandSource source = CommandSource.SYSTEM;

    // Link to diagnostic session if created by AI
    private String diagnosticSessionId;

    // Link to original problem code
    private String problemCode;

    // AI confidence level (0.0 - 1.0)
    private Double confidence;

    // Risk level (low, medium, high)
    private String riskLevel;

    // Execution result
    private Boolean success;

    @Column(length = 4000)
    private String output;

    private Integer returnCode;

    @Column(length = 2000)
    private String errorMessage;

    // Timestamps
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant pickedUpAt;

    private Instant completedAt;

    // Created by (username or system)
    private String createdBy;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public CommandStatus getStatus() {
        return status;
    }

    public void setStatus(CommandStatus status) {
        this.status = status;
    }

    public CommandSource getSource() {
        return source;
    }

    public void setSource(CommandSource source) {
        this.source = source;
    }

    public String getDiagnosticSessionId() {
        return diagnosticSessionId;
    }

    public void setDiagnosticSessionId(String diagnosticSessionId) {
        this.diagnosticSessionId = diagnosticSessionId;
    }

    public String getProblemCode() {
        return problemCode;
    }

    public void setProblemCode(String problemCode) {
        this.problemCode = problemCode;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Integer getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(Integer returnCode) {
        this.returnCode = returnCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(Instant pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
