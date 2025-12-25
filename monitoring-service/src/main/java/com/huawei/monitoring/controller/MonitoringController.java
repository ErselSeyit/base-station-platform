package com.huawei.monitoring.controller;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.service.MonitoringService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
public class MonitoringController {

    private final MonitoringService service;

    @Autowired
    public MonitoringController(MonitoringService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MetricDataDTO> recordMetric(@Valid @RequestBody MetricDataDTO dto) {
        MetricDataDTO created = service.recordMetric(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<MetricDataDTO>> getAllMetrics(
            @RequestParam(name = "startTime", required = false) String startTimeStr,
            @RequestParam(name = "endTime", required = false) String endTimeStr) {
        try {
            LocalDateTime startTime = null;
            LocalDateTime endTime = null;
            
            if (startTimeStr != null && !startTimeStr.isEmpty()) {
                // Handle ISO 8601 format with or without 'Z' (UTC indicator)
                if (startTimeStr.endsWith("Z")) {
                    startTime = ZonedDateTime.parse(startTimeStr).toLocalDateTime();
                } else {
                    startTime = LocalDateTime.parse(startTimeStr);
                }
            }
            if (endTimeStr != null && !endTimeStr.isEmpty()) {
                // Handle ISO 8601 format with or without 'Z' (UTC indicator)
                if (endTimeStr.endsWith("Z")) {
                    endTime = ZonedDateTime.parse(endTimeStr).toLocalDateTime();
                } else {
                    endTime = LocalDateTime.parse(endTimeStr);
                }
            }
            
            if (startTime != null && endTime != null) {
                return ResponseEntity.ok(service.getMetricsByTimeRange(startTime, endTime));
            } else if (startTime != null) {
                // If only startTime is provided, use current time as endTime
                return ResponseEntity.ok(service.getMetricsByTimeRange(startTime, LocalDateTime.now()));
            } else {
                // If no parameters, return recent metrics from last 24 hours
                return ResponseEntity.ok(service.getMetricsByTimeRange(
                    LocalDateTime.now().minusDays(1), 
                    LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            // Log error and return empty list to prevent frontend errors
            e.printStackTrace();
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
}

