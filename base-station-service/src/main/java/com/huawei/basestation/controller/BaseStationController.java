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
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.service.BaseStationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/stations")
@Validated
public class BaseStationController {

    private static final String STATION_ID_NULL_MESSAGE = "Station ID cannot be null";
    private static final String STATION_DTO_NULL_MESSAGE = "Station DTO cannot be null";

    private final BaseStationService service;

    public BaseStationController(BaseStationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<BaseStationDTO> createStation(@Valid @RequestBody BaseStationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createStation(Objects.requireNonNull(dto, STATION_DTO_NULL_MESSAGE)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseStationDTO> getStationById(@PathVariable Long id) {
        return service.getStationById(Objects.requireNonNull(id, STATION_ID_NULL_MESSAGE))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<BaseStationDTO>> getAllStations(
            @RequestParam(required = false) StationStatus status,
            @RequestParam(required = false) StationType type) {
        if (status != null) {
            return ResponseEntity.ok(service.getStationsByStatus(status));
        }
        if (type != null) {
            return ResponseEntity.ok(service.getStationsByType(type));
        }
        return ResponseEntity.ok(service.getAllStations());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<BaseStationDTO> updateStation(
            @PathVariable Long id,
            @Valid @RequestBody BaseStationDTO dto) {
        try {
            return ResponseEntity.ok(service.updateStation(
                    Objects.requireNonNull(id, STATION_ID_NULL_MESSAGE),
                    Objects.requireNonNull(dto, STATION_DTO_NULL_MESSAGE)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStation(@PathVariable Long id) {
        try {
            service.deleteStation(Objects.requireNonNull(id, STATION_ID_NULL_MESSAGE));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search/area")
    public ResponseEntity<List<BaseStationDTO>> findStationsInArea(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") Double minLat,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") Double maxLat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") Double minLon,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") Double maxLon) {
        return ResponseEntity.ok(service.findStationsInArea(
                Objects.requireNonNull(minLat, "Minimum latitude cannot be null"),
                Objects.requireNonNull(maxLat, "Maximum latitude cannot be null"),
                Objects.requireNonNull(minLon, "Minimum longitude cannot be null"),
                Objects.requireNonNull(maxLon, "Maximum longitude cannot be null")));
    }

    @GetMapping("/stats/count")
    public ResponseEntity<Map<String, Long>> getStationCountByStatus(@RequestParam StationStatus status) {
        return ResponseEntity.ok(Map.of("count",
                service.getStationCountByStatus(Objects.requireNonNull(status, "Status cannot be null"))));
    }

    /**
     * Find stations within a radius of a point (Haversine distance).
     *
     * Example: /api/v1/stations/search/nearby?lat=40.7128&lon=-74.0060&radiusKm=5
     */
    @GetMapping("/search/nearby")
    public ResponseEntity<List<BaseStationDTO>> findStationsNearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") Double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") Double lon,
            @RequestParam(defaultValue = "10") @Positive Double radiusKm) {
        return ResponseEntity.ok(service.findStationsNearPoint(
                Objects.requireNonNull(lat, "Latitude cannot be null"),
                Objects.requireNonNull(lon, "Longitude cannot be null"),
                Objects.requireNonNull(radiusKm, "Radius cannot be null")));
    }
}
