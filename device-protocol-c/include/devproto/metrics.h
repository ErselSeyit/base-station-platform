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
 * Metric types - compatible with Python MetricType enum
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
