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

