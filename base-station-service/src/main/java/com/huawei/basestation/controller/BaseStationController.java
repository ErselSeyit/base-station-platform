package com.huawei.basestation.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/v1/stations")
@SuppressWarnings("null")
public class BaseStationController {

    private final BaseStationService service;

    public BaseStationController(BaseStationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BaseStationDTO> createStation(@Valid @RequestBody BaseStationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createStation(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseStationDTO> getStationById(@PathVariable Long id) {
        return service.getStationById(id)
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
    public ResponseEntity<BaseStationDTO> updateStation(
            @PathVariable Long id,
            @Valid @RequestBody BaseStationDTO dto) {
        try {
            return ResponseEntity.ok(service.updateStation(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable Long id) {
        try {
            service.deleteStation(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search/area")
    public ResponseEntity<List<BaseStationDTO>> findStationsInArea(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLon,
            @RequestParam Double maxLon) {
        return ResponseEntity.ok(service.findStationsInArea(minLat, maxLat, minLon, maxLon));
    }

    @GetMapping("/stats/count")
    public ResponseEntity<Map<String, Long>> getStationCountByStatus(@RequestParam StationStatus status) {
        return ResponseEntity.ok(Map.of("count", service.getStationCountByStatus(status)));
    }

    /**
     * Find stations within a radius of a point (Haversine distance).
     * 
     * Example: /api/v1/stations/search/nearby?lat=40.7128&lon=-74.0060&radiusKm=5
     */
    @GetMapping("/search/nearby")
    public ResponseEntity<List<BaseStationDTO>> findStationsNearby(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(defaultValue = "10") Double radiusKm) {
        return ResponseEntity.ok(service.findStationsNearPoint(lat, lon, radiusKm));
    }
}
