package com.huawei.basestation.dto;

import com.huawei.basestation.model.StationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for bulk station import.
 */
public record BulkImportRequest(
        @NotEmpty(message = "At least one station is required")
        @Valid
        List<StationImportItem> stations,

        ImportOptions options
) {
    /**
     * Individual station data for import.
     */
    public record StationImportItem(
            @NotBlank(message = "Station name is required")
            String stationName,

            @NotBlank(message = "Location is required")
            String location,

            @NotNull(message = "Latitude is required")
            Double latitude,

            @NotNull(message = "Longitude is required")
            Double longitude,

            @NotNull(message = "Station type is required")
            StationType stationType,

            Double powerConsumption,

            String description
    ) {}

    /**
     * Options for bulk import behavior.
     */
    public record ImportOptions(
            boolean skipDuplicates,      // Skip stations with duplicate names
            boolean updateExisting,       // Update existing stations if found
            boolean validateCoordinates,  // Validate lat/lon are in valid range
            boolean dryRun               // Validate only, don't persist
    ) {
        public ImportOptions() {
            this(true, false, true, false);
        }
    }

    public BulkImportRequest {
        if (options == null) {
            options = new ImportOptions();
        }
    }
}
