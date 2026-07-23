package io.github.erselseyit.basestation.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.erselseyit.basestation.monitoring.model.MetricType;

/**
 * Describes one metric the platform can record: its name, its unit, and — for
 * RAN and power/environment metrics — the 3GPP TS 28.552 counter it
 * corresponds to. Facility telemetry has no counter, reported as null.
 *
 * @param name            the metric type name
 * @param unit            the unit symbol the metric is measured in
 * @param threeGppCounter the 3GPP TS 28.552 counter name, or null if the metric
 *                        sits outside the RAN performance model
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricCatalogEntryDTO(String name, String unit, String threeGppCounter) {

    public static MetricCatalogEntryDTO of(MetricType type) {
        return new MetricCatalogEntryDTO(
                type.name(),
                type.getUnit().getUnit(),
                type.threeGppCounter().orElse(null));
    }
}
