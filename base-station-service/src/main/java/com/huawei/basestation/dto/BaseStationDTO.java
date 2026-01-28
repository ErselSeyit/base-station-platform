package com.huawei.basestation.dto;

import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.Nullable;

public class BaseStationDTO {

    private Long id;

    @NotBlank(message = "Station name is required")
    private String stationName;

    @NotBlank(message = "Location is required")
    private String location;

    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90 degrees")
    @Max(value = 90, message = "Latitude must be between -90 and 90 degrees")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180 degrees")
    @Max(value = 180, message = "Longitude must be between -180 and 180 degrees")
    private Double longitude;

    @NotNull(message = "Station type is required")
    private StationType stationType;

    private StationStatus status;

    @Positive(message = "Power consumption must be positive")
    private Double powerConsumption;

    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public BaseStationDTO() {
        // Default constructor required for Jackson deserialization and JPA
    }

    // Getters and Setters
    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    @Nullable
    public String getStationName() {
        return stationName;
    }

    public void setStationName(@Nullable String stationName) {
        this.stationName = stationName;
    }

    @Nullable
    public String getLocation() {
        return location;
    }

    public void setLocation(@Nullable String location) {
        this.location = location;
    }

    @Nullable
    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(@Nullable Double latitude) {
        this.latitude = latitude;
    }

    @Nullable
    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(@Nullable Double longitude) {
        this.longitude = longitude;
    }

    @Nullable
    public StationType getStationType() {
        return stationType;
    }

    public void setStationType(@Nullable StationType stationType) {
        this.stationType = stationType;
    }

    @Nullable
    public StationStatus getStatus() {
        return status;
    }

    public void setStatus(@Nullable StationStatus status) {
        this.status = status;
    }

    @Nullable
    public Double getPowerConsumption() {
        return powerConsumption;
    }

    public void setPowerConsumption(@Nullable Double powerConsumption) {
        this.powerConsumption = powerConsumption;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nullable
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@Nullable LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(@Nullable LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseStationDTO that = (BaseStationDTO) o;

        return Objects.equals(id, that.id)
                && Objects.equals(stationName, that.stationName)
                && Objects.equals(location, that.location)
                && Objects.equals(latitude, that.latitude)
                && Objects.equals(longitude, that.longitude)
                && stationType == that.stationType
                && status == that.status
                && Objects.equals(powerConsumption, that.powerConsumption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, stationName, location, latitude, longitude, stationType, status, powerConsumption);
    }

    @Override
    public String toString() {
        return "BaseStationDTO{" +
                "id=" + id +
                ", stationName='" + stationName + '\'' +
                ", location='" + location + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", stationType=" + stationType +
                ", status=" + status +
                ", powerConsumption=" + powerConsumption +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

