/**
 * @file metrics.h
 * @brief Metric type definitions and encoding/decoding helpers
 *
 * Compatible with Python MetricType enum in device_protocol.py
 */

#ifndef DEVPROTO_METRICS_H
#define DEVPROTO_METRICS_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Metric types - compatible with Python MetricType enum and Go edge-bridge
 */
typedef enum {
    /* System metrics (0x01-0x0F) */
    DEVPROTO_METRIC_CPU_USAGE        = 0x01,
    DEVPROTO_METRIC_MEMORY_USAGE     = 0x02,
    DEVPROTO_METRIC_TEMPERATURE      = 0x03,
    DEVPROTO_METRIC_HUMIDITY         = 0x04,
    DEVPROTO_METRIC_FAN_SPEED        = 0x05,
    DEVPROTO_METRIC_VOLTAGE          = 0x06,
    DEVPROTO_METRIC_CURRENT          = 0x07,
    DEVPROTO_METRIC_POWER            = 0x08,

    /* RF metrics (0x10-0x1F) */
    DEVPROTO_METRIC_SIGNAL_STRENGTH  = 0x10,
    DEVPROTO_METRIC_SIGNAL_QUALITY   = 0x11,
    DEVPROTO_METRIC_INTERFERENCE     = 0x12,
    DEVPROTO_METRIC_BER              = 0x13,
    DEVPROTO_METRIC_VSWR             = 0x14,
    DEVPROTO_METRIC_ANTENNA_TILT     = 0x15,

    /* Performance metrics (0x20-0x2F) */
    DEVPROTO_METRIC_THROUGHPUT       = 0x20,
    DEVPROTO_METRIC_LATENCY          = 0x21,
    DEVPROTO_METRIC_PACKET_LOSS      = 0x22,
    DEVPROTO_METRIC_JITTER           = 0x23,
    DEVPROTO_METRIC_CONNECTION_COUNT = 0x24,

    /* Device metrics (0x30-0x3F) */
    DEVPROTO_METRIC_BATTERY_LEVEL    = 0x30,
    DEVPROTO_METRIC_UPTIME           = 0x31,
    DEVPROTO_METRIC_ERROR_COUNT      = 0x32,

    /* 5G NR700 (n28 band) metrics (0x40-0x4F) */
    DEVPROTO_METRIC_DL_THROUGHPUT_NR700 = 0x40,
    DEVPROTO_METRIC_UL_THROUGHPUT_NR700 = 0x41,
    DEVPROTO_METRIC_RSRP_NR700          = 0x42,
    DEVPROTO_METRIC_SINR_NR700          = 0x43,

    /* 5G NR3500 (n78 band) metrics (0x50-0x5F) */
    DEVPROTO_METRIC_DL_THROUGHPUT_NR3500 = 0x50,
    DEVPROTO_METRIC_UL_THROUGHPUT_NR3500 = 0x51,
    DEVPROTO_METRIC_RSRP_NR3500          = 0x52,
    DEVPROTO_METRIC_SINR_NR3500          = 0x53,

    /* 5G Radio metrics (0x60-0x6F) */
    DEVPROTO_METRIC_PDCP_THROUGHPUT      = 0x60,
    DEVPROTO_METRIC_RLC_THROUGHPUT       = 0x61,
    DEVPROTO_METRIC_INITIAL_BLER         = 0x62,
    DEVPROTO_METRIC_AVG_MCS              = 0x63,
    DEVPROTO_METRIC_RB_PER_SLOT          = 0x64,
    DEVPROTO_METRIC_RANK_INDICATOR       = 0x65,

    /* RF Quality metrics (0x70-0x7F) */
    DEVPROTO_METRIC_TX_IMBALANCE         = 0x70,
    DEVPROTO_METRIC_LATENCY_PING         = 0x71,
    DEVPROTO_METRIC_HANDOVER_SUCCESS     = 0x72,
    DEVPROTO_METRIC_INTERFERENCE_LEVEL   = 0x73,

    /* Carrier Aggregation metrics (0x78-0x7F) */
    DEVPROTO_METRIC_CA_DL_THROUGHPUT     = 0x78,
    DEVPROTO_METRIC_CA_UL_THROUGHPUT     = 0x79,

    /* ========================================================================
     * Extended Metrics (Phase 2 Enhancement)
     * ======================================================================== */

    /* Power & Energy metrics (0x80-0x8F) */
    DEVPROTO_METRIC_UTILITY_VOLTAGE_L1    = 0x80,  /* V, 0-500 */
    DEVPROTO_METRIC_UTILITY_VOLTAGE_L2    = 0x81,  /* V, 0-500 */
    DEVPROTO_METRIC_UTILITY_VOLTAGE_L3    = 0x82,  /* V, 0-500 */
    DEVPROTO_METRIC_POWER_FACTOR          = 0x83,  /* ratio, 0-1.0 */
    DEVPROTO_METRIC_GENERATOR_FUEL_LEVEL  = 0x84,  /* %, 0-100 */
    DEVPROTO_METRIC_GENERATOR_RUNTIME     = 0x85,  /* hours */
    DEVPROTO_METRIC_BATTERY_SOC           = 0x86,  /* %, 0-100 (State of Charge) */
    DEVPROTO_METRIC_BATTERY_DOD           = 0x87,  /* %, 0-100 (Depth of Discharge) */
    DEVPROTO_METRIC_BATTERY_CELL_TEMP_MIN = 0x88,  /* °C, -20 to 80 */
    DEVPROTO_METRIC_BATTERY_CELL_TEMP_MAX = 0x89,  /* °C, -20 to 80 */
    DEVPROTO_METRIC_SOLAR_PANEL_VOLTAGE   = 0x8A,  /* V, 0-100 */
    DEVPROTO_METRIC_SOLAR_CHARGE_CURRENT  = 0x8B,  /* A, 0-50 */
    DEVPROTO_METRIC_SITE_POWER_KWH        = 0x8C,  /* kWh cumulative */

    /* Environmental & Safety metrics (0x90-0x9F) */
    DEVPROTO_METRIC_WIND_SPEED            = 0x90,  /* km/h, 0-200 */
    DEVPROTO_METRIC_WIND_DIRECTION        = 0x91,  /* degrees, 0-360 */
    DEVPROTO_METRIC_PRECIPITATION         = 0x92,  /* mm/h, 0-500 */
    DEVPROTO_METRIC_LIGHTNING_DISTANCE    = 0x93,  /* km, 0-50 */
    DEVPROTO_METRIC_TILT_ANGLE            = 0x94,  /* degrees, -10 to 10 */
    DEVPROTO_METRIC_VIBRATION_LEVEL       = 0x95,  /* mm/s, 0-100 */
    DEVPROTO_METRIC_WATER_LEVEL           = 0x96,  /* mm, 0-1000 */
    DEVPROTO_METRIC_PM25_LEVEL            = 0x97,  /* µg/m³, 0-500 */
    DEVPROTO_METRIC_SMOKE_DETECTED        = 0x98,  /* bool, 0-1 */
    DEVPROTO_METRIC_CO_LEVEL              = 0x99,  /* ppm, 0-1000 */
    DEVPROTO_METRIC_DOOR_STATUS           = 0x9A,  /* bool, 0=closed, 1=open */
    DEVPROTO_METRIC_MOTION_DETECTED       = 0x9B,  /* bool, 0-1 */

    /* Transport/Backhaul metrics (0xA0-0xAF) */
    DEVPROTO_METRIC_FIBER_RX_POWER        = 0xA0,  /* dBm, -40 to 10 */
    DEVPROTO_METRIC_FIBER_TX_POWER        = 0xA1,  /* dBm, -10 to 10 */
    DEVPROTO_METRIC_FIBER_BER             = 0xA2,  /* ratio, 0 to 1e-3 */
    DEVPROTO_METRIC_FIBER_OSNR            = 0xA3,  /* dB, 0-40 */
    DEVPROTO_METRIC_MW_RSL                = 0xA4,  /* dBm, -80 to 0 (Received Signal Level) */
    DEVPROTO_METRIC_MW_SNR                = 0xA5,  /* dB, 0-50 */
    DEVPROTO_METRIC_MW_MODULATION         = 0xA6,  /* enum, 0-11 (QPSK to 4096QAM) */
    DEVPROTO_METRIC_ETH_UTILIZATION       = 0xA7,  /* %, 0-100 */
    DEVPROTO_METRIC_ETH_ERRORS            = 0xA8,  /* count */
    DEVPROTO_METRIC_ETH_LATENCY           = 0xA9,  /* ms, 0-1000 */
    DEVPROTO_METRIC_PTP_OFFSET            = 0xAA,  /* ns, -1e6 to 1e6 */
    DEVPROTO_METRIC_GPS_SATELLITES        = 0xAB,  /* count, 0-24 */

    /* Advanced Radio metrics (0xB0-0xBF) */
    DEVPROTO_METRIC_BEAM_WEIGHT_MAG       = 0xB0,  /* ratio, 0-1 */
    DEVPROTO_METRIC_BEAM_WEIGHT_PHASE     = 0xB1,  /* degrees, -180 to 180 */
    DEVPROTO_METRIC_PRECODING_RANK        = 0xB2,  /* layers, 1-8 */
    DEVPROTO_METRIC_PIM_LEVEL             = 0xB3,  /* dBm, -150 to 0 (Passive Intermod) */
    DEVPROTO_METRIC_CO_CHANNEL_INTERF     = 0xB4,  /* dBm, -120 to 0 */
    DEVPROTO_METRIC_OCCUPIED_BANDWIDTH    = 0xB5,  /* MHz, 0-100 */
    DEVPROTO_METRIC_ACLR                  = 0xB6,  /* dB, 0-80 (Adjacent Channel Leakage) */
    DEVPROTO_METRIC_GTP_THROUGHPUT        = 0xB7,  /* Mbps, 0-10000 */
    DEVPROTO_METRIC_PACKET_DELAY          = 0xB8,  /* ms, 0-1000 */
    DEVPROTO_METRIC_RRC_SETUP_SUCCESS     = 0xB9,  /* %, 0-100 */
    DEVPROTO_METRIC_PAGING_SUCCESS        = 0xBA,  /* %, 0-100 */

    /* Network Slicing metrics (0xC0-0xCF) - 5G specific */
    DEVPROTO_METRIC_SLICE_THROUGHPUT      = 0xC0,  /* Mbps, 0-10000 */
    DEVPROTO_METRIC_SLICE_LATENCY         = 0xC1,  /* ms, 0-1000 */
    DEVPROTO_METRIC_SLICE_PACKET_LOSS     = 0xC2,  /* %, 0-100 */
    DEVPROTO_METRIC_SLICE_PRB_UTIL        = 0xC3,  /* %, 0-100 (PRB utilization) */
    DEVPROTO_METRIC_SLICE_SLA_COMPLIANCE  = 0xC4,  /* %, 0-100 */

    /* Special values */
    DEVPROTO_METRIC_ALL              = 0xFF
} devproto_metric_type_t;

/**
 * Metric entry structure (5 bytes on wire: type + float)
 */
typedef struct __attribute__((packed)) {
    uint8_t  type;              /* Metric type */
    uint8_t  value_bytes[4];    /* IEEE 754 float, big-endian */
} devproto_metric_entry_t;

/**
 * Metric value with decoded float
 */
typedef struct {
    devproto_metric_type_t type;
    float value;
} devproto_metric_t;

/**
 * Encode a metric value to wire format
 * @param entry  Output metric entry
 * @param type   Metric type
 * @param value  Float value
 * @return       0 on success, -1 on error
 */
int devproto_metric_encode(devproto_metric_entry_t *entry,
                           devproto_metric_type_t type,
                           float value);

/**
 * Decode a metric entry from wire format
 * @param entry  Input metric entry
 * @param metric Output metric with type and value
 * @return       0 on success, -1 on error
 */
int devproto_metric_decode(const devproto_metric_entry_t *entry,
                           devproto_metric_t *metric);

/**
 * Encode a float to big-endian bytes
 * @param value  Float value
 * @param bytes  Output buffer (4 bytes)
 */
void devproto_float_to_be(float value, uint8_t *bytes);

/**
 * Decode a float from big-endian bytes
 * @param bytes  Input buffer (4 bytes)
 * @return       Float value
 */
float devproto_float_from_be(const uint8_t *bytes);

/**
 * Get metric name string (for debugging)
 * @param type  Metric type
 * @return      Static string, or "UNKNOWN" if invalid
 */
const char *devproto_metric_name(devproto_metric_type_t type);

/**
 * Parse multiple metrics from response payload
 * @param payload       Payload data
 * @param payload_len   Payload length
 * @param metrics       Output array of metrics
 * @param max_metrics   Maximum metrics to parse
 * @return              Number of metrics parsed, or -1 on error
 */
int devproto_metrics_parse(const uint8_t *payload, size_t payload_len,
                           devproto_metric_t *metrics, size_t max_metrics);

/**
 * Build metrics response payload
 * @param metrics       Array of metrics to encode
 * @param num_metrics   Number of metrics
 * @param buffer        Output buffer
 * @param buf_size      Buffer size
 * @return              Number of bytes written, or -1 on error
 */
int devproto_metrics_build(const devproto_metric_t *metrics, size_t num_metrics,
                           uint8_t *buffer, size_t buf_size);

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_METRICS_H */
