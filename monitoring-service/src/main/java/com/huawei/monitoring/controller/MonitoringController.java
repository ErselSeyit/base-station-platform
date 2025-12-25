package com.huawei.monitoring.controller;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/v1/metrics")
@SuppressWarnings("null")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);
    
    private final MonitoringService service;

    public MonitoringController(MonitoringService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MetricDataDTO> recordMetric(@Valid @RequestBody MetricDataDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordMetric(dto));
    }

    @GetMapping
    public ResponseEntity<List<MetricDataDTO>> getAllMetrics(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        try {
            LocalDateTime start = parseDateTime(startTime);
            LocalDateTime end = parseDateTime(endTime);

            if (start != null && end != null) {
                return ResponseEntity.ok(service.getMetricsByTimeRange(start, end));
            }
            if (start != null) {
                return ResponseEntity.ok(service.getMetricsByTimeRange(start, LocalDateTime.now()));
            }
            // Default: last 24 hours
            LocalDateTime now = LocalDateTime.now();
            return ResponseEntity.ok(service.getMetricsByTimeRange(now.minusDays(1), now));
        } catch (Exception e) {
            log.error("Error fetching metrics", e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStation(@PathVariable Long stationId) {
        return ResponseEntity.ok(service.getMetricsByStation(stationId));
    }

    @GetMapping("/station/{stationId}/type/{metricType}")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStationAndType(
            @PathVariable Long stationId,
            @PathVariable MetricType metricType) {
        return ResponseEntity.ok(service.getMetricsByStationAndType(stationId, metricType));
    }

    @GetMapping("/time-range")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(service.getMetricsByTimeRange(start, end));
    }

    @GetMapping("/station/{stationId}/time-range")
    public ResponseEntity<List<MetricDataDTO>> getMetricsByStationAndTimeRange(
            @PathVariable Long stationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(service.getMetricsByStationAndTimeRange(stationId, start, end));
    }

    @GetMapping("/threshold")
    public ResponseEntity<List<MetricDataDTO>> getMetricsAboveThreshold(
            @RequestParam MetricType metricType,
            @RequestParam Double threshold) {
        return ResponseEntity.ok(service.getMetricsAboveThreshold(metricType, threshold));
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
