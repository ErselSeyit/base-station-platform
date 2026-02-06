package com.huawei.monitoring.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * SON (Self-Organizing Network) recommendation document.
 *
 * Stores AI-generated optimization recommendations that require
 * operator approval before execution.
 */
@Document(collection = "son_recommendations")
@CompoundIndex(name = "idx_station_status", def = "{'stationId': 1, 'status': 1}")
@CompoundIndex(name = "idx_function_status", def = "{'functionType': 1, 'status': 1}")
public class SONRecommendation {

    @Id
    private String id;

    @Indexed
    private Long stationId;

    @Indexed
    private SONFunction functionType;

    private String actionType;

    private String actionValue;

    private String description;

    private Double expectedImprovement;

    private Double confidence;

    @Indexed
    private SONStatus status = SONStatus.PENDING;

    private Boolean autoExecutable = false;

    private Boolean approvalRequired = true;

    private String rollbackAction;

    private String approvedBy;

    private LocalDateTime approvedAt;

    private String rejectedBy;

    private LocalDateTime rejectedAt;

    private String rejectionReason;

    private LocalDateTime executedAt;

    private String executionResult;

    private Boolean executionSuccess;

    private LocalDateTime rolledBackAt;

    private String rolledBackBy;

    private String rollbackReason;

    @Indexed
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime expiresAt;

    // Constructors
    public SONRecommendation() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public SONRecommendation(Long stationId, SONFunction functionType, String actionType) {
        this();
        this.stationId = stationId;
        this.functionType = functionType;
        this.actionType = actionType;
    }

    // SON Function Types
    public enum SONFunction {
        MLB("Mobility Load Balancing"),
        MRO("Mobility Robustness Optimization"),
        CCO("Coverage & Capacity Optimization"),
        ES("Energy Saving"),
        ANR("Automatic Neighbor Relation"),
        RAO("Random Access Optimization"),
        ICIC("Inter-Cell Interference Coordination");

        private final String displayName;

        SONFunction(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // SON Recommendation Status
    public enum SONStatus {
        PENDING,           // Awaiting review
        APPROVED,          // Approved, awaiting execution
        REJECTED,          // Rejected by operator
        EXECUTING,         // Currently being executed
        EXECUTED,          // Successfully executed
        FAILED,            // Execution failed
        ROLLED_BACK,       // Changes reverted
        EXPIRED            // Recommendation no longer valid
    }

    // Getters and Setters
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

    public SONFunction getFunctionType() {
        return functionType;
    }

    public void setFunctionType(SONFunction functionType) {
        this.functionType = functionType;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionValue() {
        return actionValue;
    }

    public void setActionValue(String actionValue) {
        this.actionValue = actionValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getExpectedImprovement() {
        return expectedImprovement;
    }

    public void setExpectedImprovement(Double expectedImprovement) {
        this.expectedImprovement = expectedImprovement;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public SONStatus getStatus() {
        return status;
    }

    public void setStatus(SONStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Boolean getAutoExecutable() {
        return autoExecutable;
    }

    public void setAutoExecutable(Boolean autoExecutable) {
        this.autoExecutable = autoExecutable;
    }

    public Boolean getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(Boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getRollbackAction() {
        return rollbackAction;
    }

    public void setRollbackAction(String rollbackAction) {
        this.rollbackAction = rollbackAction;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public String getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }

    public Boolean getExecutionSuccess() {
        return executionSuccess;
    }

    public void setExecutionSuccess(Boolean executionSuccess) {
        this.executionSuccess = executionSuccess;
    }

    public LocalDateTime getRolledBackAt() {
        return rolledBackAt;
    }

    public void setRolledBackAt(LocalDateTime rolledBackAt) {
        this.rolledBackAt = rolledBackAt;
    }

    public String getRolledBackBy() {
        return rolledBackBy;
    }

    public void setRolledBackBy(String rolledBackBy) {
        this.rolledBackBy = rolledBackBy;
    }

    public String getRollbackReason() {
        return rollbackReason;
    }

    public void setRollbackReason(String rollbackReason) {
        this.rollbackReason = rollbackReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    // Utility methods
    public boolean isPending() {
        return status == SONStatus.PENDING;
    }

    public boolean isApproved() {
        return status == SONStatus.APPROVED;
    }

    public boolean isExecuted() {
        return status == SONStatus.EXECUTED;
    }

    public boolean canBeApproved() {
        return status == SONStatus.PENDING;
    }

    public boolean canBeRolledBack() {
        return status == SONStatus.EXECUTED && rollbackAction != null;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    // State transition methods
    public void approve(String username) {
        this.status = SONStatus.APPROVED;
        this.approvedBy = username;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void reject(String username, String reason) {
        this.status = SONStatus.REJECTED;
        this.rejectedBy = username;
        this.rejectedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void markExecuting() {
        this.status = SONStatus.EXECUTING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markExecuted(boolean success, String result) {
        this.status = success ? SONStatus.EXECUTED : SONStatus.FAILED;
        this.executionSuccess = success;
        this.executionResult = result;
        this.executedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void rollback(String username, String reason) {
        this.status = SONStatus.ROLLED_BACK;
        this.rolledBackBy = username;
        this.rolledBackAt = LocalDateTime.now();
        this.rollbackReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void markExpired() {
        this.status = SONStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }
}
