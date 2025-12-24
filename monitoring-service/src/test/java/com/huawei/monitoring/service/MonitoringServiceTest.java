package com.huawei.monitoring.service;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricData;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.repository.MetricDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private MetricDataRepository repository;

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
    void testRecordMetric_Success() {
        when(repository.save(any(MetricData.class))).thenReturn(testMetric);

        MetricDataDTO result = service.recordMetric(testDTO);

        assertNotNull(result);
        assertEquals(1L, result.getStationId());
        assertEquals(MetricType.CPU_USAGE, result.getMetricType());
        verify(repository, times(1)).save(any(MetricData.class));
    }

    @Test
    void testGetMetricsByStation() {
        MetricData metric2 = new MetricData();
        metric2.setId("2");
        metric2.setStationId(1L);
        metric2.setMetricType(MetricType.MEMORY_USAGE);
        metric2.setValue(60.0);

        when(repository.findByStationId(1L)).thenReturn(Arrays.asList(testMetric, metric2));

        List<MetricDataDTO> result = service.getMetricsByStation(1L);

        assertEquals(2, result.size());
        verify(repository, times(1)).findByStationId(1L);
    }

    @Test
    void testGetMetricsByStationAndType() {
        when(repository.findByStationIdAndMetricType(1L, MetricType.CPU_USAGE))
                .thenReturn(Arrays.asList(testMetric));

        List<MetricDataDTO> result = service.getMetricsByStationAndType(1L, MetricType.CPU_USAGE);

        assertEquals(1, result.size());
        assertEquals(MetricType.CPU_USAGE, result.get(0).getMetricType());
    }

    @Test
    void testGetMetricsAboveThreshold() {
        when(repository.findMetricsAboveThreshold(MetricType.CPU_USAGE, 70.0))
                .thenReturn(Arrays.asList(testMetric));

        List<MetricDataDTO> result = service.getMetricsAboveThreshold(MetricType.CPU_USAGE, 70.0);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getValue() > 70.0);
    }
}

