package com.huawei.monitoring.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Defines valid units for each metric type to ensure data consistency.
 * This prevents incorrect units from being stored in the database.
 */
public enum MetricUnit {
    PERCENTAGE("%"),
    CELSIUS("°C"),
    WATTS("W"),
    DBM("dBm"),
    DB("dB"),
    CONNECTIONS("connections"),
    MBPS("Mbps"),
    RPM("RPM"),
    HOURS("h"),
    MILLISECONDS("ms"),
    COUNT("count"),
    RATIO("ratio");

    private final String unit;

    MetricUnit(String unit) {
        this.unit = unit;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * Maps each MetricType to its valid unit.
     * This ensures consistency across the application.
     */
    private static final Map<MetricType, MetricUnit> METRIC_TYPE_UNITS = new EnumMap<>(MetricType.class);

    static {
        // Infrastructure Metrics
        METRIC_TYPE_UNITS.put(MetricType.CPU_USAGE, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.MEMORY_USAGE, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.TEMPERATURE, CELSIUS);
        METRIC_TYPE_UNITS.put(MetricType.POWER_CONSUMPTION, WATTS);
        METRIC_TYPE_UNITS.put(MetricType.FAN_SPEED, RPM);
        METRIC_TYPE_UNITS.put(MetricType.UPTIME, HOURS);
        METRIC_TYPE_UNITS.put(MetricType.CONNECTION_COUNT, CONNECTIONS);

        // Legacy RF Metrics
        METRIC_TYPE_UNITS.put(MetricType.SIGNAL_STRENGTH, DBM);
        METRIC_TYPE_UNITS.put(MetricType.DATA_THROUGHPUT, MBPS);

        // 5G NR700 (n28) Metrics
        METRIC_TYPE_UNITS.put(MetricType.DL_THROUGHPUT_NR700, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.UL_THROUGHPUT_NR700, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.RSRP_NR700, DBM);
        METRIC_TYPE_UNITS.put(MetricType.SINR_NR700, DB);

        // 5G NR3500 (n78) Metrics
        METRIC_TYPE_UNITS.put(MetricType.DL_THROUGHPUT_NR3500, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.UL_THROUGHPUT_NR3500, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.RSRP_NR3500, DBM);
        METRIC_TYPE_UNITS.put(MetricType.SINR_NR3500, DB);

        // 5G Radio Metrics
        METRIC_TYPE_UNITS.put(MetricType.PDCP_THROUGHPUT, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.RLC_THROUGHPUT, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.INITIAL_BLER, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.AVG_MCS, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.RB_PER_SLOT, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.RANK_INDICATOR, COUNT);

        // RF Quality Metrics
        METRIC_TYPE_UNITS.put(MetricType.TX_IMBALANCE, DB);
        METRIC_TYPE_UNITS.put(MetricType.LATENCY_PING, MILLISECONDS);
        METRIC_TYPE_UNITS.put(MetricType.HANDOVER_SUCCESS_RATE, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.INTERFERENCE_LEVEL, DBM);
        METRIC_TYPE_UNITS.put(MetricType.VSWR, RATIO);

        // Carrier Aggregation
        METRIC_TYPE_UNITS.put(MetricType.CA_DL_THROUGHPUT, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.CA_UL_THROUGHPUT, MBPS);
    }

    /**
     * Gets the expected unit for a given metric type.
     *
     * @param metricType the metric type
     * @return the expected unit string
     * @throws IllegalArgumentException if metric type has no defined unit
     */
    public static String getUnitForMetricType(MetricType metricType) {
        MetricUnit unit = METRIC_TYPE_UNITS.get(metricType);
        if (unit == null) {
            throw new IllegalArgumentException("No unit defined for metric type: " + metricType);
        }
        return unit.getUnit();
    }

    /**
     * Validates if a unit is correct for the given metric type.
     *
     * @param metricType the metric type
     * @param unit the unit to validate
     * @return true if the unit is valid for the metric type, false otherwise
     */
    public static boolean isValidUnit(MetricType metricType, String unit) {
        if (metricType == null || unit == null) {
            return false;
        }
        String expectedUnit = getUnitForMetricType(metricType);
        return expectedUnit.equals(unit);
    }
}
