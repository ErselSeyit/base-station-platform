package com.huawei.monitoring.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a diagnostic session from problem detection through resolution.
 * Used by the AI learning system to track solutions and their effectiveness.
 */
@Document(collection = "diagnostic_sessions")
@CompoundIndex(name = "idx_station_status", def = "{'stationId': 1, 'status': 1}")
@CompoundIndex(name = "idx_problem_code", def = "{'problemCode': 1, 'status': 1}")
public class DiagnosticSession {

    @Id
    private String id;

    /**
     * Version field for optimistic locking to prevent concurrent modification.
     */
    @Version
    private Long version;

    /**
     * External problem ID from the AI diagnostic service.
     */
    @Indexed(unique = true)
    private String problemId;

    @Indexed
    private Long stationId;

    private String stationName;

    @Indexed
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    /**
     * Problem category: hardware, network, software, power, security
     */
    private String category;

    /**
     * Severity: low, medium, high, critical
     */
    private String severity;

    /**
     * Problem code: CPU_OVERHEAT, MEMORY_PRESSURE, etc.
     */
    @Indexed
    private String problemCode;

    /**
     * Human-readable problem description.
     */
    private String message;

    /**
     * Metrics snapshot at the time of problem detection.
     */
    private Map<String, Object> metricsSnapshot;

    /**
     * Current status of the diagnostic session.
     */
    @Indexed
    private DiagnosticStatus status;

    /**
     * AI-generated solution.
     */
    private AISolution aiSolution;

    /**
     * Operator feedback on the solution.
     */
    private SolutionFeedback feedback;

    /**
     * Whether the solution was auto-applied due to high confidence.
     */
    private boolean autoApplied;

    public DiagnosticSession() {
        this.createdAt = LocalDateTime.now();
        this.status = DiagnosticStatus.DETECTED;
    }

    public DiagnosticSession(String problemId, Long stationId, String stationName,
                             String category, String severity, String problemCode, String message) {
        this();
        this.problemId = problemId;
        this.stationId = stationId;
        this.stationName = stationName;
        this.category = category;
        this.severity = severity;
        this.problemCode = problemCode;
        this.message = message;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(String problemId) {
        this.problemId = problemId;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getProblemCode() {
        return problemCode;
    }

    public void setProblemCode(String problemCode) {
        this.problemCode = problemCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getMetricsSnapshot() {
        return metricsSnapshot;
    }

    public void setMetricsSnapshot(Map<String, Object> metricsSnapshot) {
        this.metricsSnapshot = metricsSnapshot;
    }

    public DiagnosticStatus getStatus() {
        return status;
    }

    public void setStatus(DiagnosticStatus status) {
        this.status = status;
    }

    public AISolution getAiSolution() {
        return aiSolution;
    }

    public void setAiSolution(AISolution aiSolution) {
        this.aiSolution = aiSolution;
    }

    public SolutionFeedback getFeedback() {
        return feedback;
    }

    public void setFeedback(SolutionFeedback feedback) {
        this.feedback = feedback;
    }

    public boolean isAutoApplied() {
        return autoApplied;
    }

    public void setAutoApplied(boolean autoApplied) {
        this.autoApplied = autoApplied;
    }

    /**
     * Marks the session as diagnosed with an AI solution.
     */
    public void markDiagnosed(AISolution solution) {
        this.aiSolution = solution;
        this.status = DiagnosticStatus.DIAGNOSED;
    }

    /**
     * Marks the solution as applied.
     */
    public void markApplied() {
        this.status = DiagnosticStatus.APPLIED;
    }

    /**
     * Marks the session as pending confirmation.
     */
    public void markPendingConfirmation() {
        this.status = DiagnosticStatus.PENDING_CONFIRMATION;
    }

    /**
     * Marks the session as resolved with feedback.
     */
    public void markResolved(SolutionFeedback feedback) {
        this.feedback = feedback;
        this.status = DiagnosticStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Marks the session as failed with feedback.
     */
    public void markFailed(SolutionFeedback feedback) {
        this.feedback = feedback;
        this.status = DiagnosticStatus.FAILED;
        this.resolvedAt = LocalDateTime.now();
    }
}
