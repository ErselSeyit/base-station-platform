package io.github.erselseyit.basestation.monitoring.controller;

import io.github.erselseyit.basestation.common.security.Roles;
import io.github.erselseyit.basestation.monitoring.model.ThresholdConfig;
import io.github.erselseyit.basestation.monitoring.service.ThresholdConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API for managing threshold configurations.
 *
 * Provides centralized threshold configuration for both Java backend and
 * Python AI diagnostic service. Supports hot reload without service restart.
 *
 * Endpoints:
 * - GET /api/v1/thresholds - Get all thresholds as nested map (for Python client)
 * - GET /api/v1/thresholds/configs - Get all configs as list
 * - GET /api/v1/thresholds/{configType} - Get specific config
 * - POST /api/v1/thresholds - Create new config (ADMIN only)
 * - PUT /api/v1/thresholds/{configType} - Update config (OPERATOR+)
 * - DELETE /api/v1/thresholds/{configType} - Delete config (ADMIN only)
 */
@RestController
@RequestMapping("/api/v1/thresholds")
// Using ThresholdConfig directly is acceptable - it's a simple config document, not a complex entity
@SuppressWarnings("java:S4684")
public class ThresholdConfigController {

    private static final String MSG_CONFIG_TYPE_NULL = "Config type cannot be null";
    private static final String MSG_CONFIG_NULL = "Config cannot be null";
    private final ThresholdConfigService thresholdService;

    public ThresholdConfigController(ThresholdConfigService thresholdService) {
        this.thresholdService = thresholdService;
    }

    /**
     * Get all thresholds as a nested map structure.
     * Primary endpoint for Python AI diagnostic service.
     *
     * @return Map with all thresholds organized by category
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllThresholds() {
        return ResponseEntity.ok(thresholdService.getAllThresholdsAsMap());
    }

    /**
     * Get all threshold configurations as a list.
     *
     * @return List of all configurations
     */
    @GetMapping("/configs")
    public ResponseEntity<List<ThresholdConfig>> getAllConfigs() {
        return ResponseEntity.ok(thresholdService.getAllConfigs());
    }

    /**
     * Get all enabled configurations.
     *
     * @return List of enabled configurations
     */
    @GetMapping("/configs/enabled")
    public ResponseEntity<List<ThresholdConfig>> getEnabledConfigs() {
        return ResponseEntity.ok(thresholdService.getEnabledConfigs());
    }

    /**
     * Get configurations by prefix (e.g., "equipment." for all equipment thresholds).
     *
     * @param prefix Prefix to match
     * @return List of matching configurations
     */
    @GetMapping("/configs/prefix/{prefix}")
    public ResponseEntity<List<ThresholdConfig>> getConfigsByPrefix(@PathVariable String prefix) {
        return ResponseEntity.ok(thresholdService.getConfigsByPrefix(
                Objects.requireNonNull(prefix, "Prefix cannot be null")));
    }

    /**
     * Get a specific configuration by type.
     *
     * @param configType Configuration type (e.g., "health", "equipment.temperature")
     * @return The configuration or 404 if not found
     */
    @GetMapping("/{configType}")
    public ResponseEntity<ThresholdConfig> getConfig(@PathVariable String configType) {
        return thresholdService.getConfig(
                Objects.requireNonNull(configType, MSG_CONFIG_TYPE_NULL))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get health status thresholds specifically.
     *
     * @return Health thresholds map
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Double>> getHealthThresholds() {
        return ResponseEntity.ok(thresholdService.getHealthThresholds());
    }

    /**
     * Get confidence automation thresholds specifically.
     *
     * @return Confidence thresholds map
     */
    @GetMapping("/confidence")
    public ResponseEntity<Map<String, Double>> getConfidenceThresholds() {
        return ResponseEntity.ok(thresholdService.getConfidenceThresholds());
    }

    /**
     * Get equipment thresholds by type.
     *
     * @param equipmentType Equipment type (e.g., "temperature", "cpu")
     * @return Equipment thresholds map
     */
    @GetMapping("/equipment/{equipmentType}")
    public ResponseEntity<Map<String, Double>> getEquipmentThresholds(
            @PathVariable String equipmentType) {
        Map<String, Double> thresholds = thresholdService.getEquipmentThresholds(
                Objects.requireNonNull(equipmentType, "Equipment type cannot be null"));
        if (thresholds.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(thresholds);
    }

    /**
     * Create a new threshold configuration.
     * Requires ADMIN role.
     *
     * @param config The configuration to create
     * @param user The authenticated user
     * @return The created configuration
     */
    @PostMapping
    @PreAuthorize(Roles.HAS_ADMIN)
    public ResponseEntity<ThresholdConfig> createConfig(
            @RequestBody ThresholdConfig config,
            @AuthenticationPrincipal UserDetails user) {
        ThresholdConfig saved = thresholdService.saveConfig(
                Objects.requireNonNull(config, MSG_CONFIG_NULL),
                user.getUsername());
        return ResponseEntity.ok(saved);
    }

    /**
     * Update an existing threshold configuration.
     * Requires OPERATOR or higher role.
     *
     * @param configType Configuration type to update
     * @param config The updated configuration
     * @param user The authenticated user
     * @return The updated configuration or 404 if not found
     */
    @PutMapping("/{configType}")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<ThresholdConfig> updateConfig(
            @PathVariable String configType,
            @RequestBody ThresholdConfig config,
            @AuthenticationPrincipal UserDetails user) {
        // Ensure configType matches the path
        config.setConfigType(Objects.requireNonNull(configType, MSG_CONFIG_TYPE_NULL));

        ThresholdConfig saved = thresholdService.saveConfig(config, user.getUsername());
        return ResponseEntity.ok(saved);
    }

    /**
     * Update specific thresholds within a configuration.
     * Requires OPERATOR or higher role.
     *
     * @param configType Configuration type to update
     * @param thresholds Map of threshold names to values
     * @param user The authenticated user
     * @return The updated configuration or 404 if not found
     */
    @PutMapping("/{configType}/thresholds")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<ThresholdConfig> updateThresholds(
            @PathVariable String configType,
            @RequestBody Map<String, Double> thresholds,
            @AuthenticationPrincipal UserDetails user) {
        return thresholdService.updateThresholds(
                Objects.requireNonNull(configType, MSG_CONFIG_TYPE_NULL),
                Objects.requireNonNull(thresholds, "Thresholds cannot be null"),
                user.getUsername())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Delete a threshold configuration.
     * Requires ADMIN role.
     *
     * @param configType Configuration type to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{configType}")
    @PreAuthorize(Roles.HAS_ADMIN)
    public ResponseEntity<Void> deleteConfig(@PathVariable String configType) {
        thresholdService.deleteConfig(
                Objects.requireNonNull(configType, MSG_CONFIG_TYPE_NULL));
        return ResponseEntity.noContent().build();
    }

    /**
     * Initialize default configurations.
     * Requires ADMIN role.
     *
     * @return Status message
     */
    @PostMapping("/initialize")
    @PreAuthorize(Roles.HAS_ADMIN)
    public ResponseEntity<Map<String, String>> initializeDefaults() {
        thresholdService.initializeDefaults();
        return ResponseEntity.ok(Map.of("status", "initialized"));
    }
}
