package com.huawei.monitoring.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.monitoring.config.TestSecurityConfig;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.service.MonitoringService;

/**
 * Unit tests for MonitoringController.
 *
 * Tests all REST endpoints for recording and retrieving metrics.
 */
@WebMvcTest(MonitoringController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("MonitoringController Tests")
@SuppressWarnings("null") // Mockito matchers and test data return null placeholders
class MonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MonitoringService monitoringService;

    @Test
    @DisplayName("POST /api/v1/metrics - Should record metric successfully")
    void recordMetric_ValidRequest_ReturnsCreated() throws Exception {
        // Given
        MetricDataDTO inputDTO = createMetricDTO(1L, MetricType.CPU_USAGE, 75.5);
        MetricDataDTO savedDTO = createMetricDTO(1L, MetricType.CPU_USAGE, 75.5);
        savedDTO.setId("metric-123");
        savedDTO.setTimestamp(LocalDateTime.now());

        when(monitoringService.recordMetric(any(MetricDataDTO.class))).thenReturn(savedDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/metrics")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(inputDTO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("metric-123"))
                .andExpect(jsonPath("$.stationId").value(1L))
                .andExpect(jsonPath("$.metricType").value("CPU_USAGE"))
                .andExpect(jsonPath("$.value").value(75.5))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(monitoringService).recordMetric(any(MetricDataDTO.class));
    }

    @Test
    @DisplayName("POST /api/v1/metrics - Should return 400 for invalid metric")
    void recordMetric_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Given - missing required fields
        MetricDataDTO invalidDTO = new MetricDataDTO();

        // When & Then
        mockMvc.perform(post("/api/v1/metrics")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(invalidDTO))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/metrics - Should return all metrics with default time range")
    void getAllMetrics_NoParams_ReturnsMetricsForLast24Hours() throws Exception {
        // Given
        List<MetricDataDTO> metrics = Arrays.asList(
                createMetricDTO(1L, MetricType.CPU_USAGE, 75.5),
                createMetricDTO(2L, MetricType.MEMORY_USAGE, 60.0)
        );
        // Controller calls getMetricsByTimeRangeWithLimit with default limit=1000 and sortAsc=false
        when(monitoringService.getMetricsByTimeRangeWithLimit(
                any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class), any(Boolean.class)))
                .thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stationId").value(1L))
                .andExpect(jsonPath("$[1].stationId").value(2L));

        verify(monitoringService).getMetricsByTimeRangeWithLimit(
                any(LocalDateTime.class), any(LocalDateTime.class), any(Integer.class), any(Boolean.class));
    }

    @Test
    @DisplayName("GET /api/v1/metrics/station/{stationId} - Should return metrics for station")
    void getMetricsByStation_ValidStationId_ReturnsMetrics() throws Exception {
        // Given
        Long stationId = 1L;
        List<MetricDataDTO> metrics = Arrays.asList(
                createMetricDTO(stationId, MetricType.CPU_USAGE, 75.5),
                createMetricDTO(stationId, MetricType.MEMORY_USAGE, 60.0),
                createMetricDTO(stationId, MetricType.POWER_CONSUMPTION, 1500.0)
        );
        when(monitoringService.getMetricsByStation(stationId)).thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/station/{stationId}", stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].stationId").value(stationId))
                .andExpect(jsonPath("$[0].metricType").value("CPU_USAGE"))
                .andExpect(jsonPath("$[1].metricType").value("MEMORY_USAGE"))
                .andExpect(jsonPath("$[2].metricType").value("POWER_CONSUMPTION"));

        verify(monitoringService).getMetricsByStation(stationId);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/station/{stationId} - Should return empty list for station with no metrics")
    void getMetricsByStation_NoMetrics_ReturnsEmptyList() throws Exception {
        // Given
        Long stationId = 999L;
        when(monitoringService.getMetricsByStation(stationId)).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/station/{stationId}", stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(monitoringService).getMetricsByStation(stationId);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/station/{stationId}/type/{metricType} - Should return metrics by station and type")
    void getMetricsByStationAndType_Valid_ReturnsMetrics() throws Exception {
        // Given
        Long stationId = 1L;
        MetricType metricType = MetricType.CPU_USAGE;
        List<MetricDataDTO> metrics = Arrays.asList(
                createMetricDTO(stationId, metricType, 75.5),
                createMetricDTO(stationId, metricType, 80.0)
        );
        when(monitoringService.getMetricsByStationAndType(stationId, metricType)).thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/station/{stationId}/type/{metricType}", stationId, metricType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].metricType").value("CPU_USAGE"))
                .andExpect(jsonPath("$[0].value").value(75.5))
                .andExpect(jsonPath("$[1].value").value(80.0));

        verify(monitoringService).getMetricsByStationAndType(stationId, metricType);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/time-range - Should return metrics in time range")
    void getMetricsByTimeRange_ValidRange_ReturnsMetrics() throws Exception {
        // Given
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 2, 0, 0);
        List<MetricDataDTO> metrics = Arrays.asList(
                createMetricDTO(1L, MetricType.CPU_USAGE, 75.5),
                createMetricDTO(2L, MetricType.MEMORY_USAGE, 60.0)
        );
        when(monitoringService.getMetricsByTimeRange(start, end)).thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/time-range")
                .param("start", "2025-01-01T00:00:00")
                .param("end", "2025-01-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        verify(monitoringService).getMetricsByTimeRange(start, end);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/station/{stationId}/time-range - Should return station metrics in time range")
    void getMetricsByStationAndTimeRange_Valid_ReturnsMetrics() throws Exception {
        // Given
        Long stationId = 1L;
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 2, 0, 0);
        List<MetricDataDTO> metrics = Arrays.asList(
                createMetricDTO(stationId, MetricType.CPU_USAGE, 75.5)
        );
        when(monitoringService.getMetricsByStationAndTimeRange(stationId, start, end)).thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/station/{stationId}/time-range", stationId)
                .param("start", "2025-01-01T00:00:00")
                .param("end", "2025-01-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stationId").value(stationId));

        verify(monitoringService).getMetricsByStationAndTimeRange(stationId, start, end);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/threshold - Should return metrics above threshold")
    void getMetricsAboveThreshold_Valid_ReturnsMetrics() throws Exception {
        // Given
        MetricType metricType = MetricType.CPU_USAGE;
        Double threshold = 80.0;
        List<MetricDataDTO> metrics = Arrays.asList(
                createMetricDTO(1L, metricType, 85.0),
                createMetricDTO(2L, metricType, 90.5)
        );
        when(monitoringService.getMetricsAboveThreshold(metricType, threshold)).thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/threshold")
                .param("metricType", metricType.toString())
                .param("threshold", threshold.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].value").value(85.0))
                .andExpect(jsonPath("$[1].value").value(90.5));

        verify(monitoringService).getMetricsAboveThreshold(metricType, threshold);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/station/{stationId}/latest - Should return latest metric")
    void getLatestMetricByStation_MetricExists_ReturnsMetric() throws Exception {
        // Given
        Long stationId = 1L;
        MetricDataDTO latestMetric = createMetricDTO(stationId, MetricType.CPU_USAGE, 75.5);
        latestMetric.setTimestamp(LocalDateTime.now());
        when(monitoringService.getLatestMetricByStation(stationId)).thenReturn(Optional.of(latestMetric));

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/station/{stationId}/latest", stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(stationId))
                .andExpect(jsonPath("$.metricType").value("CPU_USAGE"))
                .andExpect(jsonPath("$.value").value(75.5))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(monitoringService).getLatestMetricByStation(stationId);
    }

    @Test
    @DisplayName("GET /api/v1/metrics/station/{stationId}/latest - Should return 404 when no metric exists")
    void getLatestMetricByStation_NoMetric_ReturnsNotFound() throws Exception {
        // Given
        Long stationId = 999L;
        when(monitoringService.getLatestMetricByStation(stationId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/metrics/station/{stationId}/latest", stationId))
                .andExpect(status().isNotFound());

        verify(monitoringService).getLatestMetricByStation(stationId);
    }

    private MetricDataDTO createMetricDTO(Long stationId, MetricType type, Double value) {
        MetricDataDTO dto = new MetricDataDTO();
        dto.setStationId(stationId);
        dto.setStationName("BS-" + stationId);
        dto.setMetricType(type);
        dto.setValue(value);
        dto.setUnit(type == MetricType.POWER_CONSUMPTION ? "W" : "%");
        dto.setTimestamp(LocalDateTime.now());
        dto.setStatus("NORMAL");
        return dto;
    }
}
