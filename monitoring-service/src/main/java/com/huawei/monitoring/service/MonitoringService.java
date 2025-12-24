package com.huawei.monitoring.service;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.repository.MetricDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MonitoringService {

    private final MetricDataRepository repository;

    @Autowired
    public MonitoringService(MetricDataRepository repository) {
        this.repository = repository;
    }

    public MetricDataDTO recordMetric(MetricDataDTO dto) {
        MetricData metric = convertToEntity(dto);
        if (metric.getTimestamp() == null) {
            metric.setTimestamp(LocalDateTime.now());
        }
        MetricData saved = repository.save(metric);
        return convertToDTO(saved);
    }

    public List<MetricDataDTO> getMetricsByStation(Long stationId) {
        return repository.findByStationId(stationId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MetricDataDTO> getMetricsByStationAndType(Long stationId, MetricType metricType) {
        return repository.findByStationIdAndMetricType(stationId, metricType).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MetricDataDTO> getMetricsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByTimestampBetween(start, end).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MetricDataDTO> getMetricsByStationAndTimeRange(Long stationId, LocalDateTime start, LocalDateTime end) {
        return repository.findMetricsByStationAndTimeRange(stationId, start, end).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MetricDataDTO> getMetricsAboveThreshold(MetricType metricType, Double threshold) {
        return repository.findMetricsAboveThreshold(metricType, threshold).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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

