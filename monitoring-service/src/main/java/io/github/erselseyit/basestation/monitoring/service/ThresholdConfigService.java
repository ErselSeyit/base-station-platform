package io.github.erselseyit.basestation.monitoring.service;

import static io.github.erselseyit.basestation.common.constants.MessagingConstants.THRESHOLD_CONFIG_EXCHANGE;
import static io.github.erselseyit.basestation.common.constants.MessagingConstants.THRESHOLD_CONFIG_UPDATED_ROUTING_KEY;

import io.github.erselseyit.basestation.monitoring.model.ThresholdConfig;
import io.github.erselseyit.basestation.monitoring.repository.ThresholdConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing threshold configurations.
 *
 * Provides centralized threshold configuration with:
 * - Redis caching for fast reads (24h TTL)
 * - RabbitMQ broadcast for cache invalidation across services
 * - MongoDB persistence for durability
 *
 * ConfigType conventions:
 * - "health" - Health status thresholds
 * - "confidence" - Automation confidence thresholds
 * - "learning" - Learning algorithm thresholds
 * - "equipment.{type}" - Equipment-specific thresholds (e.g., "equipment.temperature")
 * - "risk.{level}" - Risk level configurations
 */
@Service
@SuppressWarnings("null") // Spring Data repositories and Optional operations guarantee non-null for present values
public class ThresholdConfigService {

    private static final Logger log = LoggerFactory.getLogger(ThresholdConfigService.class);

    public static final String THRESHOLD_CACHE = "thresholdConfigs";
    private static final String SYSTEM_USER = "system";

    private final ThresholdConfigRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public ThresholdConfigService(ThresholdConfigRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Get a single threshold configuration by type.
     * Results are cached in Redis.
     *
     * @param configType Configuration type (e.g., "health", "equipment.temperature")
     * @return Optional containing the config if found
     */
    @Cacheable(value = THRESHOLD_CACHE, key = "#configType")
    public Optional<ThresholdConfig> getConfig(String configType) {
        log.debug("Cache miss for configType: {}, loading from MongoDB", configType);
        return repository.findByConfigType(configType);
    }

    /**
     * Get a threshold value by config type and threshold name.
     *
     * @param configType Configuration type
     * @param thresholdName Threshold name within the config
     * @return Optional containing the threshold value if found
     */
    public Optional<Double> getThresholdValue(String configType, String thresholdName) {
        // Call repository directly to avoid self-call cache bypass (S6809)
        return repository.findByConfigType(configType)
                .map(config -> config.getThreshold(thresholdName));
    }

    /**
     * Get a threshold value with a default fallback.
     *
     * @param configType Configuration type
     * @param thresholdName Threshold name
     * @param defaultValue Default value if not found
     * @return Threshold value or default
     */
    public Double getThresholdValue(String configType, String thresholdName, Double defaultValue) {
        return getThresholdValue(configType, thresholdName).orElse(defaultValue);
    }

    /**
     * Get all threshold configurations.
     * Not cached due to potential size.
     *
     * @return List of all configurations
     */
    public List<ThresholdConfig> getAllConfigs() {
        return repository.findAll();
    }

    /**
     * Get all enabled configurations.
     *
     * @return List of enabled configurations
     */
    public List<ThresholdConfig> getEnabledConfigs() {
        return repository.findByEnabledTrue();
    }

    /**
     * Get all configurations with types starting with a prefix.
     *
     * @param prefix Prefix to match (e.g., "equipment." for all equipment thresholds)
     * @return List of matching configurations
     */
    public List<ThresholdConfig> getConfigsByPrefix(String prefix) {
        return repository.findByConfigTypeStartingWith("^" + prefix);
    }

    /**
     * Get all thresholds as a nested map structure.
     * Suitable for JSON serialization to Python client.
     *
     * @return Map with all thresholds organized by category
     */
    @Cacheable(value = THRESHOLD_CACHE, key = "'all'")
    public Map<String, Object> getAllThresholdsAsMap() {
        log.debug("Cache miss for all thresholds, loading from MongoDB");
        Map<String, Object> result = new HashMap<>();

        List<ThresholdConfig> configs = repository.findByEnabledTrue();
        for (ThresholdConfig config : configs) {
            String type = config.getConfigType();

            if (type.contains(".")) {
                // Hierarchical config (e.g., "equipment.temperature")
                String[] parts = type.split("\\.", 2);
                String category = parts[0];
                String subType = parts[1];

                @SuppressWarnings("unchecked")
                Map<String, Object> categoryMap = (Map<String, Object>) result.computeIfAbsent(
                        category, k -> new HashMap<String, Object>());

                Map<String, Object> configData = new HashMap<>();
                configData.putAll(config.getThresholds().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                configData.putAll(config.getMetadata());

                categoryMap.put(subType, configData);
            } else {
                // Top-level config (e.g., "health", "confidence")
                Map<String, Object> configData = new HashMap<>();
                configData.putAll(config.getThresholds().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                if (!config.getMetadata().isEmpty()) {
                    configData.put("_metadata", config.getMetadata());
                }
                result.put(type, configData);
            }
        }

        return result;
    }

    /**
     * Create or update a threshold configuration.
     * Invalidates cache and broadcasts update to other services.
     *
     * @param config The configuration to save
     * @param updatedBy Username of the person making the change
     * @return The saved configuration
     */
    @Transactional
    @CacheEvict(value = THRESHOLD_CACHE, allEntries = true)
    public ThresholdConfig saveConfig(ThresholdConfig config, String updatedBy) {
        config.setUpdatedBy(updatedBy);
        config.setUpdatedAt(LocalDateTime.now());

        ThresholdConfig saved = repository.save(config);
        log.info("Saved threshold config: type={}, updatedBy={}", config.getConfigType(), updatedBy);

        // Broadcast update to other services
        broadcastThresholdUpdate(saved.getConfigType());

        return saved;
    }

    /**
     * Update specific thresholds within a configuration.
     *
     * @param configType Configuration type to update
     * @param thresholds Map of threshold names to values to update
     * @param updatedBy Username of the person making the change
     * @return The updated configuration, or empty if config not found
     */
    @Transactional
    @CacheEvict(value = THRESHOLD_CACHE, allEntries = true)
    public Optional<ThresholdConfig> updateThresholds(
            String configType,
            Map<String, Double> thresholds,
            String updatedBy) {

        Optional<ThresholdConfig> existing = repository.findByConfigType(configType);
        if (existing.isEmpty()) {
            log.warn("Cannot update non-existent config: {}", configType);
            return Optional.empty();
        }

        ThresholdConfig config = existing.get();
        config.getThresholds().putAll(thresholds);
        config.setUpdatedBy(updatedBy);
        config.setUpdatedAt(LocalDateTime.now());

        ThresholdConfig saved = repository.save(config);
        log.info("Updated thresholds for config: type={}, keys={}, updatedBy={}",
                configType, thresholds.keySet(), updatedBy);

        broadcastThresholdUpdate(configType);

        return Optional.of(saved);
    }

    /**
     * Delete a threshold configuration.
     *
     * @param configType Configuration type to delete
     */
    @Transactional
    @CacheEvict(value = THRESHOLD_CACHE, allEntries = true)
    public void deleteConfig(String configType) {
        repository.deleteByConfigType(configType);
        log.info("Deleted threshold config: type={}", configType);
        broadcastThresholdUpdate(configType);
    }

    /**
     * Initialize default configurations if not present.
     * Called on application startup.
     */
    @Transactional
    public void initializeDefaults() {
        if (repository.count() == 0) {
            log.info("Initializing default threshold configurations...");

            saveConfigIfNotExists(ThresholdConfig.healthStatus(), SYSTEM_USER);
            saveConfigIfNotExists(ThresholdConfig.confidence(), SYSTEM_USER);
            saveConfigIfNotExists(ThresholdConfig.learning(), SYSTEM_USER);
            saveConfigIfNotExists(ThresholdConfig.equipmentTemperature(), SYSTEM_USER);
            saveConfigIfNotExists(ThresholdConfig.equipmentCpu(), SYSTEM_USER);
            saveConfigIfNotExists(ThresholdConfig.equipmentBatterySoc(), SYSTEM_USER);
            saveConfigIfNotExists(ThresholdConfig.equipmentSignalRsrp(), SYSTEM_USER);

            log.info("Default threshold configurations initialized");
        }
    }

    /**
     * Save a configuration only if it doesn't already exist.
     */
    private void saveConfigIfNotExists(ThresholdConfig config, String createdBy) {
        if (!repository.existsByConfigType(config.getConfigType())) {
            config.setUpdatedBy(createdBy);
            config.setUpdatedAt(LocalDateTime.now());
            repository.save(config);
            log.debug("Created default config: {}", config.getConfigType());
        }
    }

    /**
     * Broadcast a threshold update event to other services via RabbitMQ.
     */
    private void broadcastThresholdUpdate(String configType) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("configType", configType);
            event.put("timestamp", LocalDateTime.now().toString());
            event.put("action", "updated");

            rabbitTemplate.convertAndSend(
                    THRESHOLD_CONFIG_EXCHANGE,
                    THRESHOLD_CONFIG_UPDATED_ROUTING_KEY,
                    event);

            log.debug("Broadcast threshold update: configType={}", configType);
        } catch (Exception e) {
            // Don't fail the update if broadcast fails
            log.warn("Failed to broadcast threshold update for {}: {}", configType, e.getMessage());
        }
    }

    // Convenience methods for common threshold lookups

    /**
     * Get health status thresholds.
     */
    public Map<String, Double> getHealthThresholds() {
        // Call repository directly to avoid self-call cache bypass (S6809)
        return repository.findByConfigType("health")
                .map(ThresholdConfig::getThresholds)
                .orElseGet(() -> Map.of(
                        "critical", 0.4,
                        "warning", 0.6,
                        "degraded", 0.8));
    }

    /**
     * Get confidence automation thresholds.
     */
    public Map<String, Double> getConfidenceThresholds() {
        // Call repository directly to avoid self-call cache bypass (S6809)
        return repository.findByConfigType("confidence")
                .map(ThresholdConfig::getThresholds)
                .orElseGet(() -> Map.of(
                        "auto_apply", 0.95,
                        "suggest_apply", 0.85,
                        "manual_review", 0.70,
                        "low", 0.70));
    }

    /**
     * Get equipment thresholds by type.
     *
     * @param equipmentType Equipment type (e.g., "temperature", "cpu")
     * @return Thresholds map or empty map if not found
     */
    public Map<String, Double> getEquipmentThresholds(String equipmentType) {
        // Call repository directly to avoid self-call cache bypass (S6809)
        return repository.findByConfigType("equipment." + equipmentType)
                .map(ThresholdConfig::getThresholds)
                .orElseGet(HashMap::new);
    }
}
