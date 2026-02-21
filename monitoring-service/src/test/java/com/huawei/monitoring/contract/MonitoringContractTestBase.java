package com.huawei.monitoring.contract;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;

import com.huawei.monitoring.controller.DiagnosticController;
import com.huawei.monitoring.controller.MonitoringController;
import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.DiagnosticSession;
import com.huawei.monitoring.model.DiagnosticStatus;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.service.DiagnosticSessionService;
import com.huawei.monitoring.service.MonitoringService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Base class for Spring Cloud Contract generated tests.
 * Uses standalone MockMvc setup with mocked services to avoid requiring
 * MongoDB, Redis, and RabbitMQ infrastructure.
 *
 * <p>Security is bypassed by not including the SecurityConfig filter chain.
 * The contracts include X-User-Name and X-User-Role headers to document
 * the expected authentication mechanism for consumers.
 */
public abstract class MonitoringContractTestBase {

    @BeforeEach
    void setup() {
        MonitoringService monitoringService = Mockito.mock(MonitoringService.class);
        DiagnosticSessionService diagnosticSessionService = Mockito.mock(DiagnosticSessionService.class);

        // Stub: GET /api/v1/metrics/station/1 returns a list with one metric
        MetricDataDTO cpuMetric = new MetricDataDTO();
        cpuMetric.setId("metric-001");
        cpuMetric.setStationId(1L);
        cpuMetric.setMetricType(MetricType.CPU_USAGE);
        cpuMetric.setValue(45.5);
        cpuMetric.setUnit("%");
        cpuMetric.setTimestamp(LocalDateTime.of(2026, 1, 15, 10, 30, 0));

        when(monitoringService.getMetricsByStation(eq(1L)))
                .thenReturn(List.of(cpuMetric));

        // Stub: GET /api/v1/metrics/station/99999 returns empty list
        when(monitoringService.getMetricsByStation(eq(99999L)))
                .thenReturn(List.of());

        // Stub: GET /api/v1/diagnostics/station/1 returns a list with one session
        DiagnosticSession session = new DiagnosticSession(
                "problem-001", 1L, "BS-001",
                "network", "high", "SIGNAL_DEGRADATION",
                "Signal degradation detected on station BS-001"
        );
        session.setId("session-001");
        session.setStatus(DiagnosticStatus.DETECTED);

        when(diagnosticSessionService.getSessionsForStation(eq(1L)))
                .thenReturn(List.of(session));

        // Build standalone MockMvc without security filters
        StandaloneMockMvcBuilder builder = MockMvcBuilders.standaloneSetup(
                new MonitoringController(monitoringService),
                new DiagnosticController(diagnosticSessionService)
        );

        RestAssuredMockMvc.standaloneSetup(builder);
    }
}
