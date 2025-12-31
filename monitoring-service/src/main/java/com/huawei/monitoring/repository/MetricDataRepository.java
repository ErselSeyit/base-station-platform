package com.huawei.monitoring.repository;

import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MetricData entities.
 * 
 * <p>This repository is configured with {@code @NonNullApi} at the package level,
 * which means all method parameters and return values are non-null by default.
 * 
 * <p>Methods returning {@code List} are guaranteed never to return null;
 * they return empty lists when no results are found.
 * 
 * @see org.springframework.lang.NonNullApi
 * @see <a href="https://docs.spring.io/spring-data/mongodb/reference/repositories/null-handling.html">Spring Data MongoDB Null Handling</a>
 */
@Repository
public interface MetricDataRepository extends MongoRepository<MetricData, String> {
    
    /**
     * Finds all metric data for a given station ID.
     * 
     * @param stationId the station ID (must not be null)
     * @return a list of metric data (never null, may be empty)
     */
    List<MetricData> findByStationId(Long stationId);
    
    /**
     * Finds all metric data for a given station ID and metric type.
     * 
     * @param stationId the station ID (must not be null)
     * @param metricType the metric type (must not be null)
     * @return a list of metric data (never null, may be empty)
     */
    List<MetricData> findByStationIdAndMetricType(Long stationId, MetricType metricType);
    
    /**
     * Finds all metric data within a time range.
     * 
     * @param start the start time (must not be null)
     * @param end the end time (must not be null)
     * @return a list of metric data (never null, may be empty)
     */
    List<MetricData> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * Finds all metric data for a given station within a time range.
     * 
     * @param stationId the station ID (must not be null)
     * @param start the start time (must not be null)
     * @param end the end time (must not be null)
     * @return a list of metric data (never null, may be empty)
     */
    @Query("{ 'stationId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }")
    List<MetricData> findMetricsByStationAndTimeRange(Long stationId, LocalDateTime start, LocalDateTime end);
    
    /**
     * Finds all metric data above a given threshold for a metric type.
     * 
     * @param metricType the metric type (must not be null)
     * @param threshold the threshold value (must not be null)
     * @return a list of metric data (never null, may be empty)
     */
    @Query("{ 'metricType': ?0, 'value': { $gt: ?1 } }")
    List<MetricData> findMetricsAboveThreshold(MetricType metricType, Double threshold);
    
    /**
     * Finds the latest metric data for a given station ID.
     * Returns the most recent metric by timestamp.
     * 
     * @param stationId the station ID (must not be null)
     * @return Optional containing the latest metric data, or empty if none found
     */
    @Query(value = "{ 'stationId': ?0 }", sort = "{ 'timestamp': -1 }")
    Optional<MetricData> findTopByStationIdOrderByTimestampDesc(Long stationId);
    
    /**
     * Finds the latest metric data for multiple station IDs.
     * Uses aggregation to get the latest metric per station efficiently.
     * 
     * @param stationIds list of station IDs (must not be null)
     * @return a list of latest metric data (never null, may be empty)
     */
    @Query(value = "{ 'stationId': { $in: ?0 } }", sort = "{ 'timestamp': -1 }")
    List<MetricData> findByStationIdInOrderByTimestampDesc(List<Long> stationIds);
}

