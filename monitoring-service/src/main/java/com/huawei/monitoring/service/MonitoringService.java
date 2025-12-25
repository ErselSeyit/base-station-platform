package com.huawei.monitoring.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.repository.MetricDataRepository;
import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

@Service
@SuppressWarnings("null")
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    
    private final MetricDataRepository repository;
    private final MetricsWebSocketHandler webSocketHandler;
    private final AlertingService alertingService;

    public MonitoringService(
            MetricDataRepository repository,
            MetricsWebSocketHandler webSocketHandler,
            AlertingService alertingService) {
        this.repository = repository;
        this.webSocketHandler = webSocketHandler;
        this.alertingService = alertingService;
    }

    public MetricDataDTO recordMetric(MetricDataDTO dto) {
        MetricData metric = convertToEntity(dto);
        if (metric.getTimestamp() == null) {
            metric.setTimestamp(LocalDateTime.now());
        }
        
        MetricDataDTO saved = convertToDTO(repository.save(metric));
        
        // Broadcast to WebSocket clients for real-time updates
        webSocketHandler.broadcastMetric(saved);
        
        // Check alerting rules
        alertingService.evaluateMetric(saved);
        
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

    public List<MetricDataDTO> getMetricsByStationAndTimeRange(Long stationId, LocalDateTime start, LocalDateTime end) {
        return repository.findMetricsByStationAndTimeRange(stationId, start, end).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<MetricDataDTO> getMetricsAboveThreshold(MetricType metricType, Double threshold) {
        return repository.findMetricsAboveThreshold(metricType, threshold).stream()
                .map(this::convertToDTO)
                .toList();
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
}
