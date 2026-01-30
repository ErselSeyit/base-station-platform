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
import org.springframework.lang.Nullable;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/v1/metrics")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Metrics", description = "Base station metrics collection and querying")
@SecurityRequirement(name = "bearerAuth")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);
    private static final String STATION_ID_NULL_MESSAGE = "Station ID cannot be null";
    private static final String START_TIME_NULL_MESSAGE = "Start time cannot be null";

    private final MonitoringService service;

    public MonitoringController(MonitoringService service) {
        this.service = service;
    }

    @Operation(summary = "Record metric", description = "Records a single metric data point")
    @ApiResponse(responseCode = "201", description = "Metric recorded")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<MetricDataDTO> recordMetric(
            @Parameter(description = "Metric data") @Valid @RequestBody MetricDataDTO dto) {
        log.debug("Recording metric for station {}: type={}, value={}", 
                dto.getStationId(), dto.getMetricType(), dto.getValue());
        MetricDataDTO recorded = service.recordMetric(Objects.requireNonNull(dto, "Metric DTO cannot be null"));
        log.info("Successfully recorded metric for station {}: type={}", 
                recorded.getStationId(), recorded.getMetricType());
        return ResponseEntity.status(HttpStatus.CREATED).body(recorded);
    }

    private static final int MAX_LIMIT = 10000;

    @Operation(summary = "Get metrics", description = "Retrieves metrics with optional time range filter and limit")
    @ApiResponse(responseCode = "200", description = "List of metrics (limited to prevent OOM)")
    @GetMapping
    public ResponseEntity<List<MetricDataDTO>> getAllMetrics(
            @Parameter(description = "Start time (ISO format)") @RequestParam(required = false) @Nullable String startTime,
            @Parameter(description = "End time (ISO format)") @RequestParam(required = false) @Nullable String endTime,
            @Parameter(description = "Max results (default 1000, max 10000)") @RequestParam(defaultValue = "1000") int limit,
            @Parameter(description = "Sort order: asc or desc (default desc)") @RequestParam(defaultValue = "desc") String sort) {
        // Let exceptions propagate to GlobalExceptionHandler instead of swallowing them
        int effectiveLimit = Math.clamp(limit, 1, MAX_LIMIT);
        boolean sortAsc = "asc".equalsIgnoreCase(sort);
        log.debug("Getting metrics with time range: start={}, end={}, limit={}, sort={}", startTime, endTime, effectiveLimit, sort);
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        LocalDateTime effectiveEnd = Objects.requireNonNull(end != null ? end : LocalDateTime.now());
        LocalDateTime effectiveStart = Objects.requireNonNull(start != null ? start : effectiveEnd.minusDays(1));

        return ResponseEntity.ok(service.getMetricsByTimeRangeWithLimit(effectiveStart, effectiveEnd, effectiveLimit, sortAsc));
    }

    @Operation(summary = "Get metrics by station", description = "Retrieves all metrics for a specific station")
    @ApiResponse(responseCode = "200", description = "List of metrics")
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStation(
            @Parameter(description = "Station ID") @PathVariable Long stationId) {
        log.debug("Getting metrics for station: {}", stationId);
        return ResponseEntity.ok(service.getMetricsByStation(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE)));
    }

    @Operation(summary = "Get metrics by station and type", description = "Retrieves metrics of a specific type for a station")
    @ApiResponse(responseCode = "200", description = "List of metrics")
    @GetMapping("/station/{stationId}/type/{metricType}")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStationAndType(
            @Parameter(description = "Station ID") @PathVariable Long stationId,
            @Parameter(description = "Metric type") @PathVariable MetricType metricType) {
        return ResponseEntity.ok(service.getMetricsByStationAndType(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE),
                Objects.requireNonNull(metricType, "Metric type cannot be null")));
    }

    @Operation(summary = "Get metrics by time range", description = "Retrieves metrics within a specific time range")
    @ApiResponse(responseCode = "200", description = "List of metrics")
    @GetMapping("/time-range")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByTimeRange(
            @Parameter(description = "Start time (ISO format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "End time (ISO format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(service.getMetricsByTimeRange(
                Objects.requireNonNull(start, START_TIME_NULL_MESSAGE),
                Objects.requireNonNull(end, "End time cannot be null")));
    }

    @Operation(summary = "Get station metrics by time range", description = "Retrieves metrics for a station within a time range")
    @ApiResponse(responseCode = "200", description = "List of metrics")
    @GetMapping("/station/{stationId}/time-range")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStationAndTimeRange(
            @Parameter(description = "Station ID") @PathVariable Long stationId,
            @Parameter(description = "Start time (ISO format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "End time (ISO format)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(service.getMetricsByStationAndTimeRange(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE),
                Objects.requireNonNull(start, START_TIME_NULL_MESSAGE),
                Objects.requireNonNull(end, "End time cannot be null")));
    }

    @Operation(summary = "Get metrics above threshold", description = "Retrieves metrics that exceed a specified threshold value")
    @ApiResponse(responseCode = "200", description = "List of metrics above threshold")
    @GetMapping("/threshold")
    public ResponseEntity<List<MetricDataDTO>> getMetricsAboveThreshold(
            @Parameter(description = "Metric type") @RequestParam MetricType metricType,
            @Parameter(description = "Threshold value") @RequestParam Double threshold) {
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
    @Operation(summary = "Get latest metric", description = "Retrieves the most recent metric for a station")
    @ApiResponse(responseCode = "200", description = "Latest metric found")
    @ApiResponse(responseCode = "404", description = "No metrics found for station")
    @GetMapping("/station/{stationId}/latest")
    public ResponseEntity<MetricDataDTO> getLatestMetricByStation(
            @Parameter(description = "Station ID") @PathVariable Long stationId) {
        log.debug("Getting latest metric for station: {}", stationId);
        Optional<MetricDataDTO> latest = service.getLatestMetricByStation(
                Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE));
        if (latest.isPresent()) {
            log.debug("Found latest metric for station {}: type={}", stationId, latest.get().getMetricType());
        } else {
            log.debug("No metrics found for station: {}", stationId);
        }
        return Objects.requireNonNull(
                latest.map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                "Response cannot be null");
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
    @Operation(summary = "Batch get latest metrics",
            description = "Retrieves latest metrics for multiple stations in a single request")
    @ApiResponse(responseCode = "200", description = "Map of station ID to latest metric")
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
    @Operation(summary = "Batch record metrics",
            description = "Records multiple metrics at once. Used by edge bridges for efficient metric upload. Returns detailed error information for any failed metrics.")
    @ApiResponse(responseCode = "201", description = "Metrics recorded (may include partial failures)")
    @ApiResponse(responseCode = "400", description = "All metrics failed to record")
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<BatchRecordResponse> recordMetricsBatch(
            @Parameter(description = "Batch of metrics") @Valid @RequestBody BatchRecordRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        List<BatchMetricEntry> metrics = Objects.requireNonNull(request.getMetrics(), "Metrics cannot be null");

        log.info("Recording batch of {} metrics for station {}",
                metrics.size(), request.getStationId());

        int recorded = 0;
        List<BatchRecordError> errors = new java.util.ArrayList<>();

        for (int i = 0; i < metrics.size(); i++) {
            BatchMetricEntry entry = metrics.get(i);
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
            } catch (IllegalArgumentException e) {
                String errorMsg = "Invalid metric type: " + entry.getType();
                log.warn("Failed to record metric at index {}: {}", i, errorMsg);
                errors.add(new BatchRecordError(i, entry.getType(), errorMsg));
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Failed to record metric {} at index {}: {}", entry.getType(), i, errorMsg);
                errors.add(new BatchRecordError(i, entry.getType(), errorMsg));
            }
        }

        log.info("Successfully recorded {} out of {} metrics for station {} ({} errors)",
                recorded, metrics.size(), request.getStationId(), errors.size());

        BatchRecordResponse response = new BatchRecordResponse();
        response.setReceived(recorded);
        response.setFailed(errors.size());
        response.setErrors(errors.isEmpty() ? null : errors);

        if (recorded == 0 && !metrics.isEmpty()) {
            response.setStatus("FAILED");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else if (!errors.isEmpty()) {
            response.setStatus("PARTIAL");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            response.setStatus("OK");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }

    @Nullable
    private Long parseStationId(@Nullable String stationId) {
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

        @Nullable
        public List<Long> getStationIds() {
            return stationIds;
        }

        public void setStationIds(@Nullable List<Long> stationIds) {
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

        @Nullable
        public String getStationId() {
            return stationId;
        }

        public void setStationId(@Nullable String stationId) {
            this.stationId = stationId;
        }

        @Nullable
        public List<BatchMetricEntry> getMetrics() {
            return metrics;
        }

        public void setMetrics(@Nullable List<BatchMetricEntry> metrics) {
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

        @Nullable
        public String getType() {
            return type;
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }

        @Nullable
        public Double getValue() {
            return value;
        }

        public void setValue(@Nullable Double value) {
            this.value = value;
        }

        @Nullable
        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(@Nullable LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }

    /**
     * Response DTO for batch recording with detailed error reporting.
     */
    public static class BatchRecordResponse {
        private int received;
        private int failed;
        private String status;
        private List<BatchRecordError> errors;

        public int getReceived() {
            return received;
        }

        public void setReceived(int received) {
            this.received = received;
        }

        public int getFailed() {
            return failed;
        }

        public void setFailed(int failed) {
            this.failed = failed;
        }

        @Nullable
        public String getStatus() {
            return status;
        }

        public void setStatus(@Nullable String status) {
            this.status = status;
        }

        @Nullable
        public List<BatchRecordError> getErrors() {
            return errors;
        }

        public void setErrors(@Nullable List<BatchRecordError> errors) {
            this.errors = errors;
        }
    }

    /**
     * Error details for a single failed metric in batch recording.
     */
    public static class BatchRecordError {
        private int index;
        private String metricType;
        private String error;

        public BatchRecordError() {}

        public BatchRecordError(int index, @Nullable String metricType, @Nullable String error) {
            this.index = index;
            this.metricType = metricType;
            this.error = error;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        @Nullable
        public String getMetricType() {
            return metricType;
        }

        public void setMetricType(@Nullable String metricType) {
            this.metricType = metricType;
        }

        @Nullable
        public String getError() {
            return error;
        }

        public void setError(@Nullable String error) {
            this.error = error;
        }
    }

    @Nullable
    private LocalDateTime parseDateTime(@Nullable String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        if (dateTimeStr.endsWith("Z")) {
            return ZonedDateTime.parse(dateTimeStr).toLocalDateTime();
        }
        return LocalDateTime.parse(dateTimeStr);
    }
}
