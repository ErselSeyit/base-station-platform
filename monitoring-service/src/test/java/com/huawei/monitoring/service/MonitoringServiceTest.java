package com.huawei.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.event.MetricRecordedEvent;
import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.repository.MetricDataRepository;
import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private MetricDataRepository repository;

    @Mock
    private MetricsWebSocketHandler webSocketHandler;

    @Mock
    private AlertingService alertingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MonitoringService service;

    private MetricData testMetric;
    private MetricDataDTO testDTO;

    @BeforeEach
    void setUp() {
        testMetric = new MetricData();
        testMetric.setId("1");
        testMetric.setStationId(1L);
        testMetric.setStationName("BS-001");
        testMetric.setMetricType(MetricType.CPU_USAGE);
        testMetric.setValue(75.5);
        testMetric.setUnit("%");
        testMetric.setTimestamp(LocalDateTime.now());

        testDTO = new MetricDataDTO();
        testDTO.setStationId(1L);
        testDTO.setStationName("BS-001");
        testDTO.setMetricType(MetricType.CPU_USAGE);
        testDTO.setValue(75.5);
        testDTO.setUnit("%");
    }

    @Test
    @SuppressWarnings("null")
    void recordMetric_savesAndReturnsMetric() {
        when(repository.save(any(MetricData.class))).thenReturn(testMetric);

        MetricDataDTO result = service.recordMetric(testDTO);

        assertNotNull(result);
        assertEquals(1L, result.getStationId());
        assertEquals(MetricType.CPU_USAGE, result.getMetricType());
        verify(repository).save(any(MetricData.class));
        // Event-driven: WebSocket broadcast and alert evaluation are handled by event listeners
        verify(eventPublisher).publishEvent(any(MetricRecordedEvent.class));
    }

    @Test
    void getMetricsByStation_returnsAllForStation() {
        MetricData metric2 = new MetricData();
        metric2.setId("2");
        metric2.setStationId(1L);
        metric2.setMetricType(MetricType.MEMORY_USAGE);
        metric2.setValue(60.0);

        when(repository.findByStationId(1L)).thenReturn(List.of(testMetric, metric2));

        List<MetricDataDTO> result = service.getMetricsByStation(1L);

        assertEquals(2, result.size());
    }

    @Test
    void getMetricsByStationAndType_filtersCorrectly() {
        when(repository.findByStationIdAndMetricType(1L, MetricType.CPU_USAGE))
                .thenReturn(List.of(testMetric));

        List<MetricDataDTO> result = service.getMetricsByStationAndType(1L, MetricType.CPU_USAGE);

        assertEquals(1, result.size());
        assertEquals(MetricType.CPU_USAGE, result.get(0).getMetricType());
    }

    @Test
    void getMetricsAboveThreshold_returnsOnlyAbove() {
        when(repository.findMetricsAboveThreshold(MetricType.CPU_USAGE, 70.0))
                .thenReturn(List.of(testMetric));

        List<MetricDataDTO> result = service.getMetricsAboveThreshold(MetricType.CPU_USAGE, 70.0);

        assertEquals(1, result.size());
        assertTrue(Objects.requireNonNull(result.get(0).getValue()) > 70.0);
    }
}
