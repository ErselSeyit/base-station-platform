package io.github.erselseyit.basestation.monitoring.dto;

import io.github.erselseyit.basestation.monitoring.model.MetricType;
import io.github.erselseyit.basestation.monitoring.validation.ValidMetricUnit;
import io.github.erselseyit.basestation.monitoring.validation.ValidMetricValue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.Nullable;

import io.github.erselseyit.basestation.monitoring.model.Band;

@ValidMetricUnit
@ValidMetricValue
public class MetricDataDTO {

    private String id;

    @NotNull(message = "Station ID is required")
    private Long stationId;

    private String stationName;

    @NotNull(message = "Metric type is required")
    private MetricType metricType;

    /** NR band for radio metrics; defaults to NONE when absent from the payload. */
    private Band band = Band.NONE;

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
    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
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
    public MetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(@Nullable MetricType metricType) {
        this.metricType = metricType;
    }

    public Band getBand() {
        return band;
    }

    public void setBand(@Nullable Band band) {
        this.band = band == null ? Band.NONE : band;
    }

    @Nullable
    public Double getValue() {
        return value;
    }

    public void setValue(@Nullable Double value) {
        this.value = value;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }

    public void setUnit(@Nullable String unit) {
        this.unit = unit;
    }

    @Nullable
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(@Nullable LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public void setStatus(@Nullable String status) {
        this.status = status;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MetricDataDTO that = (MetricDataDTO) o;

        return Objects.equals(id, that.id)
                && Objects.equals(stationId, that.stationId)
                && Objects.equals(stationName, that.stationName)
                && metricType == that.metricType
                && Objects.equals(value, that.value)
                && Objects.equals(unit, that.unit)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, stationId, stationName, metricType, value, unit, timestamp, status);
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
