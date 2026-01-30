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
    CA_UL_THROUGHPUT
}
