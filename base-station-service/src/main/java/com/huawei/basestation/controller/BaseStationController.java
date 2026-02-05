package com.huawei.basestation.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.common.constants.ValidationMessages;
import com.huawei.common.security.Roles;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.service.BaseStationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/stations")
@Validated
@Tag(name = "Base Stations", description = "Base station CRUD and geospatial queries")
@SecurityRequirement(name = "bearerAuth")
public class BaseStationController {

    private final BaseStationService service;

    public BaseStationController(BaseStationService service) {
        this.service = service;
    }

    @Operation(summary = "Create station", description = "Creates a new base station")
    @ApiResponse(responseCode = "201", description = "Station created")
    @PostMapping
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<BaseStationDTO> createStation(
            @Parameter(description = "Station data") @Valid @RequestBody BaseStationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createStation(Objects.requireNonNull(dto, ValidationMessages.DTO_NULL_MESSAGE)));
    }

    @Operation(summary = "Get station by ID", description = "Retrieves a base station by its unique identifier")
    @ApiResponse(responseCode = "200", description = "Station found")
    @ApiResponse(responseCode = "404", description = "Station not found")
    @GetMapping("/{id}")
    public ResponseEntity<BaseStationDTO> getStationById(
            @Parameter(description = "Station ID") @PathVariable Long id) {
        return Objects.requireNonNull(
                service.getStationById(Objects.requireNonNull(id, ValidationMessages.STATION_ID_NULL_MESSAGE))
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()));
    }

    @Operation(summary = "List all stations", description = "Retrieves all base stations, optionally filtered by status or type")
    @ApiResponse(responseCode = "200", description = "List of stations")
    @GetMapping
    public ResponseEntity<List<BaseStationDTO>> getAllStations(
            @Parameter(description = "Filter by status") @RequestParam(required = false) @Nullable StationStatus status,
            @Parameter(description = "Filter by type") @RequestParam(required = false) @Nullable StationType type) {
        if (status != null) {
            return ResponseEntity.ok(service.getStationsByStatus(status));
        }
        if (type != null) {
            return ResponseEntity.ok(service.getStationsByType(type));
        }
        return ResponseEntity.ok(service.getAllStations());
    }

    @Operation(summary = "Update station", description = "Updates an existing base station")
    @ApiResponse(responseCode = "200", description = "Station updated")
    @ApiResponse(responseCode = "404", description = "Station not found")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<BaseStationDTO> updateStation(
            @Parameter(description = "Station ID") @PathVariable Long id,
            @Parameter(description = "Updated station data") @Valid @RequestBody BaseStationDTO dto) {
        try {
            return ResponseEntity.ok(service.updateStation(
                    Objects.requireNonNull(id, ValidationMessages.STATION_ID_NULL_MESSAGE),
                    Objects.requireNonNull(dto, ValidationMessages.DTO_NULL_MESSAGE)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Delete station", description = "Deletes a base station (Admin only)")
    @ApiResponse(responseCode = "204", description = "Station deleted")
    @ApiResponse(responseCode = "404", description = "Station not found")
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.HAS_ADMIN)
    public ResponseEntity<Void> deleteStation(
            @Parameter(description = "Station ID") @PathVariable Long id) {
        try {
            service.deleteStation(Objects.requireNonNull(id, ValidationMessages.STATION_ID_NULL_MESSAGE));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Find stations in area", description = "Finds stations within a geographic bounding box")
    @ApiResponse(responseCode = "200", description = "List of stations in area")
    @GetMapping("/search/area")
    public ResponseEntity<List<BaseStationDTO>> findStationsInArea(
            @Parameter(description = "Min latitude") @RequestParam @DecimalMin("-90") @DecimalMax("90") Double minLat,
            @Parameter(description = "Max latitude") @RequestParam @DecimalMin("-90") @DecimalMax("90") Double maxLat,
            @Parameter(description = "Min longitude") @RequestParam @DecimalMin("-180") @DecimalMax("180") Double minLon,
            @Parameter(description = "Max longitude") @RequestParam @DecimalMin("-180") @DecimalMax("180") Double maxLon) {
        return ResponseEntity.ok(service.findStationsInArea(
                Objects.requireNonNull(minLat, "Minimum latitude cannot be null"),
                Objects.requireNonNull(maxLat, "Maximum latitude cannot be null"),
                Objects.requireNonNull(minLon, "Minimum longitude cannot be null"),
                Objects.requireNonNull(maxLon, "Maximum longitude cannot be null")));
    }

    @Operation(summary = "Count stations by status", description = "Returns the count of stations with a specific status")
    @ApiResponse(responseCode = "200", description = "Station count")
    @GetMapping("/stats/count")
    public ResponseEntity<Map<String, Long>> getStationCountByStatus(
            @Parameter(description = "Station status") @RequestParam StationStatus status) {
        return ResponseEntity.ok(Map.of("count",
                service.getStationCountByStatus(Objects.requireNonNull(status, "Status cannot be null"))));
    }

    @Operation(summary = "Find nearby stations",
            description = "Finds stations within a radius using Haversine distance calculation")
    @ApiResponse(responseCode = "200", description = "List of nearby stations")
    @GetMapping("/search/nearby")
    public ResponseEntity<List<BaseStationDTO>> findStationsNearby(
            @Parameter(description = "Center latitude") @RequestParam @DecimalMin("-90") @DecimalMax("90") Double lat,
            @Parameter(description = "Center longitude") @RequestParam @DecimalMin("-180") @DecimalMax("180") Double lon,
            @Parameter(description = "Search radius in km") @RequestParam(defaultValue = "10") @Positive Double radiusKm) {
        return ResponseEntity.ok(service.findStationsNearPoint(
                Objects.requireNonNull(lat, "Latitude cannot be null"),
                Objects.requireNonNull(lon, "Longitude cannot be null"),
                Objects.requireNonNull(radiusKm, "Radius cannot be null")));
    }
}
