package io.github.erselseyit.basestation.monitoring.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import io.github.erselseyit.basestation.monitoring.config.TestSecurityConfig;
import io.github.erselseyit.basestation.monitoring.dto.MetricDataDTO;
import io.github.erselseyit.basestation.monitoring.model.MetricType;
import io.github.erselseyit.basestation.monitoring.service.MonitoringService;

/**
 * Tests for batch metrics endpoint in MonitoringController.
 *
 * Verifies that the batch endpoint correctly handles requests
 * and returns metrics for multiple stations.
 */
@WebMvcTest(MonitoringController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("MonitoringController Batch Endpoint Tests")
class MonitoringControllerBatchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MonitoringService monitoringService;

    @Test
    @DisplayName("Should return latest metrics for multiple stations")
    void getLatestMetricsBatch_MultipleStations_ReturnsMetrics() throws Exception {
        // Given
        List<Long> stationIds = Arrays.asList(1L, 2L, 3L);
        Map<Long, MetricDataDTO> metrics = new HashMap<>();
        
        MetricDataDTO metric1 = createMetricDTO(1L, MetricType.CPU_USAGE, 75.5);
        MetricDataDTO metric2 = createMetricDTO(2L, MetricType.MEMORY_USAGE, 60.0);
        MetricDataDTO metric3 = createMetricDTO(3L, MetricType.POWER_CONSUMPTION, 1500.0);
        
        metrics.put(1L, metric1);
        metrics.put(2L, metric2);
        metrics.put(3L, metric3);
        
        when(monitoringService.getLatestMetricsByStations(anyList())).thenReturn(metrics);

        // When & Then
        mockMvc.perform(post("/api/v1/metrics/batch/latest")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(Map.of("stationIds", stationIds)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1'].stationId").value(1L))
                .andExpect(jsonPath("$['2'].stationId").value(2L))
                .andExpect(jsonPath("$['3'].stationId").value(3L))
                .andExpect(jsonPath("$['1'].metricType").value("CPU_USAGE"))
                .andExpect(jsonPath("$['2'].metricType").value("MEMORY_USAGE"))
                .andExpect(jsonPath("$['3'].metricType").value("POWER_CONSUMPTION"));
    }

    @Test
    @DisplayName("Should return 400 for empty station IDs list")
    void getLatestMetricsBatch_EmptyList_ReturnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/metrics/batch/latest")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(Map.of("stationIds", Arrays.asList())))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for missing station IDs")
    void getLatestMetricsBatch_MissingStationIds_ReturnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/metrics/batch/latest")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should carry the NR band from a batch entry through to the stored metric")
    void recordMetricsBatch_PreservesBand() throws Exception {
        // The edge bridge uploads banded NR metrics (e.g. RSRP on N78). Regression
        // guard: the batch record path must not drop the band on the way to the DTO.
        String body = objectMapper.writeValueAsString(Map.of(
                "stationId", "1",
                "metrics", List.of(Map.of("type", "RSRP", "band", "N78", "value", -92.0))));

        mockMvc.perform(post("/api/v1/metrics/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(Objects.requireNonNull(body)))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<MetricDataDTO> captor =
                org.mockito.ArgumentCaptor.forClass(MetricDataDTO.class);
        verify(monitoringService).recordMetric(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                io.github.erselseyit.basestation.monitoring.model.Band.N78,
                captor.getValue().getBand());
    }

    private MetricDataDTO createMetricDTO(Long stationId, MetricType type, Double value) {
        MetricDataDTO dto = new MetricDataDTO();
        dto.setStationId(stationId);
        dto.setStationName("BS-" + stationId);
        dto.setMetricType(type);
        dto.setValue(value);
        dto.setUnit("%");
        return dto;
    }
}
