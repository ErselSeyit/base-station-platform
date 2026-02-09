package com.huawei.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.Nullable;

/**
 * Event DTO for alert notifications sent via RabbitMQ.
 * 
 * Shared across monitoring-service and notification-service.
 * Uses String types for metricType and severity to ensure compatibility
 * across services regardless of enum definitions.
 */
public class AlertEvent {
    
    @JsonProperty("alertRuleId")
    private String alertRuleId;
    
    @JsonProperty("alertRuleName")
    private String alertRuleName;
    
    @JsonProperty("stationId")
    private Long stationId;
    
    @JsonProperty("stationName")
    private String stationName;
    
    @JsonProperty("metricType")
    private String metricType;
    
    @JsonProperty("metricValue")
    private Double metricValue;
    
    @JsonProperty("threshold")
    private Double threshold;
    
    @JsonProperty("severity")
    private String severity;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    /**
     * Unique problem ID for correlating alerts with diagnostic sessions.
     * Format: PROB-{stationId}-{metricType}-{timestamp}
     */
    @JsonProperty("problemId")
    private String problemId;

    public AlertEvent() {
        this.timestamp = LocalDateTime.now();
    }

    private AlertEvent(Builder builder) {
        this.alertRuleId = builder.alertRuleId;
        this.alertRuleName = builder.alertRuleName;
        this.stationId = builder.stationId;
        this.stationName = builder.stationName;
        this.metricType = builder.metricType;
        this.metricValue = builder.metricValue;
        this.threshold = builder.threshold;
        this.severity = builder.severity;
        this.message = builder.message;
        this.problemId = builder.problemId;
        this.timestamp = LocalDateTime.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        @Nullable private String alertRuleId;
        @Nullable private String alertRuleName;
        @Nullable private Long stationId;
        @Nullable private String stationName;
        @Nullable private String metricType;
        @Nullable private Double metricValue;
        @Nullable private Double threshold;
        @Nullable private String severity;
        @Nullable private String message;
        @Nullable private String problemId;

        public Builder alertRuleId(@Nullable String alertRuleId) {
            this.alertRuleId = alertRuleId;
            return this;
        }

        public Builder alertRuleName(@Nullable String alertRuleName) {
            this.alertRuleName = alertRuleName;
            return this;
        }

        public Builder stationId(@Nullable Long stationId) {
            this.stationId = stationId;
            return this;
        }

        public Builder stationName(@Nullable String stationName) {
            this.stationName = stationName;
            return this;
        }

        public Builder metricType(@Nullable String metricType) {
            this.metricType = metricType;
            return this;
        }

        public Builder metricValue(@Nullable Double metricValue) {
            this.metricValue = metricValue;
            return this;
        }

        public Builder threshold(@Nullable Double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder severity(@Nullable String severity) {
            this.severity = severity;
            return this;
        }

        public Builder message(@Nullable String message) {
            this.message = message;
            return this;
        }

        public Builder problemId(@Nullable String problemId) {
            this.problemId = problemId;
            return this;
        }

        public AlertEvent build() {
            return new AlertEvent(this);
        }
    }

    // Getters and Setters
    @Nullable
    public String getAlertRuleId() {
        return alertRuleId;
    }

    public void setAlertRuleId(@Nullable String alertRuleId) {
        this.alertRuleId = alertRuleId;
    }

    @Nullable
    public String getAlertRuleName() {
        return alertRuleName;
    }

    public void setAlertRuleName(@Nullable String alertRuleName) {
        this.alertRuleName = alertRuleName;
    }

    @Nullable
    public Long getStationId() {
        return stationId;
    }

    public void setStationId(@Nullable Long stationId) {
        this.stationId = stationId;
    }

    @Nullable
    public String getStationName() {
        return stationName;
    }

    public void setStationName(@Nullable String stationName) {
        this.stationName = stationName;
    }

    @Nullable
    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(@Nullable String metricType) {
        this.metricType = metricType;
    }

    @Nullable
    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(@Nullable Double metricValue) {
        this.metricValue = metricValue;
    }

    @Nullable
    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(@Nullable Double threshold) {
        this.threshold = threshold;
    }

    @Nullable
    public String getSeverity() {
        return severity;
    }

    public void setSeverity(@Nullable String severity) {
        this.severity = severity;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    @Nullable
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(@Nullable LocalDateTime timestamp) {
        this.timestamp = Objects.requireNonNullElseGet(timestamp, LocalDateTime::now);
    }

    @Nullable
    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(@Nullable String problemId) {
        this.problemId = problemId;
    }

    @Override
    public String toString() {
        return "AlertEvent{" +
                "alertRuleId='" + alertRuleId + '\'' +
                ", alertRuleName='" + alertRuleName + '\'' +
                ", stationId=" + stationId +
                ", stationName='" + stationName + '\'' +
                ", metricType='" + metricType + '\'' +
                ", metricValue=" + metricValue +
                ", threshold=" + threshold +
                ", severity='" + severity + '\'' +
                ", message='" + message + '\'' +
                ", problemId='" + problemId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
