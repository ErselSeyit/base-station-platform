package com.huawei.basestation.controller;

import com.huawei.basestation.model.EdgeBridgeInstance;
import com.huawei.basestation.model.EdgeBridgeInstance.BridgeStatus;
import com.huawei.basestation.service.EdgeBridgeService;
import com.huawei.basestation.service.EdgeBridgeService.EdgeBridgeRegistration;
import com.huawei.basestation.service.EdgeBridgeService.BridgeStats;
import com.huawei.common.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for edge-bridge management.
 * Provides endpoints for bridge registration, heartbeat, and status queries.
 */
@RestController
@RequestMapping("/api/v1/edge-bridges")
@Validated
@Tag(name = "Edge Bridges", description = "Edge-bridge instance management")
@SecurityRequirement(name = "bearerAuth")
public class EdgeBridgeController {

    private final EdgeBridgeService service;

    public EdgeBridgeController(EdgeBridgeService service) {
        this.service = service;
    }

    /**
     * Register or update an edge-bridge instance.
     * Called by edge-bridge on startup and periodically.
     */
    @Operation(summary = "Register edge-bridge", description = "Registers a new edge-bridge or updates existing")
    @ApiResponse(responseCode = "200", description = "Bridge registered/updated")
    @PostMapping("/register")
    @PreAuthorize(Roles.HAS_SERVICE)
    public ResponseEntity<EdgeBridgeInstance> register(@Valid @RequestBody EdgeBridgeRegistration registration) {
        EdgeBridgeInstance bridge = service.register(registration);
        return ResponseEntity.ok(bridge);
    }

    /**
     * Receive heartbeat from edge-bridge.
     * Lightweight endpoint for frequent health checks.
     */
    @Operation(summary = "Heartbeat", description = "Records heartbeat from edge-bridge")
    @ApiResponse(responseCode = "200", description = "Heartbeat recorded")
    @ApiResponse(responseCode = "404", description = "Bridge not found")
    @PostMapping("/{bridgeId}/heartbeat")
    @PreAuthorize(Roles.HAS_SERVICE)
    public ResponseEntity<Map<String, String>> heartbeat(@PathVariable @NotBlank String bridgeId) {
        var result = service.heartbeat(bridgeId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        EdgeBridgeInstance bridge = result.get();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "bridgeId", bridgeId,
                "bridgeStatus", bridge.getStatus().name()
        ));
    }

    /**
     * Get all registered edge-bridges.
     */
    @Operation(summary = "List all bridges", description = "Returns all registered edge-bridges")
    @GetMapping
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<List<EdgeBridgeInstance>> getAllBridges() {
        return ResponseEntity.ok(service.getAllBridges());
    }

    /**
     * Get bridges by status.
     */
    @Operation(summary = "List bridges by status", description = "Returns bridges with specified status")
    @GetMapping("/status/{status}")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<List<EdgeBridgeInstance>> getBridgesByStatus(@PathVariable BridgeStatus status) {
        return ResponseEntity.ok(service.getBridgesByStatus(status));
    }

    /**
     * Get online bridges.
     */
    @Operation(summary = "List online bridges", description = "Returns all online edge-bridges")
    @GetMapping("/online")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<List<EdgeBridgeInstance>> getOnlineBridges() {
        return ResponseEntity.ok(service.getOnlineBridges());
    }

    /**
     * Get a specific edge-bridge by ID.
     */
    @Operation(summary = "Get bridge by ID", description = "Returns edge-bridge details")
    @ApiResponse(responseCode = "200", description = "Bridge found")
    @ApiResponse(responseCode = "404", description = "Bridge not found")
    @GetMapping("/{bridgeId}")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<EdgeBridgeInstance> getBridge(@PathVariable @NotBlank String bridgeId) {
        var result = service.getBridgeById(bridgeId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get());
    }

    /**
     * Get bridge managing a specific station.
     */
    @Operation(summary = "Get bridge for station", description = "Returns the edge-bridge managing a station")
    @GetMapping("/station/{stationId}")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<EdgeBridgeInstance> getBridgeForStation(@PathVariable Long stationId) {
        var result = service.getBridgeForStation(stationId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get());
    }

    /**
     * Manually mark bridge as offline.
     */
    @Operation(summary = "Mark offline", description = "Manually marks a bridge as offline")
    @PostMapping("/{bridgeId}/offline")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<EdgeBridgeInstance> markOffline(@PathVariable @NotBlank String bridgeId) {
        var result = service.markOffline(bridgeId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get());
    }

    /**
     * Gracefully stop bridge (called by bridge on shutdown).
     */
    @Operation(summary = "Stop bridge", description = "Gracefully stops a bridge")
    @PostMapping("/{bridgeId}/stop")
    @PreAuthorize(Roles.HAS_SERVICE)
    public ResponseEntity<EdgeBridgeInstance> stop(@PathVariable @NotBlank String bridgeId) {
        var result = service.markStopped(bridgeId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get());
    }

    /**
     * Update managed stations for a bridge.
     */
    @Operation(summary = "Update managed stations", description = "Updates the list of stations managed by bridge")
    @PutMapping("/{bridgeId}/stations")
    @PreAuthorize(Roles.HAS_SERVICE)
    public ResponseEntity<EdgeBridgeInstance> updateManagedStations(
            @PathVariable @NotBlank String bridgeId,
            @RequestBody List<Long> stationIds) {
        var result = service.updateManagedStations(bridgeId, stationIds);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.get());
    }

    /**
     * Get bridge statistics.
     */
    @Operation(summary = "Get statistics", description = "Returns edge-bridge summary statistics")
    @GetMapping("/stats")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<BridgeStats> getStats() {
        return ResponseEntity.ok(service.getStats());
    }
}
