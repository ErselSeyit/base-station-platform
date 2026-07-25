package io.github.erselseyit.basestation.monitoring.service;

import java.util.Optional;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps a station metric type to the diagnostic problem code and category that
 * the AI diagnostic service understands. Pure, stateless string mapping,
 * extracted from {@link DiagnosticSessionService} so it can be unit-tested in
 * isolation and reused without dragging in the session/repository machinery.
 */
@Component
public class DiagnosticProblemCodeMapper {

    /** Returned when the metric type is absent and no specific code applies. */
    static final String UNKNOWN_PROBLEM_CODE = "UNKNOWN";

    /**
     * Coarse problem category for a non-null metric type
     * (e.g. {@code "hardware"}, {@code "power"}, {@code "network"},
     * {@code "software"}). The caller is responsible for the null case.
     */
    public String categoryFor(String metricType) {
        return switch (metricType.toUpperCase()) {
            case "CPU_USAGE", "MEMORY_USAGE", "FAN_SPEED" -> "hardware";
            case "TEMPERATURE", "POWER_CONSUMPTION" -> "power";
            case "SIGNAL_STRENGTH", "DATA_THROUGHPUT", "CONNECTION_COUNT" -> "network";
            default -> "software";
        };
    }

    /**
     * Problem code for a possibly-null metric type. Returns {@link #UNKNOWN_PROBLEM_CODE}
     * when the type is absent, and matches the AI service's code vocabulary
     * otherwise (falling back to {@code <TYPE>_ISSUE} for unmapped types).
     */
    public String problemCodeFor(@Nullable String metricType) {
        return Optional.ofNullable(metricType)
                .map(String::toUpperCase)
                .map(this::codeForUpperMetricType)
                .orElse(UNKNOWN_PROBLEM_CODE);
    }

    private String codeForUpperMetricType(String metricType) {
        return switch (metricType) {
            case "CPU_USAGE", "TEMPERATURE" -> "CPU_OVERHEAT";
            case "MEMORY_USAGE" -> "MEMORY_PRESSURE";
            case "SIGNAL_STRENGTH" -> "SIGNAL_DEGRADATION";
            case "POWER_CONSUMPTION" -> "HIGH_POWER_CONSUMPTION";
            case "INITIAL_BLER" -> "HIGH_BLOCK_ERROR_RATE";
            case "BATTERY_SOC" -> "LOW_BATTERY";
            case "LATENCY_PING" -> "HIGH_LATENCY";
            case "DATA_THROUGHPUT" -> "LOW_THROUGHPUT";
            case "HANDOVER_SUCCESS_RATE" -> "HANDOVER_FAILURE";
            case "INTERFERENCE_LEVEL" -> "HIGH_INTERFERENCE";
            default -> metricType + "_ISSUE";
        };
    }
}
