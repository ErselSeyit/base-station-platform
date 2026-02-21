package com.huawei.monitoring.repository;

import com.huawei.monitoring.model.ThresholdConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ThresholdConfig entities.
 *
 * Provides access to threshold configurations stored in MongoDB.
 */
@Repository
public interface ThresholdConfigRepository extends MongoRepository<ThresholdConfig, String> {

    /**
     * Find configuration by type.
     * @param configType The configuration type (e.g., "health", "confidence", "equipment.temperature")
     * @return Optional containing the config if found
     */
    Optional<ThresholdConfig> findByConfigType(String configType);

    /**
     * Find all configurations with types starting with a prefix.
     * Useful for getting all equipment thresholds (prefix: "equipment.").
     *
     * @param prefix The prefix to match (e.g., "equipment.")
     * @return List of matching configurations
     */
    @Query("{ 'configType': { $regex: ?0, $options: '' } }")
    List<ThresholdConfig> findByConfigTypeStartingWith(String prefix);

    /**
     * Find all enabled configurations.
     * @return List of enabled configurations
     */
    List<ThresholdConfig> findByEnabledTrue();

    /**
     * Find all enabled configurations with types starting with a prefix.
     *
     * @param prefix The prefix to match
     * @return List of matching enabled configurations
     */
    @Query("{ 'configType': { $regex: ?0, $options: '' }, 'enabled': true }")
    List<ThresholdConfig> findByConfigTypeStartingWithAndEnabledTrue(String prefix);

    /**
     * Delete configuration by type.
     * @param configType The configuration type to delete
     */
    void deleteByConfigType(String configType);

    /**
     * Check if a configuration type exists.
     * @param configType The configuration type to check
     * @return true if exists
     */
    boolean existsByConfigType(String configType);

    /**
     * Count configurations by type prefix.
     * @param prefix The prefix to match
     * @return Count of matching configurations
     */
    @Query(value = "{ 'configType': { $regex: ?0, $options: '' } }", count = true)
    long countByConfigTypeStartingWith(String prefix);
}
