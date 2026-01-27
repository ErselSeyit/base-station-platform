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
import com.huawei.monitoring.service.MonitoringService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/v1/metrics")
@PreAuthorize("isAuthenticated()")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);
    private static final String STATION_ID_NULL_MESSAGE = "Station ID cannot be null";
    private static final String START_TIME_NULL_MESSAGE = "Start time cannot be null";

    private final MonitoringService service;

    public MonitoringController(MonitoringService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<MetricDataDTO> recordMetric(@Valid @RequestBody MetricDataDTO dto) {
        log.debug("Recording metric for station {}: type={}, value={}", 
                dto.getStationId(), dto.getMetricType(), dto.getValue());
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
     * Batch endpoint to record multiple metrics at once.
     * Used by edge bridges to upload metrics from MIPS devices efficiently.
     *
     * <p>Request body example:
     * <pre>
     * {
     *   "stationId": "BS-001",
     *   "metrics": [
     *     {"type": "TEMPERATURE", "value": 55.2, "timestamp": "2026-01-28T12:00:00"},
     *     {"type": "CPU_USAGE", "value": 25.5, "timestamp": "2026-01-28T12:00:00"}
     *   ]
     * }
     * </pre>
     *
     * @param request the batch record request
     * @return response with count of recorded metrics
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<BatchRecordResponse> recordMetricsBatch(
            @Valid @RequestBody BatchRecordRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");

        log.info("Recording batch of {} metrics for station {}",
                request.getMetrics().size(), request.getStationId());

        int recorded = 0;
        for (BatchMetricEntry entry : request.getMetrics()) {
            try {
                MetricDataDTO dto = new MetricDataDTO();
                dto.setStationId(parseStationId(request.getStationId()));
                dto.setMetricType(MetricType.valueOf(entry.getType()));
                dto.setValue(entry.getValue());
                if (entry.getTimestamp() != null) {
                    dto.setTimestamp(entry.getTimestamp());
                }
                service.recordMetric(dto);
                recorded++;
            } catch (Exception e) {
                log.warn("Failed to record metric {}: {}", entry.getType(), e.getMessage());
            }
        }

        log.info("Successfully recorded {} out of {} metrics for station {}",
                recorded, request.getMetrics().size(), request.getStationId());

        BatchRecordResponse response = new BatchRecordResponse();
        response.setReceived(recorded);
        response.setStatus(recorded > 0 ? "OK" : "FAILED");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Long parseStationId(String stationId) {
        if (stationId == null) {
            return null;
        }
        try {
            return Long.parseLong(stationId);
        } catch (NumberFormatException e) {
            String numericPart = stationId.replaceAll("\\D", "");
            return numericPart.isEmpty() ? 1L : Long.parseLong(numericPart);
        }
    }

    /**
     * Request DTO for batch metrics fetch endpoint.
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

    /**
     * Request DTO for batch metrics recording endpoint.
     */
    public static class BatchRecordRequest {
        @jakarta.validation.constraints.NotNull(message = "Station ID is required")
        private String stationId;

        @NotEmpty(message = "Metrics list cannot be empty")
        private List<BatchMetricEntry> metrics;

        public String getStationId() {
            return stationId;
        }

        public void setStationId(String stationId) {
            this.stationId = stationId;
        }

        public List<BatchMetricEntry> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<BatchMetricEntry> metrics) {
            this.metrics = metrics;
        }
    }

    /**
     * Single metric entry for batch recording.
     */
    public static class BatchMetricEntry {
        @jakarta.validation.constraints.NotNull(message = "Metric type is required")
        private String type;

        @jakarta.validation.constraints.NotNull(message = "Value is required")
        private Double value;

        private LocalDateTime timestamp;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Double getValue() {
            return value;
        }

        public void setValue(Double value) {
            this.value = value;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }

    /**
     * Response DTO for batch recording.
     */
    public static class BatchRecordResponse {
        private int received;
        private String status;

        public int getReceived() {
            return received;
        }

        public void setReceived(int received) {
            this.received = received;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
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
