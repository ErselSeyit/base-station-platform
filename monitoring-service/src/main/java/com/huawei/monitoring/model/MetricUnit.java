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
    RATIO("ratio"),
    VOLTS("V"),
    AMPS("A"),
    KWH("kWh"),
    DEGREES("°"),
    KMH("km/h"),
    MM("mm"),
    CM("cm"),
    PPM("ppm"),
    UGM3("µg/m³"),
    MHZ("MHz");

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

        // Power & Energy Metrics
        METRIC_TYPE_UNITS.put(MetricType.UTILITY_VOLTAGE_L1, VOLTS);
        METRIC_TYPE_UNITS.put(MetricType.UTILITY_VOLTAGE_L2, VOLTS);
        METRIC_TYPE_UNITS.put(MetricType.UTILITY_VOLTAGE_L3, VOLTS);
        METRIC_TYPE_UNITS.put(MetricType.POWER_FACTOR, RATIO);
        METRIC_TYPE_UNITS.put(MetricType.GENERATOR_FUEL_LEVEL, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.GENERATOR_RUNTIME, HOURS);
        METRIC_TYPE_UNITS.put(MetricType.BATTERY_SOC, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.BATTERY_DOD, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.BATTERY_CELL_TEMP_MIN, CELSIUS);
        METRIC_TYPE_UNITS.put(MetricType.BATTERY_CELL_TEMP_MAX, CELSIUS);
        METRIC_TYPE_UNITS.put(MetricType.SOLAR_PANEL_VOLTAGE, VOLTS);
        METRIC_TYPE_UNITS.put(MetricType.SOLAR_CHARGE_CURRENT, AMPS);
        METRIC_TYPE_UNITS.put(MetricType.SITE_POWER_KWH, KWH);

        // Environmental & Safety Metrics
        METRIC_TYPE_UNITS.put(MetricType.WIND_SPEED, KMH);
        METRIC_TYPE_UNITS.put(MetricType.WIND_DIRECTION, DEGREES);
        METRIC_TYPE_UNITS.put(MetricType.PRECIPITATION, MM);
        METRIC_TYPE_UNITS.put(MetricType.LIGHTNING_DISTANCE, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.TILT_ANGLE, DEGREES);
        METRIC_TYPE_UNITS.put(MetricType.VIBRATION_LEVEL, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.WATER_LEVEL, CM);
        METRIC_TYPE_UNITS.put(MetricType.PM25_LEVEL, UGM3);
        METRIC_TYPE_UNITS.put(MetricType.SMOKE_DETECTED, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.CO_LEVEL, PPM);
        METRIC_TYPE_UNITS.put(MetricType.DOOR_STATUS, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.MOTION_DETECTED, COUNT);

        // Transport/Backhaul Metrics
        METRIC_TYPE_UNITS.put(MetricType.FIBER_RX_POWER, DBM);
        METRIC_TYPE_UNITS.put(MetricType.FIBER_TX_POWER, DBM);
        METRIC_TYPE_UNITS.put(MetricType.FIBER_BER, RATIO);
        METRIC_TYPE_UNITS.put(MetricType.FIBER_OSNR, DB);
        METRIC_TYPE_UNITS.put(MetricType.MW_RSL, DBM);
        METRIC_TYPE_UNITS.put(MetricType.MW_SNR, DB);
        METRIC_TYPE_UNITS.put(MetricType.MW_MODULATION, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.ETH_UTILIZATION, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.ETH_ERRORS, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.ETH_LATENCY, MILLISECONDS);
        METRIC_TYPE_UNITS.put(MetricType.PTP_OFFSET, MILLISECONDS);
        METRIC_TYPE_UNITS.put(MetricType.GPS_SATELLITES, COUNT);

        // Advanced Radio Metrics
        METRIC_TYPE_UNITS.put(MetricType.BEAM_WEIGHT_MAG, RATIO);
        METRIC_TYPE_UNITS.put(MetricType.BEAM_WEIGHT_PHASE, DEGREES);
        METRIC_TYPE_UNITS.put(MetricType.PRECODING_RANK, COUNT);
        METRIC_TYPE_UNITS.put(MetricType.PIM_LEVEL, DBM);
        METRIC_TYPE_UNITS.put(MetricType.CO_CHANNEL_INTERFERENCE, DBM);
        METRIC_TYPE_UNITS.put(MetricType.OCCUPIED_BANDWIDTH, MHZ);
        METRIC_TYPE_UNITS.put(MetricType.ACLR, DB);
        METRIC_TYPE_UNITS.put(MetricType.GTP_THROUGHPUT, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.PACKET_DELAY, MILLISECONDS);
        METRIC_TYPE_UNITS.put(MetricType.RRC_SETUP_SUCCESS, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.PAGING_SUCCESS, PERCENTAGE);

        // Network Slicing Metrics
        METRIC_TYPE_UNITS.put(MetricType.SLICE_THROUGHPUT, MBPS);
        METRIC_TYPE_UNITS.put(MetricType.SLICE_LATENCY, MILLISECONDS);
        METRIC_TYPE_UNITS.put(MetricType.SLICE_PACKET_LOSS, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.SLICE_PRB_UTIL, PERCENTAGE);
        METRIC_TYPE_UNITS.put(MetricType.SLICE_SLA_COMPLIANCE, PERCENTAGE);
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
