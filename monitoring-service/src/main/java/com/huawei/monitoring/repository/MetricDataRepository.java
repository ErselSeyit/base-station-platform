package com.huawei.monitoring.repository;

import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricDataRepository extends MongoRepository<MetricData, String> {
    
    List<MetricData> findByStationId(Long stationId);
    
    List<MetricData> findByStationIdAndMetricType(Long stationId, MetricType metricType);
    
    List<MetricData> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{ 'stationId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }")
    List<MetricData> findMetricsByStationAndTimeRange(Long stationId, LocalDateTime start, LocalDateTime end);
    
    @Query("{ 'metricType': ?0, 'value': { $gt: ?1 } }")
    List<MetricData> findMetricsAboveThreshold(MetricType metricType, Double threshold);
}

