package com.huawei.monitoring.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Threshold configuration document stored in MongoDB.
 *
 * Provides centralized, hot-reloadable threshold configuration for both
 * Java backend and Python AI diagnostic service.
 *
 * ConfigType examples:
 * - "health" - Health status thresholds (critical, warning, degraded)
 * - "confidence" - Automation confidence thresholds
 * - "learning" - Learning algorithm thresholds
 * - "equipment.temperature" - Temperature thresholds
 * - "equipment.cpu" - CPU usage thresholds
 * - "risk.LOW" - Low risk level configuration
 */
@Document(collection = "threshold_config")
public class ThresholdConfig {

    @Id
    private String id;

    /**
     * Unique configuration type identifier.
     * Uses dot notation for hierarchical configs (e.g., "equipment.temperature").
     */
    @Indexed(unique = true)
    private String configType;

    /**
     * Human-readable description of this configuration.
     */
    private String description;

    /**
     * Threshold values as key-value pairs.
     * Keys are threshold names, values are numeric thresholds.
     * Example: {"critical": 0.4, "warning": 0.6, "degraded": 0.8}
     */
    private Map<String, Double> thresholds = new HashMap<>();

    /**
     * Additional metadata as key-value pairs.
     * Example: {"unit": "celsius", "higher_is_worse": true}
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Optimistic locking version for concurrent updates.
     */
    @Version
    private Long version;

    /**
     * Username who last updated this config.
     */
    private String updatedBy;

    /**
     * Timestamp of last update.
     */
    private LocalDateTime updatedAt;

    /**
     * Whether this configuration is active.
     */
    private boolean enabled = true;

    // Constructors

    public ThresholdConfig() {
        // Default constructor for MongoDB
    }

    public ThresholdConfig(String configType, String description) {
        this.configType = configType;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    // Fluent builder methods

    public ThresholdConfig withThreshold(String name, Double value) {
        this.thresholds.put(name, value);
        return this;
    }

    public ThresholdConfig withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public ThresholdConfig withUpdatedBy(String username) {
        this.updatedBy = username;
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    // Static factory methods for common configurations

    public static ThresholdConfig healthStatus() {
        return new ThresholdConfig("health", "Health status determination thresholds")
                .withThreshold("critical", 0.4)
                .withThreshold("warning", 0.6)
                .withThreshold("degraded", 0.8)
                .withMetadata("comment", "Health score below threshold maps to that status");
    }

    public static ThresholdConfig confidence() {
        return new ThresholdConfig("confidence", "Automation confidence thresholds")
                .withThreshold("auto_apply", 0.95)
                .withThreshold("suggest_apply", 0.85)
                .withThreshold("manual_review", 0.70)
                .withThreshold("low", 0.70)
                .withThreshold("auto_apply_low_risk", 0.90)
                .withThreshold("auto_apply_medium_risk", 0.95);
    }

    public static ThresholdConfig learning() {
        return new ThresholdConfig("learning", "Learning algorithm thresholds")
                .withThreshold("high_success_rate", 0.80)
                .withThreshold("low_success_rate", 0.50)
                .withThreshold("max_confidence_boost", 0.10)
                .withThreshold("max_confidence_penalty", 0.20)
                .withThreshold("min_feedback_for_adjustment", 5.0);
    }

    public static ThresholdConfig equipmentTemperature() {
        return new ThresholdConfig("equipment.temperature", "Temperature thresholds for equipment health")
                .withThreshold("healthy", 50.0)
                .withThreshold("warning", 70.0)
                .withThreshold("critical", 80.0)
                .withThreshold("ultra_critical", 95.0)
                .withMetadata("unit", "celsius")
                .withMetadata("higher_is_worse", true);
    }

    public static ThresholdConfig equipmentCpu() {
        return new ThresholdConfig("equipment.cpu", "CPU usage thresholds")
                .withThreshold("healthy", 60.0)
                .withThreshold("warning", 75.0)
                .withThreshold("critical", 90.0)
                .withMetadata("unit", "percent")
                .withMetadata("higher_is_worse", true);
    }

    public static ThresholdConfig equipmentBatterySoc() {
        return new ThresholdConfig("equipment.battery_soc", "Battery state of charge thresholds")
                .withThreshold("healthy", 50.0)
                .withThreshold("warning", 20.0)
                .withThreshold("critical", 10.0)
                .withMetadata("unit", "percent")
                .withMetadata("higher_is_worse", false);
    }

    public static ThresholdConfig equipmentSignalRsrp() {
        return new ThresholdConfig("equipment.signal_rsrp", "Signal RSRP thresholds")
                .withThreshold("healthy", -80.0)
                .withThreshold("warning", -100.0)
                .withThreshold("critical", -110.0)
                .withMetadata("unit", "dBm")
                .withMetadata("higher_is_worse", false);
    }

    // Utility methods

    /**
     * Get a threshold value by name.
     * @param name Threshold name (e.g., "critical", "warning")
     * @return Threshold value or null if not found
     */
    public Double getThreshold(String name) {
        return thresholds.get(name);
    }

    /**
     * Get a threshold value by name with a default.
     * @param name Threshold name
     * @param defaultValue Default if threshold not found
     * @return Threshold value or default
     */
    public Double getThresholdOrDefault(String name, Double defaultValue) {
        return thresholds.getOrDefault(name, defaultValue);
    }

    /**
     * Check if higher values are worse for this metric.
     * @return true if higher values indicate worse health
     */
    public boolean isHigherIsWorse() {
        Object value = metadata.get("higher_is_worse");
        return Boolean.TRUE.equals(value);
    }

    /**
     * Get the unit for this metric.
     * @return Unit string or null
     */
    public String getUnit() {
        Object value = metadata.get("unit");
        return value != null ? value.toString() : null;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Double> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<String, Double> thresholds) {
        this.thresholds = thresholds != null ? thresholds : new HashMap<>();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThresholdConfig that = (ThresholdConfig) o;
        return Objects.equals(configType, that.configType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configType);
    }

    @Override
    public String toString() {
        return "ThresholdConfig{" +
                "configType='" + configType + '\'' +
                ", thresholds=" + thresholds +
                ", enabled=" + enabled +
                '}';
    }
}
