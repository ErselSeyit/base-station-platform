package com.huawei.monitoring.dto;

import com.huawei.monitoring.model.MetricType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class MetricDataDTO {

    private String id;

    @NotNull(message = "Station ID is required")
    private Long stationId;

    private String stationName;

    @NotNull(message = "Metric type is required")
    private MetricType metricType;

    @NotNull(message = "Value is required")
    private Double value;

    private String unit;
    private LocalDateTime timestamp;
    private String status;

    // Constructors
    public MetricDataDTO() {
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
}

