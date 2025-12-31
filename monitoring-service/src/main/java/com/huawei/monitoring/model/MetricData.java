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
}

