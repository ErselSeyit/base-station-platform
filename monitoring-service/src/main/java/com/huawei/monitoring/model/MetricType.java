package com.huawei.monitoring.model;

/**
 * Metric types for base station monitoring.
 * Includes standard infrastructure metrics and 5G NR-specific metrics.
 */
public enum MetricType {
    // Infrastructure Metrics
    CPU_USAGE,
    MEMORY_USAGE,
    TEMPERATURE,
    POWER_CONSUMPTION,
    FAN_SPEED,
    UPTIME,
    CONNECTION_COUNT,

    // Legacy RF Metrics
    SIGNAL_STRENGTH,
    DATA_THROUGHPUT,

    // 5G NR700 (n28) Metrics - 700MHz Band
    DL_THROUGHPUT_NR700,
    UL_THROUGHPUT_NR700,
    RSRP_NR700,
    SINR_NR700,

    // 5G NR3500 (n78) Metrics - 3.5GHz Band
    DL_THROUGHPUT_NR3500,
    UL_THROUGHPUT_NR3500,
    RSRP_NR3500,
    SINR_NR3500,

    // 5G Radio Metrics
    PDCP_THROUGHPUT,
    RLC_THROUGHPUT,
    INITIAL_BLER,
    AVG_MCS,
    RB_PER_SLOT,
    RANK_INDICATOR,

    // RF Quality Metrics
    TX_IMBALANCE,
    LATENCY_PING,
    HANDOVER_SUCCESS_RATE,
    INTERFERENCE_LEVEL,
    VSWR,

    // Carrier Aggregation
    CA_DL_THROUGHPUT,
    CA_UL_THROUGHPUT,

    // ========================================================================
    // Extended Metrics (Phase 2 Enhancement)
    // ========================================================================

    // Power & Energy Metrics (0x80-0x8F)
    UTILITY_VOLTAGE_L1,
    UTILITY_VOLTAGE_L2,
    UTILITY_VOLTAGE_L3,
    POWER_FACTOR,
    GENERATOR_FUEL_LEVEL,
    GENERATOR_RUNTIME,
    BATTERY_SOC,
    BATTERY_DOD,
    BATTERY_CELL_TEMP_MIN,
    BATTERY_CELL_TEMP_MAX,
    SOLAR_PANEL_VOLTAGE,
    SOLAR_CHARGE_CURRENT,
    SITE_POWER_KWH,

    // Environmental & Safety Metrics (0x90-0x9F)
    WIND_SPEED,
    WIND_DIRECTION,
    PRECIPITATION,
    LIGHTNING_DISTANCE,
    TILT_ANGLE,
    VIBRATION_LEVEL,
    WATER_LEVEL,
    PM25_LEVEL,
    SMOKE_DETECTED,
    CO_LEVEL,
    DOOR_STATUS,
    MOTION_DETECTED,

    // Transport/Backhaul Metrics (0xA0-0xAF)
    FIBER_RX_POWER,
    FIBER_TX_POWER,
    FIBER_BER,
    FIBER_OSNR,
    MW_RSL,
    MW_SNR,
    MW_MODULATION,
    ETH_UTILIZATION,
    ETH_ERRORS,
    ETH_LATENCY,
    PTP_OFFSET,
    GPS_SATELLITES,

    // Advanced Radio Metrics (0xB0-0xBF)
    BEAM_WEIGHT_MAG,
    BEAM_WEIGHT_PHASE,
    PRECODING_RANK,
    PIM_LEVEL,
    CO_CHANNEL_INTERFERENCE,
    OCCUPIED_BANDWIDTH,
    ACLR,
    GTP_THROUGHPUT,
    PACKET_DELAY,
    RRC_SETUP_SUCCESS,
    PAGING_SUCCESS,

    // Network Slicing Metrics (0xC0-0xCF) - 5G specific
    SLICE_THROUGHPUT,
    SLICE_LATENCY,
    SLICE_PACKET_LOSS,
    SLICE_PRB_UTIL,
    SLICE_SLA_COMPLIANCE
}
