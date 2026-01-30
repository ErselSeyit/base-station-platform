package com.huawei.basestation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "base_stations", indexes = {
    @Index(name = "idx_geo_coordinates", columnList = "latitude, longitude"),
    @Index(name = "idx_station_status", columnList = "status"),
    @Index(name = "idx_station_type", columnList = "stationType")
})
@EntityListeners(AuditingEntityListener.class)
public class BaseStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Station name is required")
    @Column(nullable = false, unique = true)
    private String stationName;

    @NotBlank(message = "Location is required")
    @Column(nullable = false)
    private String location;

    @NotNull(message = "Latitude is required")
    @Column(nullable = false)
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationType stationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationStatus status = StationStatus.ACTIVE;

    @Positive(message = "Power consumption must be positive")
    private Double powerConsumption;

    @Column(length = 1000)
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public BaseStation() {
    }

    public BaseStation(String stationName, String location, Double latitude, Double longitude, 
                      StationType stationType) {
        this.stationName = stationName;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stationType = stationType;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public StationType getStationType() {
        return stationType;
    }

    public void setStationType(StationType stationType) {
        this.stationType = stationType;
    }

    public StationStatus getStatus() {
        return status;
    }

    public void setStatus(StationStatus status) {
        this.status = status;
    }

    public Double getPowerConsumption() {
        return powerConsumption;
    }

    public void setPowerConsumption(Double powerConsumption) {
        this.powerConsumption = powerConsumption;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========================================================================
    // Domain Behavior Methods
    // ========================================================================

    /**
     * Activates the station, making it operational.
     *
     * @throws IllegalStateException if station is in ERROR state
     */
    public void activate() {
        if (this.status == StationStatus.ERROR) {
            throw new IllegalStateException("Cannot activate station in ERROR state. Resolve errors first.");
        }
        this.status = StationStatus.ACTIVE;
    }

    /**
     * Deactivates the station gracefully.
     */
    public void deactivate() {
        this.status = StationStatus.INACTIVE;
    }

    /**
     * Puts the station into maintenance mode.
     */
    public void setMaintenance() {
        this.status = StationStatus.MAINTENANCE;
    }

    /**
     * Marks the station as offline (e.g., connection lost).
     */
    public void markOffline() {
        this.status = StationStatus.OFFLINE;
    }

    /**
     * Marks the station as having an error condition.
     *
     * @param errorDescription description of the error for logging
     */
    public void markError(String errorDescription) {
        this.status = StationStatus.ERROR;
        // Error description could be stored in description field or logged
        if (errorDescription != null && !errorDescription.isBlank()) {
            this.description = (this.description != null ? this.description + " | " : "")
                    + "ERROR: " + errorDescription;
        }
    }

    /**
     * Checks if the station is currently operational (ACTIVE).
     *
     * @return true if the station is active and can handle traffic
     */
    public boolean isOperational() {
        return this.status == StationStatus.ACTIVE;
    }

    /**
     * Checks if the station is in a healthy state (ACTIVE or MAINTENANCE).
     *
     * @return true if the station is not in an error or offline state
     */
    public boolean isHealthy() {
        return this.status == StationStatus.ACTIVE
                || this.status == StationStatus.MAINTENANCE
                || this.status == StationStatus.INACTIVE;
    }

    /**
     * Checks if the station requires attention (ERROR or OFFLINE).
     *
     * @return true if the station needs operator attention
     */
    public boolean requiresAttention() {
        return this.status == StationStatus.ERROR
                || this.status == StationStatus.OFFLINE;
    }

    /**
     * Calculates the approximate distance to another location using Haversine formula.
     *
     * @param targetLat target latitude in degrees
     * @param targetLon target longitude in degrees
     * @return distance in kilometers
     */
    public double distanceToKm(double targetLat, double targetLon) {
        final double EARTH_RADIUS_KM = 6371.0;

        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(targetLat);
        double deltaLat = Math.toRadians(targetLat - this.latitude);
        double deltaLon = Math.toRadians(targetLon - this.longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Checks if power consumption exceeds expected threshold for station type.
     *
     * @return true if power consumption is abnormally high
     */
    public boolean isPowerConsumptionHigh() {
        if (this.powerConsumption == null) {
            return false;
        }
        // Threshold values based on station type (in watts)
        double threshold = switch (this.stationType) {
            case MACRO_CELL -> 5000.0;  // Macro cells typically use 2-5kW
            case MICRO_CELL -> 500.0;   // Micro cells use 100-500W
            case SMALL_CELL -> 200.0;   // Small cells use 50-200W
            case PICO_CELL -> 100.0;    // Pico cells use 10-100W
            case FEMTO_CELL -> 20.0;    // Femto cells use 5-20W
        };
        return this.powerConsumption > threshold;
    }

    /**
     * Updates the station's geographic coordinates.
     *
     * @param latitude  new latitude (-90 to 90)
     * @param longitude new longitude (-180 to 180)
     * @throws IllegalArgumentException if coordinates are out of valid range
     */
    public void updateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}

