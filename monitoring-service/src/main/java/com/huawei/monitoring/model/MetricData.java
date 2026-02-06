package com.huawei.monitoring.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "metric_data")
@CompoundIndex(name = "idx_station_timestamp", def = "{'stationId': 1, 'timestamp': -1}")
@CompoundIndex(name = "idx_station_type", def = "{'stationId': 1, 'metricType': 1}")
public class MetricData {

    @Id
    private String id;

    @Indexed
    private Long stationId;
    private String stationName;
    private MetricType metricType;
    private Double value;
    private String unit;
    @Indexed
    private LocalDateTime timestamp;
    private String status;

    public MetricData() {
    }

    public MetricData(Long stationId, String stationName, MetricType metricType, Double value, String unit) {
        this.stationId = stationId;
        this.stationName = stationName;
        this.metricType = metricType;
        this.value = value;
        this.unit = unit;
        this.timestamp = LocalDateTime.now();
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

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // ========================================================================
    // Domain Behavior Methods
    // ========================================================================

    /**
     * Checks if the metric value exceeds a given threshold.
     *
     * @param threshold the threshold to compare against
     * @return true if value exceeds the threshold
     */
    public boolean exceedsThreshold(double threshold) {
        return this.value != null && this.value > threshold;
    }

    /**
     * Checks if this metric indicates an anomaly based on type-specific thresholds.
     *
     * @return true if the value is outside normal operating range
     */
    public boolean isAnomaly() {
        if (this.value == null || this.metricType == null) {
            return false;
        }

        return switch (this.metricType) {
            case CPU_USAGE, MEMORY_USAGE -> this.value > 90.0;        // > 90% is anomalous
            case TEMPERATURE -> this.value > 85.0 || this.value < 0;  // Outside 0-85°C
            case POWER_CONSUMPTION -> this.value < 0;                  // Negative power invalid
            case SIGNAL_STRENGTH -> this.value < -110.0;              // Below -110 dBm is poor
            case RSRP_NR700, RSRP_NR3500 -> this.value < -120.0;      // Poor RSRP
            case SINR_NR700, SINR_NR3500 -> this.value < 0.0;         // Negative SINR is poor
            case INITIAL_BLER -> this.value > 10.0;                   // > 10% BLER is high
            case HANDOVER_SUCCESS_RATE -> this.value < 95.0;          // < 95% success rate
            case VSWR -> this.value > 2.0;                            // VSWR > 2:1 indicates issues
            case LATENCY_PING -> this.value > 100.0;                  // > 100ms latency
            default -> false;
        };
    }

    /**
     * Checks if this metric indicates a critical condition requiring immediate attention.
     *
     * @return true if the value indicates a critical state
     */
    public boolean isCritical() {
        if (this.value == null || this.metricType == null) {
            return false;
        }

        return switch (this.metricType) {
            case CPU_USAGE, MEMORY_USAGE -> this.value > 98.0;        // Near 100%
            case TEMPERATURE -> this.value > 95.0;                     // Overheating
            case SIGNAL_STRENGTH -> this.value < -120.0;              // Very poor signal
            case RSRP_NR700, RSRP_NR3500 -> this.value < -130.0;      // Very poor RSRP
            case INITIAL_BLER -> this.value > 30.0;                   // Very high BLER
            case HANDOVER_SUCCESS_RATE -> this.value < 80.0;          // High handover failures
            case VSWR -> this.value > 3.0;                            // High VSWR indicates damage
            default -> false;
        };
    }

    /**
     * Checks if the metric is stale (older than the specified duration).
     *
     * @param maxAgeSeconds maximum age in seconds
     * @return true if the metric timestamp is older than maxAgeSeconds
     */
    public boolean isStale(long maxAgeSeconds) {
        if (this.timestamp == null) {
            return true;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(maxAgeSeconds);
        return this.timestamp.isBefore(threshold);
    }

    /**
     * Checks if this is a throughput metric.
     *
     * @return true if the metric type represents throughput
     */
    public boolean isThroughputMetric() {
        if (this.metricType == null) {
            return false;
        }
        return switch (this.metricType) {
            case DATA_THROUGHPUT, DL_THROUGHPUT_NR700, UL_THROUGHPUT_NR700,
                 DL_THROUGHPUT_NR3500, UL_THROUGHPUT_NR3500,
                 PDCP_THROUGHPUT, RLC_THROUGHPUT,
                 CA_DL_THROUGHPUT, CA_UL_THROUGHPUT -> true;
            default -> false;
        };
    }

    /**
     * Checks if this is an RF quality metric (signal strength, RSRP, SINR, etc.).
     *
     * @return true if the metric type represents RF quality
     */
    public boolean isRfQualityMetric() {
        if (this.metricType == null) {
            return false;
        }
        return switch (this.metricType) {
            case SIGNAL_STRENGTH, RSRP_NR700, RSRP_NR3500,
                 SINR_NR700, SINR_NR3500, TX_IMBALANCE,
                 INTERFERENCE_LEVEL, VSWR -> true;
            default -> false;
        };
    }

    /**
     * Gets a human-readable description of the metric status.
     *
     * @return status description (NORMAL, WARNING, or CRITICAL)
     */
    public String getHealthStatus() {
        if (isCritical()) {
            return "CRITICAL";
        } else if (isAnomaly()) {
            return "WARNING";
        }
        return "NORMAL";
    }
}

