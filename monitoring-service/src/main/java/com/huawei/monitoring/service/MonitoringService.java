package com.huawei.monitoring.service;

import static com.huawei.monitoring.config.RedisConfig.METRICS_CACHE;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huawei.monitoring.dto.DailyMetricAggregateDTO;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.event.MetricRecordedEvent;
import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.repository.MetricDataRepository;

@Service
@SuppressWarnings("null") // Repository and stream operations guarantee non-null collections
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final MetricDataRepository repository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public MonitoringService(
            MetricDataRepository repository,
            MongoTemplate mongoTemplate,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Records a new metric to the database and publishes an event for downstream processing.
     *
     * <p>This method follows the Single Responsibility Principle by only handling metric persistence.
     * Side effects (WebSocket broadcasting, alert evaluation) are handled by event listeners
     * asynchronously after the metric is saved.
     *
     * @param dto the metric data to record
     * @return the saved metric with generated ID and timestamp
     */
    @Transactional
    @CacheEvict(value = METRICS_CACHE, allEntries = true)
    public MetricDataDTO recordMetric(MetricDataDTO dto) {
        MetricData metric = convertToEntity(dto);
        if (metric.getTimestamp() == null) {
            metric.setTimestamp(LocalDateTime.now());
        }

        // Auto-set correct unit if not provided or empty
        if (metric.getUnit() == null || metric.getUnit().isEmpty()) {
            metric.setUnit(com.huawei.monitoring.model.MetricUnit.getUnitForMetricType(metric.getMetricType()));
        }

        MetricDataDTO saved = convertToDTO(repository.save(metric));

        // Publish event for async processing (WebSocket broadcast, alert evaluation)
        eventPublisher.publishEvent(new MetricRecordedEvent(this, saved));

        log.debug("Recorded metric: type={}, station={}, value={}",
                saved.getMetricType(), saved.getStationId(), saved.getValue());

        return saved;
    }

    public List<MetricDataDTO> getMetricsByStation(Long stationId) {
        return repository.findByStationId(stationId).stream()
                        .map(this::convertToDTO)
                        .toList();
    }

    public List<MetricDataDTO> getMetricsByStationAndType(Long stationId, MetricType metricType) {
        return repository.findByStationIdAndMetricType(stationId, metricType).stream()
                        .map(this::convertToDTO)
                        .toList();
    }

    public List<MetricDataDTO> getMetricsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByTimestampBetween(start, end).stream()
                        .map(this::convertToDTO)
                        .toList();
    }

    /**
     * Gets metrics by time range with a limit to prevent OOM on large datasets.
     * Results are cached in Redis. Cache key rounds to day boundaries to maximize hit rate.
     *
     * @param start the start time (must not be null)
     * @param end the end time (must not be null)
     * @param limit maximum number of results to return
     * @param sortAsc true for ascending (oldest first), false for descending (newest first)
     * @return a list of metric DTOs (never null, may be empty)
     */
    @Cacheable(value = METRICS_CACHE, key = "#start.toLocalDate().toString() + '-' + #end.toLocalDate().toString() + '-' + #limit + '-' + #sortAsc")
    public List<MetricDataDTO> getMetricsByTimeRangeWithLimit(LocalDateTime start, LocalDateTime end, int limit, boolean sortAsc) {
        Sort.Direction direction = sortAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(0, limit, Sort.by(direction, "timestamp"));
        return repository.findByTimestampBetween(start, end, pageable).stream()
                        .map(this::convertToDTO)
                        .toList();
    }

    public List<MetricDataDTO> getMetricsByStationAndTimeRange(Long stationId, LocalDateTime start, LocalDateTime end) {
        return Objects.requireNonNull(
                repository.findMetricsByStationAndTimeRange(stationId, start, end).stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Metrics list cannot be null");
    }

    public List<MetricDataDTO> getMetricsAboveThreshold(MetricType metricType, Double threshold) {
        return Objects.requireNonNull(
                repository.findMetricsAboveThreshold(metricType, threshold).stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Metrics list cannot be null");
    }

    /**
     * Gets the latest metric for a single station.
     * This is a convenience method for single-station queries.
     * 
     * @param stationId the station ID (must not be null)
     * @return Optional containing the latest metric DTO, or empty if no metrics exist for the station
     */
    public Optional<MetricDataDTO> getLatestMetricByStation(Long stationId) {
        Objects.requireNonNull(stationId, "Station ID cannot be null");
        return repository.findTopByStationIdOrderByTimestampDesc(stationId)
                .map(this::convertToDTO);
    }

    /**
     * Gets the latest metrics for multiple stations in a single query.
     * This method efficiently fetches the latest metric for each station,
     * preventing N+1 query problems when fetching metrics for multiple stations.
     * 
     * <p>Performance: This single query replaces N individual queries,
     * significantly reducing database load and response time.
     * 
     * <p>Implementation: For each station, finds the metric with the latest timestamp.
     * Uses stream processing to group by station and select the most recent.
     * 
     * @param stationIds list of station IDs (must not be null, may be empty)
     * @return map of stationId -> latest MetricDataDTO (never null, may be empty)
     */
    public Map<Long, MetricDataDTO> getLatestMetricsByStations(List<Long> stationIds) {
        Objects.requireNonNull(stationIds, "Station IDs list cannot be null");
        
        if (stationIds.isEmpty()) {
            return getEmptyMetricsMap();
        }
        
        // Fetch all metrics for the requested stations, sorted by timestamp desc
        // This gets all metrics, we'll filter to latest per station below
        List<MetricData> allMetrics = repository.findByStationIdInOrderByTimestampDesc(stationIds);

        if (allMetrics.isEmpty()) {
            return getEmptyMetricsMap();
        }
        
        // Group by stationId and take the first (most recent) for each station
        // Since we sorted by timestamp desc, the first one per station is the latest
        // Filter out null MetricData entities to ensure type safety
        // Collectors.toMap never returns null, it returns an empty map if no elements
        Map<Long, MetricDataDTO> result = allMetrics.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        MetricData::getStationId,
                        this::convertToDTO,
                        (first, second) -> first  // Keep first (most recent) when duplicates occur
                ));
        // Assert non-null to satisfy static analyzer (toMap never returns null)
        return Objects.requireNonNull(result, "Collectors.toMap should never return null");
    }

    /**
     * Returns an empty map for metrics.
     * This helper method ensures type safety and satisfies null-safety requirements.
     * 
     * @return an empty, immutable map (never null)
     */
    private static Map<Long, MetricDataDTO> getEmptyMetricsMap() {
        return Collections.<Long, MetricDataDTO>emptyMap();
    }

    private MetricData convertToEntity(MetricDataDTO dto) {
        MetricData metric = new MetricData();
        metric.setStationId(dto.getStationId());
        metric.setStationName(dto.getStationName());
        metric.setMetricType(dto.getMetricType());
        metric.setValue(dto.getValue());
        metric.setUnit(dto.getUnit());
        metric.setTimestamp(dto.getTimestamp());
        metric.setStatus(dto.getStatus());
        return metric;
    }

    private MetricDataDTO convertToDTO(MetricData metric) {
        MetricDataDTO dto = new MetricDataDTO();
        dto.setId(metric.getId());
        dto.setStationId(metric.getStationId());
        dto.setStationName(metric.getStationName());
        dto.setMetricType(metric.getMetricType());
        dto.setValue(metric.getValue());
        dto.setUnit(metric.getUnit());
        dto.setTimestamp(metric.getTimestamp());
        dto.setStatus(metric.getStatus());
        return dto;
    }

    /**
     * Gets daily aggregated metrics for chart display.
     * Uses MongoDB aggregation to efficiently calculate daily averages.
     *
     * @param start the start date (must not be null)
     * @param end the end date (must not be null)
     * @return list of daily aggregates sorted by date ascending (never null)
     */
    @Cacheable(value = METRICS_CACHE, key = "'daily-' + #start.toLocalDate().toString() + '-' + #end.toLocalDate().toString()")
    public List<DailyMetricAggregateDTO> getDailyAggregates(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "Start time cannot be null");
        Objects.requireNonNull(end, "End time cannot be null");

        log.debug("Calculating daily aggregates from {} to {}", start, end);

        // MongoDB aggregation pipeline:
        // 1. Match documents in time range
        // 2. Group by date and metricType, calculate sum and count
        // 3. Group by date only, collecting all metric averages
        // 4. Sort by date ascending
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("timestamp").gte(start).lte(end)),
            Aggregation.project()
                .and("timestamp").extractYear().as("year")
                .and("timestamp").extractMonth().as("month")
                .and("timestamp").extractDayOfMonth().as("day")
                .and("metricType").as("metricType")
                .and("value").as("value"),
            Aggregation.group("year", "month", "day", "metricType")
                .sum("value").as("sum")
                .count().as("count"),
            Aggregation.project()
                .and("_id.year").as("year")
                .and("_id.month").as("month")
                .and("_id.day").as("day")
                .and("_id.metricType").as("metricType")
                .andExpression("sum / count").as("avg")
                .and("count").as("count"),
            Aggregation.sort(Sort.Direction.ASC, "year", "month", "day")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
            aggregation, "metric_data", Document.class
        );

        // Transform raw results into DTOs grouped by date
        Map<LocalDate, DailyMetricAggregateDTO> dateMap = new HashMap<>();

        for (Document doc : results.getMappedResults()) {
            int year = doc.getInteger("year", 0);
            int month = doc.getInteger("month", 0);
            int day = doc.getInteger("day", 0);
            String metricType = doc.getString("metricType");
            Number avgNum = (Number) doc.get("avg");
            double avg = avgNum != null ? avgNum.doubleValue() : 0.0;
            Number countNum = (Number) doc.get("count");
            long count = countNum != null ? countNum.longValue() : 0L;

            LocalDate date = LocalDate.of(year, month, day);

            DailyMetricAggregateDTO dto = dateMap.computeIfAbsent(date, d -> {
                DailyMetricAggregateDTO newDto = new DailyMetricAggregateDTO();
                newDto.setDate(d);
                newDto.setAverages(new HashMap<>());
                newDto.setCounts(new HashMap<>());
                return newDto;
            });

            if (metricType != null && dto.getAverages() != null && dto.getCounts() != null) {
                dto.getAverages().put(metricType, avg);
                dto.getCounts().put(metricType, count);
            }
        }

        List<DailyMetricAggregateDTO> sortedResults = new ArrayList<>(dateMap.values());
        sortedResults.sort((a, b) -> {
            if (a.getDate() == null || b.getDate() == null) return 0;
            return a.getDate().compareTo(b.getDate());
        });

        log.debug("Aggregated {} days of metrics", sortedResults.size());
        return sortedResults;
    }
}
