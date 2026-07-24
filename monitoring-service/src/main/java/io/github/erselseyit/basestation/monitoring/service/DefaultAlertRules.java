package io.github.erselseyit.basestation.monitoring.service;

import java.util.ArrayList;
import java.util.List;

import io.github.erselseyit.basestation.monitoring.config.AlertThresholdConfig;
import io.github.erselseyit.basestation.monitoring.model.AlertRule;
import io.github.erselseyit.basestation.monitoring.model.AlertSeverity;
import io.github.erselseyit.basestation.monitoring.model.MetricType;

/**
 * Default alert-rule catalogue, extracted from AlertingService so the
 * service holds evaluation logic rather than ~220 lines of rule literals.
 */
final class DefaultAlertRules {

    private static final String UNIT_DBM = " dBm)";

    private DefaultAlertRules() { }

    /** All built-in rules, thresholds resolved from the given config. */
    static List<AlertRule> all(AlertThresholdConfig cfg) {
        List<AlertRule> rules = new ArrayList<>();
        rules.add(AlertRule.builder()
                        .id("cpu-critical")
                        .name("CPU Critical")
                        .metricType(MetricType.CPU_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getCpuCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("CPU usage exceeded " + (int) cfg.getCpuCritical() + "%")
                        .build());

        rules.add(AlertRule.builder()
                        .id("cpu-warning")
                        .name("CPU Warning")
                        .metricType(MetricType.CPU_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getCpuWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("CPU usage exceeded " + (int) cfg.getCpuWarning() + "%")
                        .build());

        rules.add(AlertRule.builder()
                        .id("memory-critical")
                        .name("Memory Critical")
                        .metricType(MetricType.MEMORY_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getMemoryCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Memory usage exceeded " + (int) cfg.getMemoryCritical() + "%")
                        .build());

        rules.add(AlertRule.builder()
                        .id("temperature-critical")
                        .name("Temperature Critical")
                        .metricType(MetricType.TEMPERATURE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getTemperatureCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Temperature exceeded safe threshold (" + (int) cfg.getTemperatureCritical() + "°C)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("signal-weak")
                        .name("Weak Signal")
                        .metricType(MetricType.SIGNAL_STRENGTH)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getSignalWeak())
                        .severity(AlertSeverity.WARNING)
                        .message("Signal strength below acceptable level (" + (int) cfg.getSignalWeak() + UNIT_DBM)
                        .build());

        // Memory warning
        rules.add(AlertRule.builder()
                        .id("memory-warning")
                        .name("Memory Warning")
                        .metricType(MetricType.MEMORY_USAGE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getMemoryWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Memory usage exceeded " + (int) cfg.getMemoryWarning() + "%")
                        .build());

        // Temperature warning
        rules.add(AlertRule.builder()
                        .id("temperature-warning")
                        .name("Temperature Warning")
                        .metricType(MetricType.TEMPERATURE)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getTemperatureWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Temperature above warning threshold (" + (int) cfg.getTemperatureWarning() + "°C)")
                        .build());

        // BLER (Block Error Rate) thresholds
        rules.add(AlertRule.builder()
                        .id("bler-critical")
                        .name("BLER Critical")
                        .metricType(MetricType.INITIAL_BLER)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getBlerCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Block error rate critical (" + (int) cfg.getBlerCritical() + "%)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("bler-warning")
                        .name("BLER Warning")
                        .metricType(MetricType.INITIAL_BLER)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getBlerWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Block error rate elevated (" + (int) cfg.getBlerWarning() + "%)")
                        .build());

        // Battery thresholds - alert when SOC is LOW
        rules.add(AlertRule.builder()
                        .id("battery-low")
                        .name("Battery Low")
                        .metricType(MetricType.BATTERY_SOC)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getBatteryLow())
                        .severity(AlertSeverity.WARNING)
                        .message("Battery state of charge low (" + (int) cfg.getBatteryLow() + "%)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("battery-critical")
                        .name("Battery Critical")
                        .metricType(MetricType.BATTERY_SOC)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getBatteryCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Battery state of charge critical (" + (int) cfg.getBatteryCritical() + "%)")
                        .build());

        // Latency thresholds
        rules.add(AlertRule.builder()
                        .id("latency-warning")
                        .name("Latency Warning")
                        .metricType(MetricType.LATENCY_PING)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getLatencyWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Network latency elevated (" + (int) cfg.getLatencyWarning() + "ms)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("latency-critical")
                        .name("Latency Critical")
                        .metricType(MetricType.LATENCY_PING)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getLatencyCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Network latency critical (" + (int) cfg.getLatencyCritical() + "ms)")
                        .build());

        // Throughput thresholds - alert when throughput is LOW
        rules.add(AlertRule.builder()
                        .id("throughput-low")
                        .name("Throughput Low")
                        .metricType(MetricType.DATA_THROUGHPUT)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getThroughputLow())
                        .severity(AlertSeverity.WARNING)
                        .message("Data throughput below threshold (" + (int) cfg.getThroughputLow() + " Mbps)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("throughput-critical")
                        .name("Throughput Critical")
                        .metricType(MetricType.DATA_THROUGHPUT)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getThroughputCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Data throughput critically low (" + (int) cfg.getThroughputCritical() + " Mbps)")
                        .build());

        // Power consumption thresholds
        rules.add(AlertRule.builder()
                        .id("power-high")
                        .name("Power High")
                        .metricType(MetricType.POWER_CONSUMPTION)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getPowerHigh())
                        .severity(AlertSeverity.WARNING)
                        .message("Power consumption high (" + (int) cfg.getPowerHigh() + "W)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("power-critical")
                        .name("Power Critical")
                        .metricType(MetricType.POWER_CONSUMPTION)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getPowerCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Power consumption critical (" + (int) cfg.getPowerCritical() + "W)")
                        .build());

        // Handover success rate - alert when below threshold
        rules.add(AlertRule.builder()
                        .id("handover-warning")
                        .name("Handover Warning")
                        .metricType(MetricType.HANDOVER_SUCCESS_RATE)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getHandoverWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Handover success rate below threshold (" + (int) cfg.getHandoverWarning() + "%)")
                        .build());

        rules.add(AlertRule.builder()
                        .id("handover-critical")
                        .name("Handover Critical")
                        .metricType(MetricType.HANDOVER_SUCCESS_RATE)
                        .operator(AlertRule.Operator.LESS_THAN)
                        .threshold(cfg.getHandoverCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Handover success rate critical (" + (int) cfg.getHandoverCritical() + "%)")
                        .build());

        // Interference level - alert when above threshold (dBm, higher is worse)
        rules.add(AlertRule.builder()
                        .id("interference-warning")
                        .name("Interference Warning")
                        .metricType(MetricType.INTERFERENCE_LEVEL)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getInterferenceWarning())
                        .severity(AlertSeverity.WARNING)
                        .message("Interference level elevated (" + (int) cfg.getInterferenceWarning() + UNIT_DBM)
                        .build());

        rules.add(AlertRule.builder()
                        .id("interference-critical")
                        .name("Interference Critical")
                        .metricType(MetricType.INTERFERENCE_LEVEL)
                        .operator(AlertRule.Operator.GREATER_THAN)
                        .threshold(cfg.getInterferenceCritical())
                        .severity(AlertSeverity.CRITICAL)
                        .message("Interference level critical (" + (int) cfg.getInterferenceCritical() + UNIT_DBM)
                        .build());
        return rules;
    }
}
