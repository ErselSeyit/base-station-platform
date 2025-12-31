package com.huawei.monitoring.controller;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.ratelimit.StationRateLimiter;
import com.huawei.monitoring.service.MonitoringService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/v1/metrics")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);
    private static final String STATION_ID_NULL_MESSAGE = "Station ID cannot be null";
    private static final String START_TIME_NULL_MESSAGE = "Start time cannot be null";

    private final MonitoringService service;
    private final StationRateLimiter rateLimiter;

    public MonitoringController(MonitoringService service, StationRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<?> recordMetric(@Valid @RequestBody MetricDataDTO dto) {
        log.debug("Recording metric for station {}: type={}, value={}",
                dto.getStationId(), dto.getMetricType(), dto.getValue());

        // Check per-station rate limit
        if (!rateLimiter.allowRequest(dto.getStationId())) {
            String message = String.format("Rate limit exceeded for station %d. Limit: %d requests per minute.",
                    dto.getStationId(), rateLimiter.getRequestsPerMinute());
            log.warn("Rate limit exceeded for station {}: current count={}",
                    dto.getStationId(), rateLimiter.getCurrentCount(dto.getStationId()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too Many Requests", "message", message));
        }

        MetricDataDTO recorded = service.recordMetric(Objects.requireNonNull(dto, "Metric DTO cannot be null"));
        log.info("Successfully recorded metric for station {}: type={}",
                recorded.getStationId(), recorded.getMetricType());
        return ResponseEntity.status(HttpStatus.CREATED).body(recorded);
    }

    @GetMapping
    public ResponseEntity<List<MetricDataDTO>> getAllMetrics(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        // Let exceptions propagate to GlobalExceptionHandler instead of swallowing them
        log.debug("Getting all metrics with time range: start={}, end={}", startTime, endTime);
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        if (start != null && end != null) {
            return ResponseEntity.ok(service.getMetricsByTimeRange(start, end));
        }
        if (start != null) {
            return ResponseEntity.ok(service.getMetricsByTimeRange(start,
                    Objects.requireNonNull(LocalDateTime.now(), "Current time cannot be null")));
        }
        // Default: last 24 hours
        LocalDateTime now = Objects.requireNonNull(LocalDateTime.now(), "Current time cannot be null");
        return ResponseEntity.ok(service.getMetricsByTimeRange(
                Objects.requireNonNull(now.minusDays(1), START_TIME_NULL_MESSAGE),
                now));
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStation(@PathVariable Long stationId) {
        log.debug("Getting metrics for station: {}", stationId);
        return ResponseEntity.ok(service.getMetricsByStation(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE)));
    }

    @GetMapping("/station/{stationId}/type/{metricType}")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStationAndType(
            @PathVariable Long stationId,
            @PathVariable MetricType metricType) {
        return ResponseEntity.ok(service.getMetricsByStationAndType(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE),
                Objects.requireNonNull(metricType, "Metric type cannot be null")));
    }

    @GetMapping("/time-range")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(service.getMetricsByTimeRange(
                Objects.requireNonNull(start, START_TIME_NULL_MESSAGE),
                Objects.requireNonNull(end, "End time cannot be null")));
    }

    @GetMapping("/station/{stationId}/time-range")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStationAndTimeRange(
            @PathVariable Long stationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(service.getMetricsByStationAndTimeRange(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE),
                Objects.requireNonNull(start, START_TIME_NULL_MESSAGE),
                Objects.requireNonNull(end, "End time cannot be null")));
    }

    @GetMapping("/threshold")
    public ResponseEntity<List<MetricDataDTO>> getMetricsAboveThreshold(
            @RequestParam MetricType metricType,
            @RequestParam Double threshold) {
        return ResponseEntity.ok(service.getMetricsAboveThreshold(
                Objects.requireNonNull(metricType, "Metric type cannot be null"),
                Objects.requireNonNull(threshold, "Threshold cannot be null")));
    }

    /**
     * Gets the latest metric for a single station.
     * 
     * @param stationId the station ID
     * @return the latest metric, or 404 if not found
     */
    @GetMapping("/station/{stationId}/latest")
    public ResponseEntity<MetricDataDTO> getLatestMetricByStation(@PathVariable Long stationId) {
        log.debug("Getting latest metric for station: {}", stationId);
        Optional<MetricDataDTO> latest = service.getLatestMetricByStation(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE));
        if (latest.isPresent()) {
            log.debug("Found latest metric for station {}: type={}", stationId, latest.get().getMetricType());
        } else {
            log.debug("No metrics found for station: {}", stationId);
        }
        return latest.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Batch endpoint to get latest metrics for multiple stations.
     * This prevents N+1 query problems by fetching all latest metrics in a single query.
     * 
     * <p>Request body example:
     * <pre>
     * {
     *   "stationIds": [1, 2, 3, 4, 5]
     * }
     * </pre>
     * 
     * <p>Response example:
     * <pre>
     * {
     *   "1": { "stationId": 1, "metricType": "CPU_USAGE", "value": 75.5, ... },
     *   "2": { "stationId": 2, "metricType": "MEMORY_USAGE", "value": 60.0, ... },
     *   ...
     * }
     * </pre>
     * 
     * @param request the batch request containing station IDs
     * @return map of stationId -> latest MetricDataDTO
     */
    @PostMapping("/batch/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'USER')")
    public ResponseEntity<Map<Long, MetricDataDTO>> getLatestMetricsBatch(
            @Valid @RequestBody BatchMetricsRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        List<Long> stationIds = Objects.requireNonNull(request.getStationIds(), "Station IDs cannot be null");
        
        log.debug("Getting latest metrics for {} stations", stationIds.size());
        Map<Long, MetricDataDTO> latestMetrics = service.getLatestMetricsByStations(stationIds);
        log.debug("Found latest metrics for {} out of {} requested stations", 
                latestMetrics.size(), stationIds.size());
        return ResponseEntity.ok(latestMetrics);
    }

    /**
     * Request DTO for batch metrics endpoint.
     */
    public static class BatchMetricsRequest {
        @NotEmpty(message = "Station IDs list cannot be empty")
        private List<Long> stationIds;

        public List<Long> getStationIds() {
            return stationIds;
        }

        public void setStationIds(List<Long> stationIds) {
            this.stationIds = stationIds;
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        if (dateTimeStr.endsWith("Z")) {
            return ZonedDateTime.parse(dateTimeStr).toLocalDateTime();
        }
        return LocalDateTime.parse(dateTimeStr);
    }
}
