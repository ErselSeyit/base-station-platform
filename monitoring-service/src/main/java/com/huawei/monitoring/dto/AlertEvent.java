package com.huawei.monitoring.dto;

import com.huawei.monitoring.model.AlertSeverity;
import com.huawei.monitoring.model.MetricType;

import java.time.LocalDateTime;

/**
 * Event DTO for alert notifications sent via RabbitMQ.
 */
public class AlertEvent {
    
    private String alertRuleId;
    private String alertRuleName;
    private Long stationId;
    private String stationName;
    private MetricType metricType;
    private Double metricValue;
    private Double threshold;
    private AlertSeverity severity;
    private String message;
    private LocalDateTime timestamp;

    public AlertEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public AlertEvent(String alertRuleId, String alertRuleName, Long stationId, String stationName,
                     MetricType metricType, Double metricValue, Double threshold,
                     AlertSeverity severity, String message) {
        this.alertRuleId = alertRuleId;
        this.alertRuleName = alertRuleName;
        this.stationId = stationId;
        this.stationName = stationName;
        this.metricType = metricType;
        this.metricValue = metricValue;
        this.threshold = threshold;
        this.severity = severity;
        this.message = message;
        this.timestamp = LocalDateTime.now();
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
        this.stationId = stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricType metricType) {
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

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
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
        this.timestamp = timestamp;
    }
}
