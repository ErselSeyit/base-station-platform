package io.github.erselseyit.basestation.common.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEventTest {

    @Test
    void builderSetsAllFieldsCorrectly() {
        AlertEvent event = AlertEvent.builder()
                .alertRuleId("rule-1")
                .alertRuleName("High CPU")
                .stationId(42L)
                .stationName("Station-42")
                .metricType("CPU_USAGE")
                .metricValue(95.5)
                .threshold(90.0)
                .severity("critical")
                .message("CPU usage exceeds threshold")
                .problemId("PROB-123")
                .build();

        assertThat(event.getAlertRuleId()).isEqualTo("rule-1");
        assertThat(event.getAlertRuleName()).isEqualTo("High CPU");
        assertThat(event.getStationId()).isEqualTo(42L);
        assertThat(event.getStationName()).isEqualTo("Station-42");
        assertThat(event.getMetricType()).isEqualTo("CPU_USAGE");
        assertThat(event.getMetricValue()).isEqualTo(95.5);
        assertThat(event.getThreshold()).isEqualTo(90.0);
        assertThat(event.getSeverity()).isEqualTo("critical");
        assertThat(event.getMessage()).isEqualTo("CPU usage exceeds threshold");
        assertThat(event.getProblemId()).isEqualTo("PROB-123");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void defaultConstructorSetsTimestampToNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        AlertEvent event = new AlertEvent();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(event.getTimestamp()).isAfter(before);
        assertThat(event.getTimestamp()).isBefore(after);
    }

    @Test
    void builderSetsTimestampToNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        AlertEvent event = AlertEvent.builder().build();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(event.getTimestamp()).isAfter(before);
        assertThat(event.getTimestamp()).isBefore(after);
    }

    @Test
    void timestampSetterHandlesNullByDefaultingToNow() {
        AlertEvent event = new AlertEvent();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        event.setTimestamp(null);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(event.getTimestamp()).isAfter(before);
        assertThat(event.getTimestamp()).isBefore(after);
    }

    @Test
    void timestampSetterAcceptsExplicitValue() {
        AlertEvent event = new AlertEvent();
        LocalDateTime explicit = LocalDateTime.of(2025, 1, 15, 10, 30, 0);
        event.setTimestamp(explicit);

        assertThat(event.getTimestamp()).isEqualTo(explicit);
    }

    @Test
    void toStringIncludesAllFields() {
        AlertEvent event = AlertEvent.builder()
                .alertRuleId("rule-1")
                .alertRuleName("High CPU")
                .stationId(42L)
                .stationName("Station-42")
                .metricType("CPU_USAGE")
                .metricValue(95.5)
                .threshold(90.0)
                .severity("critical")
                .message("CPU usage exceeds threshold")
                .problemId("PROB-123")
                .build();

        String str = event.toString();
        assertThat(str).contains("alertRuleId='rule-1'");
        assertThat(str).contains("alertRuleName='High CPU'");
        assertThat(str).contains("stationId=42");
        assertThat(str).contains("stationName='Station-42'");
        assertThat(str).contains("metricType='CPU_USAGE'");
        assertThat(str).contains("metricValue=95.5");
        assertThat(str).contains("threshold=90.0");
        assertThat(str).contains("severity='critical'");
        assertThat(str).contains("message='CPU usage exceeds threshold'");
        assertThat(str).contains("problemId='PROB-123'");
        assertThat(str).contains("timestamp=");
    }

    @Test
    void builderWithNullFieldsIsOk() {
        AlertEvent event = AlertEvent.builder()
                .alertRuleId(null)
                .stationId(null)
                .metricType(null)
                .severity(null)
                .build();

        assertThat(event.getAlertRuleId()).isNull();
        assertThat(event.getStationId()).isNull();
        assertThat(event.getMetricType()).isNull();
        assertThat(event.getSeverity()).isNull();
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void settersWorkCorrectly() {
        AlertEvent event = new AlertEvent();
        event.setAlertRuleId("rule-2");
        event.setAlertRuleName("Low Battery");
        event.setStationId(99L);
        event.setStationName("Station-99");
        event.setMetricType("BATTERY_SOC");
        event.setMetricValue(15.0);
        event.setThreshold(20.0);
        event.setSeverity("warning");
        event.setMessage("Battery low");
        event.setProblemId("PROB-456");

        assertThat(event.getAlertRuleId()).isEqualTo("rule-2");
        assertThat(event.getAlertRuleName()).isEqualTo("Low Battery");
        assertThat(event.getStationId()).isEqualTo(99L);
        assertThat(event.getStationName()).isEqualTo("Station-99");
        assertThat(event.getMetricType()).isEqualTo("BATTERY_SOC");
        assertThat(event.getMetricValue()).isEqualTo(15.0);
        assertThat(event.getThreshold()).isEqualTo(20.0);
        assertThat(event.getSeverity()).isEqualTo("warning");
        assertThat(event.getMessage()).isEqualTo("Battery low");
        assertThat(event.getProblemId()).isEqualTo("PROB-456");
    }
}
