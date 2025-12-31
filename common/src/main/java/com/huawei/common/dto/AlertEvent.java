package com.huawei.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

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
        this.timestamp = LocalDateTime.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String alertRuleId;
        private String alertRuleName;
        private Long stationId;
        private String stationName;
        private String metricType;
        private Double metricValue;
        private Double threshold;
        private String severity;
        private String message;

        public Builder alertRuleId(String alertRuleId) {
            this.alertRuleId = alertRuleId;
            return this;
        }

        public Builder alertRuleName(String alertRuleName) {
            this.alertRuleName = alertRuleName;
            return this;
        }

        public Builder stationId(Long stationId) {
            this.stationId = stationId;
            return this;
        }

        public Builder stationName(String stationName) {
            this.stationName = stationName;
            return this;
        }

        public Builder metricType(String metricType) {
            this.metricType = metricType;
            return this;
        }

        public Builder metricValue(Double metricValue) {
            this.metricValue = metricValue;
            return this;
        }

        public Builder threshold(Double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public AlertEvent build() {
            return new AlertEvent(this);
        }
    }

    // Getters and Setters
    public String getAlertRuleId() {
        return alertRuleId;
    }

    public void setAlertRuleId(String alertRuleId) {
        this.alertRuleId = alertRuleId;
    }

    public String getAlertRuleName() {
        return alertRuleName;
    }

    public void setAlertRuleName(String alertRuleName) {
        this.alertRuleName = alertRuleName;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = Objects.requireNonNull(stationId, "Station ID cannot be null");
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
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
                ", timestamp=" + timestamp +
                '}';
    }
}
