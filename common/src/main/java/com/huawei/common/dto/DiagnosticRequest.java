package com.huawei.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for AI diagnostic service.
 * Maps to the Python diagnostic service Problem format.
 */
public class DiagnosticRequest {

    @JsonProperty("id")
    private String id;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("station_id")
    private String stationId;

    @JsonProperty("category")
    private String category;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("metrics")
    private Map<String, Object> metrics;

    @JsonProperty("raw_logs")
    private String rawLogs;

    public DiagnosticRequest() {
        this.id = "PRB-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
        this.timestamp = Instant.now().toString();
        this.metrics = new HashMap<>();
        this.rawLogs = "";
    }

    private DiagnosticRequest(Builder builder) {
        this.id = builder.id != null ? builder.id : "PRB-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now().toString();
        this.stationId = builder.stationId;
        this.category = builder.category;
        this.severity = builder.severity;
        this.code = builder.code;
        this.message = builder.message;
        this.metrics = builder.metrics != null ? builder.metrics : new HashMap<>();
        this.rawLogs = builder.rawLogs != null ? builder.rawLogs : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a DiagnosticRequest from an AlertEvent.
     */
    public static DiagnosticRequest fromAlertEvent(AlertEvent alert) {
        String category = mapMetricTypeToCategory(alert.getMetricType());
        String code = mapMetricTypeToCode(alert.getMetricType());

        Map<String, Object> metrics = new HashMap<>();
        metrics.put(alert.getMetricType().toLowerCase(), alert.getMetricValue());
        metrics.put("threshold", alert.getThreshold());

        return DiagnosticRequest.builder()
                .stationId(alert.getStationName() != null ? alert.getStationName() : "STATION-" + alert.getStationId())
                .category(category)
                .severity(alert.getSeverity().toLowerCase())
                .code(code)
                .message(alert.getMessage())
                .metrics(metrics)
                .rawLogs("Alert triggered: " + alert.getAlertRuleName() + " - " + alert.getMessage())
                .build();
    }

    private static String mapMetricTypeToCategory(String metricType) {
        if (metricType == null) return "software";
        return switch (metricType.toUpperCase()) {
            case "CPU_USAGE", "MEMORY_USAGE", "TEMPERATURE" -> "hardware";
            case "SIGNAL_STRENGTH", "LATENCY", "PACKET_LOSS" -> "network";
            case "POWER_CONSUMPTION", "VOLTAGE" -> "power";
            default -> "software";
        };
    }

    private static String mapMetricTypeToCode(String metricType) {
        if (metricType == null) return "UNKNOWN_ISSUE";
        return switch (metricType.toUpperCase()) {
            case "CPU_USAGE" -> "CPU_OVERHEAT";
            case "MEMORY_USAGE" -> "MEMORY_PRESSURE";
            case "TEMPERATURE" -> "CPU_OVERHEAT";
            case "SIGNAL_STRENGTH" -> "SIGNAL_DEGRADATION";
            case "LATENCY" -> "BACKHAUL_LATENCY";
            case "POWER_CONSUMPTION" -> "HIGH_POWER_CONSUMPTION";
            default -> metricType.toUpperCase() + "_ISSUE";
        };
    }

    public static class Builder {
        private String id;
        private String timestamp;
        private String stationId;
        private String category;
        private String severity;
        private String code;
        private String message;
        private Map<String, Object> metrics;
        private String rawLogs;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder stationId(String stationId) {
            this.stationId = stationId;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder rawLogs(String rawLogs) {
            this.rawLogs = rawLogs;
            return this;
        }

        public DiagnosticRequest build() {
            return new DiagnosticRequest(this);
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }

    public String getRawLogs() { return rawLogs; }
    public void setRawLogs(String rawLogs) { this.rawLogs = rawLogs; }

    @Override
    public String toString() {
        return "DiagnosticRequest{" +
                "id='" + id + '\'' +
                ", stationId='" + stationId + '\'' +
                ", category='" + category + '\'' +
                ", severity='" + severity + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
