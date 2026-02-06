package com.huawei.basestation.dto;

import com.huawei.basestation.model.ManagementProtocol;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

import org.springframework.lang.Nullable;

/**
 * Immutable DTO for base station data transfer.
 * Uses builder pattern for ergonomic construction.
 */
public record BaseStationDTO(
        @Nullable Long id,

        @NotBlank(message = "Station name is required")
        String stationName,

        @NotBlank(message = "Location is required")
        String location,

        @NotNull(message = "Latitude is required")
        @Min(value = -90, message = "Latitude must be between -90 and 90 degrees")
        @Max(value = 90, message = "Latitude must be between -90 and 90 degrees")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @Min(value = -180, message = "Longitude must be between -180 and 180 degrees")
        @Max(value = 180, message = "Longitude must be between -180 and 180 degrees")
        Double longitude,

        @NotNull(message = "Station type is required")
        StationType stationType,

        @Nullable StationStatus status,

        @Nullable
        @Positive(message = "Power consumption must be positive")
        Double powerConsumption,

        @Nullable String description,
        @Nullable String ipAddress,
        @Nullable Integer port,
        @Nullable ManagementProtocol managementProtocol,
        @Nullable LocalDateTime createdAt,
        @Nullable LocalDateTime updatedAt
) {
    /**
     * Creates a new builder for BaseStationDTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing BaseStationDTO instances.
     */
    public static final class Builder {
        private Long id;
        private String stationName;
        private String location;
        private Double latitude;
        private Double longitude;
        private StationType stationType;
        private StationStatus status;
        private Double powerConsumption;
        private String description;
        private String ipAddress;
        private Integer port;
        private ManagementProtocol managementProtocol;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {}

        public Builder id(@Nullable Long id) {
            this.id = id;
            return this;
        }

        public Builder stationName(@Nullable String stationName) {
            this.stationName = stationName;
            return this;
        }

        public Builder location(@Nullable String location) {
            this.location = location;
            return this;
        }

        public Builder latitude(@Nullable Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(@Nullable Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder stationType(@Nullable StationType stationType) {
            this.stationType = stationType;
            return this;
        }

        public Builder status(@Nullable StationStatus status) {
            this.status = status;
            return this;
        }

        public Builder powerConsumption(@Nullable Double powerConsumption) {
            this.powerConsumption = powerConsumption;
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder ipAddress(@Nullable String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder port(@Nullable Integer port) {
            this.port = port;
            return this;
        }

        public Builder managementProtocol(@Nullable ManagementProtocol managementProtocol) {
            this.managementProtocol = managementProtocol;
            return this;
        }

        public Builder createdAt(@Nullable LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(@Nullable LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public BaseStationDTO build() {
            return new BaseStationDTO(
                    id, stationName, location, latitude, longitude,
                    stationType, status, powerConsumption, description,
                    ipAddress, port, managementProtocol, createdAt, updatedAt
            );
        }
    }
}
