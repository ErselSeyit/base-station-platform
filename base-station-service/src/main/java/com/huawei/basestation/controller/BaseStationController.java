package com.huawei.basestation.controller;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.service.BaseStationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stations")
public class BaseStationController {

    private final BaseStationService service;

    @Autowired
    public BaseStationController(BaseStationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BaseStationDTO> createStation(@Valid @RequestBody BaseStationDTO dto) {
        BaseStationDTO created = service.createStation(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
            BaseStationDTO updated = service.updateStation(id, dto);
            return ResponseEntity.ok(updated);
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
        List<BaseStationDTO> stations = service.findStationsInArea(minLat, maxLat, minLon, maxLon);
        return ResponseEntity.ok(stations);
    }

    @GetMapping("/stats/count")
    public ResponseEntity<Map<String, Long>> getStationCountByStatus(
            @RequestParam StationStatus status) {
        Long count = service.getStationCountByStatus(status);
        return ResponseEntity.ok(Map.of("count", count));
    }
}

