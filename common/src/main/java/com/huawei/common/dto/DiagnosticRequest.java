package com.huawei.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huawei.common.constants.DiagnosticConstants;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.lang.Nullable;

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
        this.id = DiagnosticConstants.PROBLEM_ID_PREFIX + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, DiagnosticConstants.PROBLEM_ID_SUFFIX_LENGTH);
        this.timestamp = Instant.now().toString();
        this.metrics = new HashMap<>();
        this.rawLogs = "";
    }

    private DiagnosticRequest(Builder builder) {
        this.id = Objects.requireNonNullElseGet(builder.id,
                () -> DiagnosticConstants.PROBLEM_ID_PREFIX + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, DiagnosticConstants.PROBLEM_ID_SUFFIX_LENGTH));
        this.timestamp = Objects.requireNonNullElseGet(builder.timestamp, () -> Instant.now().toString());
        this.stationId = builder.stationId;
        this.category = builder.category;
        this.severity = builder.severity;
        this.code = builder.code;
        this.message = builder.message;
        this.metrics = Objects.requireNonNullElseGet(builder.metrics, HashMap::new);
        this.rawLogs = Objects.requireNonNullElse(builder.rawLogs, "");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a DiagnosticRequest from an AlertEvent.
     */
    public static DiagnosticRequest fromAlertEvent(AlertEvent alert) {
        return fromAlertEvent(alert, null);
    }

    /**
     * Create a DiagnosticRequest from an AlertEvent with explicit problem ID.
     * Using explicit ID ensures the request matches the diagnostic session.
     */
    public static DiagnosticRequest fromAlertEvent(AlertEvent alert, @Nullable String problemId) {
        String metricType = Objects.requireNonNull(
                Objects.requireNonNullElse(alert.getMetricType(), "UNKNOWN"));
        String severity = Objects.requireNonNull(
                Objects.requireNonNullElse(alert.getSeverity(), "warning"));

        String category = mapMetricTypeToCategory(metricType);
        String code = mapMetricTypeToCode(metricType);
        String metricKey = metricType.toLowerCase();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put(metricKey, alert.getMetricValue());
        metrics.put("threshold", alert.getThreshold());

        String message = Objects.requireNonNullElse(alert.getMessage(), "Alert triggered");
        String alertRuleName = Objects.requireNonNullElse(alert.getAlertRuleName(), "Unknown rule");

        Builder builder = DiagnosticRequest.builder()
                .stationId(Objects.requireNonNullElseGet(alert.getStationName(), () -> "STATION-" + alert.getStationId()))
                .category(category)
                .severity(severity.toLowerCase())
                .code(code)
                .message(message)
                .metrics(metrics)
                .rawLogs("Alert triggered: " + alertRuleName + " - " + message);

        if (problemId != null) {
            builder.id(problemId);
        }

        return builder.build();
    }

    private static String mapMetricTypeToCategory(String metricType) {
        return switch (metricType.toUpperCase()) {
            case "CPU_USAGE", "MEMORY_USAGE", "TEMPERATURE" -> "hardware";
            case "SIGNAL_STRENGTH", "LATENCY", "PACKET_LOSS" -> "network";
            case "POWER_CONSUMPTION", "VOLTAGE" -> "power";
            default -> "software";
        };
    }

    private static String mapMetricTypeToCode(String metricType) {
        return switch (metricType.toUpperCase()) {
            case "CPU_USAGE", "TEMPERATURE" -> "CPU_OVERHEAT";
            case "MEMORY_USAGE" -> "MEMORY_PRESSURE";
            case "SIGNAL_STRENGTH" -> "SIGNAL_DEGRADATION";
            case "POWER_CONSUMPTION" -> "HIGH_POWER_CONSUMPTION";
            case "INITIAL_BLER" -> "HIGH_BLOCK_ERROR_RATE";
            case "BATTERY_SOC" -> "LOW_BATTERY";
            case "LATENCY_PING" -> "HIGH_LATENCY";
            case "DATA_THROUGHPUT" -> "LOW_THROUGHPUT";
            case "HANDOVER_SUCCESS_RATE" -> "HANDOVER_FAILURE";
            case "INTERFERENCE_LEVEL" -> "HIGH_INTERFERENCE";
            default -> metricType.toUpperCase() + "_ISSUE";
        };
    }

    public static class Builder {
        @Nullable private String id;
        @Nullable private String timestamp;
        @Nullable private String stationId;
        @Nullable private String category;
        @Nullable private String severity;
        @Nullable private String code;
        @Nullable private String message;
        @Nullable private Map<String, Object> metrics;
        @Nullable private String rawLogs;

        public Builder id(@Nullable String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(@Nullable String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder stationId(@Nullable String stationId) {
            this.stationId = stationId;
            return this;
        }

        public Builder category(@Nullable String category) {
            this.category = category;
            return this;
        }

        public Builder severity(@Nullable String severity) {
            this.severity = severity;
            return this;
        }

        public Builder code(@Nullable String code) {
            this.code = code;
            return this;
        }

        public Builder message(@Nullable String message) {
            this.message = message;
            return this;
        }

        public Builder metrics(@Nullable Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder rawLogs(@Nullable String rawLogs) {
            this.rawLogs = rawLogs;
            return this;
        }

        public DiagnosticRequest build() {
            return new DiagnosticRequest(this);
        }
    }

    // Getters and Setters
    @Nullable
    public String getId() { return id; }
    public void setId(@Nullable String id) { this.id = id; }

    @Nullable
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(@Nullable String timestamp) { this.timestamp = timestamp; }

    @Nullable
    public String getStationId() { return stationId; }
    public void setStationId(@Nullable String stationId) { this.stationId = stationId; }

    @Nullable
    public String getCategory() { return category; }
    public void setCategory(@Nullable String category) { this.category = category; }

    @Nullable
    public String getSeverity() { return severity; }
    public void setSeverity(@Nullable String severity) { this.severity = severity; }

    @Nullable
    public String getCode() { return code; }
    public void setCode(@Nullable String code) { this.code = code; }

    @Nullable
    public String getMessage() { return message; }
    public void setMessage(@Nullable String message) { this.message = message; }

    @Nullable
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(@Nullable Map<String, Object> metrics) { this.metrics = metrics; }

    @Nullable
    public String getRawLogs() { return rawLogs; }
    public void setRawLogs(@Nullable String rawLogs) { this.rawLogs = rawLogs; }

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
