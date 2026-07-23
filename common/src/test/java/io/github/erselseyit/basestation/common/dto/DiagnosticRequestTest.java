package io.github.erselseyit.basestation.common.dto;

import io.github.erselseyit.basestation.common.constants.DiagnosticConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticRequestTest {

    @Test
    void defaultConstructorGeneratesProbPrefixedId() {
        DiagnosticRequest request = new DiagnosticRequest();

        assertThat(request.getId()).startsWith(DiagnosticConstants.PROBLEM_ID_PREFIX);
    }

    @Test
    void defaultConstructorSetsTimestamp() {
        DiagnosticRequest request = new DiagnosticRequest();

        assertThat(request.getTimestamp()).isNotNull();
        assertThat(request.getTimestamp()).isNotEmpty();
    }

    @Test
    void defaultConstructorSetsEmptyMetrics() {
        DiagnosticRequest request = new DiagnosticRequest();

        assertThat(request.getMetrics()).isNotNull();
        assertThat(request.getMetrics()).isEmpty();
    }

    @Test
    void defaultConstructorSetsEmptyRawLogs() {
        DiagnosticRequest request = new DiagnosticRequest();

        assertThat(request.getRawLogs()).isEqualTo("");
    }

    @Test
    void builderCreatesWithAllFields() {
        Map<String, Object> metrics = Map.of("cpu", 95.0, "threshold", 90.0);

        DiagnosticRequest request = DiagnosticRequest.builder()
                .id("PRB-custom-id")
                .timestamp("2025-01-15T10:00:00Z")
                .stationId("STATION-42")
                .category("hardware")
                .severity("critical")
                .code("CPU_OVERHEAT")
                .message("CPU overheating")
                .metrics(metrics)
                .rawLogs("some logs here")
                .build();

        assertThat(request.getId()).isEqualTo("PRB-custom-id");
        assertThat(request.getTimestamp()).isEqualTo("2025-01-15T10:00:00Z");
        assertThat(request.getStationId()).isEqualTo("STATION-42");
        assertThat(request.getCategory()).isEqualTo("hardware");
        assertThat(request.getSeverity()).isEqualTo("critical");
        assertThat(request.getCode()).isEqualTo("CPU_OVERHEAT");
        assertThat(request.getMessage()).isEqualTo("CPU overheating");
        assertThat(request.getMetrics()).isEqualTo(metrics);
        assertThat(request.getRawLogs()).isEqualTo("some logs here");
    }

    @Test
    void builderWithNullIdAutoGenerates() {
        DiagnosticRequest request = DiagnosticRequest.builder()
                .id(null)
                .build();

        assertThat(request.getId()).startsWith(DiagnosticConstants.PROBLEM_ID_PREFIX);
    }

    @Test
    void builderWithNullTimestampAutoGenerates() {
        DiagnosticRequest request = DiagnosticRequest.builder()
                .timestamp(null)
                .build();

        assertThat(request.getTimestamp()).isNotNull();
        assertThat(request.getTimestamp()).isNotEmpty();
    }

    @Test
    void fromAlertEventMapsHardwareMetricsCorrectly() {
        for (String metricType : new String[]{"CPU_USAGE", "MEMORY_USAGE", "TEMPERATURE"}) {
            AlertEvent alert = AlertEvent.builder()
                    .stationId(1L)
                    .stationName("Station-1")
                    .metricType(metricType)
                    .metricValue(95.0)
                    .threshold(90.0)
                    .severity("critical")
                    .message("Threshold exceeded")
                    .build();

            DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

            assertThat(request.getCategory()).isEqualTo("hardware");
        }
    }

    @Test
    void fromAlertEventMapsCpuUsageToCorrectCode() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCode()).isEqualTo("CPU_OVERHEAT");
    }

    @Test
    void fromAlertEventMapsTemperatureToCorrectCode() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("TEMPERATURE")
                .metricValue(85.0)
                .threshold(70.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCode()).isEqualTo("CPU_OVERHEAT");
    }

    @Test
    void fromAlertEventMapsMemoryUsageToCorrectCode() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("MEMORY_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("warning")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCode()).isEqualTo("MEMORY_PRESSURE");
    }

    @Test
    void fromAlertEventMapsNetworkMetricsCorrectly() {
        for (String metricType : new String[]{"SIGNAL_STRENGTH", "LATENCY", "PACKET_LOSS"}) {
            AlertEvent alert = AlertEvent.builder()
                    .stationId(1L)
                    .stationName("Station-1")
                    .metricType(metricType)
                    .metricValue(50.0)
                    .threshold(40.0)
                    .severity("warning")
                    .build();

            DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

            assertThat(request.getCategory()).isEqualTo("network");
        }
    }

    @Test
    void fromAlertEventMapsSignalStrengthToCorrectCode() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("SIGNAL_STRENGTH")
                .metricValue(-110.0)
                .threshold(-100.0)
                .severity("warning")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCode()).isEqualTo("SIGNAL_DEGRADATION");
    }

    @Test
    void fromAlertEventMapsPowerMetricsCorrectly() {
        for (String metricType : new String[]{"POWER_CONSUMPTION", "VOLTAGE"}) {
            AlertEvent alert = AlertEvent.builder()
                    .stationId(1L)
                    .stationName("Station-1")
                    .metricType(metricType)
                    .metricValue(50.0)
                    .threshold(40.0)
                    .severity("warning")
                    .build();

            DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

            assertThat(request.getCategory()).isEqualTo("power");
        }
    }

    @Test
    void fromAlertEventMapsPowerConsumptionToCorrectCode() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("POWER_CONSUMPTION")
                .metricValue(50.0)
                .threshold(40.0)
                .severity("warning")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCode()).isEqualTo("HIGH_POWER_CONSUMPTION");
    }

    @Test
    void fromAlertEventDefaultsToSoftwareCategory() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("SOME_UNKNOWN_METRIC")
                .metricValue(50.0)
                .threshold(40.0)
                .severity("warning")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCategory()).isEqualTo("software");
    }

    @Test
    void fromAlertEventDefaultsUnknownMetricCodeToIssue() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("SOME_UNKNOWN_METRIC")
                .metricValue(50.0)
                .threshold(40.0)
                .severity("warning")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCode()).isEqualTo("SOME_UNKNOWN_METRIC_ISSUE");
    }

    @Test
    void fromAlertEventWithExplicitProblemIdUsesIt() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert, "PROB-explicit-id");

        assertThat(request.getId()).isEqualTo("PROB-explicit-id");
    }

    @Test
    void fromAlertEventWithNullProblemIdAutoGenerates() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert, null);

        assertThat(request.getId()).startsWith(DiagnosticConstants.PROBLEM_ID_PREFIX);
    }

    @Test
    void fromAlertEventWithNullMetricTypeUsesUnknown() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType(null)
                .metricValue(50.0)
                .threshold(40.0)
                .severity("warning")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getCategory()).isEqualTo("software");
        assertThat(request.getCode()).isEqualTo("UNKNOWN_ISSUE");
    }

    @Test
    void fromAlertEventWithNullSeverityUsesWarning() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity(null)
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getSeverity()).isEqualTo("warning");
    }

    @Test
    void fromAlertEventUsesStationNameWhenAvailable() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(42L)
                .stationName("MyStation")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getStationId()).isEqualTo("MyStation");
    }

    @Test
    void fromAlertEventFallsBackToStationIdWhenNameIsNull() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(42L)
                .stationName(null)
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getStationId()).isEqualTo("STATION-42");
    }

    @Test
    void fromAlertEventPopulatesMetricsMap() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getMetrics()).containsEntry("cpu_usage", 95.0);
        assertThat(request.getMetrics()).containsEntry("threshold", 90.0);
    }

    @Test
    void fromAlertEventPopulatesRawLogs() {
        AlertEvent alert = AlertEvent.builder()
                .stationId(1L)
                .stationName("Station-1")
                .metricType("CPU_USAGE")
                .metricValue(95.0)
                .threshold(90.0)
                .severity("critical")
                .alertRuleName("High CPU Alert")
                .message("CPU exceeded threshold")
                .build();

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);

        assertThat(request.getRawLogs()).contains("High CPU Alert");
        assertThat(request.getRawLogs()).contains("CPU exceeded threshold");
    }

    @Test
    void toStringIncludesKeyFields() {
        DiagnosticRequest request = DiagnosticRequest.builder()
                .id("PRB-test")
                .stationId("STATION-1")
                .category("hardware")
                .severity("critical")
                .code("CPU_OVERHEAT")
                .message("Overheating")
                .build();

        String str = request.toString();
        assertThat(str).contains("id='PRB-test'");
        assertThat(str).contains("stationId='STATION-1'");
        assertThat(str).contains("category='hardware'");
        assertThat(str).contains("severity='critical'");
        assertThat(str).contains("code='CPU_OVERHEAT'");
        assertThat(str).contains("message='Overheating'");
    }
}
