package com.huawei.monitoring.dto;

import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.validation.ValidMetricUnit;
import com.huawei.monitoring.validation.ValidMetricValue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@ValidMetricUnit
@ValidMetricValue
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
        // Default constructor required for JSON deserialization and JPA
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MetricDataDTO that = (MetricDataDTO) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (stationId != null ? !stationId.equals(that.stationId) : that.stationId != null) return false;
        if (stationName != null ? !stationName.equals(that.stationName) : that.stationName != null) return false;
        if (metricType != that.metricType) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        if (unit != null ? !unit.equals(that.unit) : that.unit != null) return false;
        if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null) return false;
        return status != null ? status.equals(that.status) : that.status == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (stationId != null ? stationId.hashCode() : 0);
        result = 31 * result + (stationName != null ? stationName.hashCode() : 0);
        result = 31 * result + (metricType != null ? metricType.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MetricDataDTO{" +
                "id='" + id + '\'' +
                ", stationId=" + stationId +
                ", stationName='" + stationName + '\'' +
                ", metricType=" + metricType +
                ", value=" + value +
                ", unit='" + unit + '\'' +
                ", timestamp=" + timestamp +
                ", status='" + status + '\'' +
                '}';
    }
}

