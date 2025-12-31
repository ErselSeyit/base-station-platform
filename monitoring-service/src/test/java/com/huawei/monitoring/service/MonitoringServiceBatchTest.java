package com.huawei.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.repository.MetricDataRepository;
import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

/**
 * Tests for batch metrics functionality in MonitoringService.
 * 
 * Verifies that the batch endpoint correctly fetches latest metrics
 * for multiple stations in a single query, preventing N+1 problems.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringService Batch Metrics Tests")
@SuppressWarnings("null") // LocalDateTime.now() and its methods are guaranteed non-null
class MonitoringServiceBatchTest {

    @Mock
    private MetricDataRepository repository;

    @Mock
    private MetricsWebSocketHandler webSocketHandler;

    @Mock
    private AlertingService alertingService;

    @InjectMocks
    private MonitoringService monitoringService;

    private MetricData metric1;
    private MetricData metric2;
    private MetricData metric3;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        metric1 = createMetric(1L, MetricType.CPU_USAGE, 75.5, now);
        metric2 = createMetric(2L, MetricType.MEMORY_USAGE, 60.0, now.minusMinutes(5));
        metric3 = createMetric(3L, MetricType.POWER_CONSUMPTION, 1500.0, now.minusMinutes(10));
    }

    private MetricData createMetric(Long stationId, MetricType type, Double value, LocalDateTime timestamp) {
        MetricData metric = new MetricData();
        metric.setId("metric-" + stationId);
        metric.setStationId(stationId);
        metric.setStationName("BS-" + stationId);
        metric.setMetricType(type);
        metric.setValue(value);
        metric.setUnit("%");
        metric.setTimestamp(timestamp);
        return metric;
    }

    @Test
    @DisplayName("Should return latest metrics for multiple stations in single query")
    void getLatestMetricsByStations_MultipleStations_ReturnsLatestMetrics() {
        // Given
        List<Long> stationIds = List.of(1L, 2L, 3L);
        List<MetricData> allMetrics = Arrays.asList(metric1, metric2, metric3);
        
        when(repository.findByStationIdInOrderByTimestampDesc(stationIds))
                .thenReturn(allMetrics);

        // When
        Map<Long, MetricDataDTO> result = monitoringService.getLatestMetricsByStations(stationIds);

        // Then
        assertThat(result)
                .hasSize(3)
                .containsKey(1L)
                .containsKey(2L)
                .containsKey(3L);
        assertThat(result.get(1L).getMetricType()).isEqualTo(MetricType.CPU_USAGE);
        assertThat(result.get(2L).getMetricType()).isEqualTo(MetricType.MEMORY_USAGE);
        assertThat(result.get(3L).getMetricType()).isEqualTo(MetricType.POWER_CONSUMPTION);
    }

    @Test
    @DisplayName("Should return empty map for empty station list")
    void getLatestMetricsByStations_EmptyList_ReturnsEmptyMap() {
        // When
        Map<Long, MetricDataDTO> result = monitoringService.getLatestMetricsByStations(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty map when no metrics found")
    void getLatestMetricsByStations_NoMetrics_ReturnsEmptyMap() {
        // Given
        List<Long> stationIds = List.of(1L, 2L);
        when(repository.findByStationIdInOrderByTimestampDesc(stationIds))
                .thenReturn(List.of());

        // When
        Map<Long, MetricDataDTO> result = monitoringService.getLatestMetricsByStations(stationIds);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle single station request")
    void getLatestMetricsByStations_SingleStation_ReturnsSingleMetric() {
        // Given
        List<Long> stationIds = List.of(1L);
        when(repository.findByStationIdInOrderByTimestampDesc(stationIds))
                .thenReturn(Collections.singletonList(metric1));

        // When
        Map<Long, MetricDataDTO> result = monitoringService.getLatestMetricsByStations(stationIds);

        // Then
        assertThat(result)
                .hasSize(1)
                .containsKey(1L);
        assertThat(result.get(1L).getStationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should select latest metric when multiple metrics exist for same station")
    void getLatestMetricsByStations_MultipleMetricsPerStation_SelectsLatest() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        MetricData older = createMetric(1L, MetricType.CPU_USAGE, 70.0, now.minusHours(1));
        MetricData newer = createMetric(1L, MetricType.CPU_USAGE, 75.5, now);
        
        List<Long> stationIds = List.of(1L);
        // Repository returns sorted by timestamp desc, so newer comes first
        when(repository.findByStationIdInOrderByTimestampDesc(stationIds))
                .thenReturn(Arrays.asList(newer, older));

        // When
        Map<Long, MetricDataDTO> result = monitoringService.getLatestMetricsByStations(stationIds);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(1L).getValue()).isEqualTo(75.5); // Latest value
    }
}
