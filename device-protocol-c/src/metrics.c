/**
 * @file metrics.c
 * @brief Metric encoding/decoding implementation
 */

#include <string.h>
#include <stdint.h>
#include <limits.h>
#include "devproto/metrics.h"

/**
 * Encode float to big-endian bytes
 */
void devproto_float_to_be(float value, uint8_t *bytes)
{
    union {
        float f;
        uint32_t u;
    } conv;

    conv.f = value;

    /* Convert to big-endian */
    bytes[0] = (conv.u >> 24) & 0xFF;
    bytes[1] = (conv.u >> 16) & 0xFF;
    bytes[2] = (conv.u >> 8) & 0xFF;
    bytes[3] = conv.u & 0xFF;
}

/**
 * Decode float from big-endian bytes
 */
float devproto_float_from_be(const uint8_t *bytes)
{
    union {
        float f;
        uint32_t u;
    } conv;

    /* Convert from big-endian */
    conv.u = ((uint32_t)bytes[0] << 24) |
             ((uint32_t)bytes[1] << 16) |
             ((uint32_t)bytes[2] << 8) |
             ((uint32_t)bytes[3]);

    return conv.f;
}

/**
 * Encode a metric value
 */
int devproto_metric_encode(devproto_metric_entry_t *entry,
                           devproto_metric_type_t type,
                           float value)
{
    if (!entry) return -1;

    entry->type = (uint8_t)type;
    devproto_float_to_be(value, entry->value_bytes);

    return 0;
}

/**
 * Decode a metric entry
 */
int devproto_metric_decode(const devproto_metric_entry_t *entry,
                           devproto_metric_t *metric)
{
    if (!entry || !metric) return -1;

    metric->type = (devproto_metric_type_t)entry->type;
    metric->value = devproto_float_from_be(entry->value_bytes);

    return 0;
}

/**
 * Get metric name string
 */
const char *devproto_metric_name(devproto_metric_type_t type)
{
    switch (type) {
    /* System metrics */
    case DEVPROTO_METRIC_CPU_USAGE:        return "CPU_USAGE";
    case DEVPROTO_METRIC_MEMORY_USAGE:     return "MEMORY_USAGE";
    case DEVPROTO_METRIC_TEMPERATURE:      return "TEMPERATURE";
    case DEVPROTO_METRIC_HUMIDITY:         return "HUMIDITY";
    case DEVPROTO_METRIC_FAN_SPEED:        return "FAN_SPEED";
    case DEVPROTO_METRIC_VOLTAGE:          return "VOLTAGE";
    case DEVPROTO_METRIC_CURRENT:          return "CURRENT";
    case DEVPROTO_METRIC_POWER:            return "POWER";

    /* RF metrics */
    case DEVPROTO_METRIC_SIGNAL_STRENGTH:  return "SIGNAL_STRENGTH";
    case DEVPROTO_METRIC_SIGNAL_QUALITY:   return "SIGNAL_QUALITY";
    case DEVPROTO_METRIC_INTERFERENCE:     return "INTERFERENCE";
    case DEVPROTO_METRIC_BER:              return "BER";
    case DEVPROTO_METRIC_VSWR:             return "VSWR";
    case DEVPROTO_METRIC_ANTENNA_TILT:     return "ANTENNA_TILT";

    /* Performance metrics */
    case DEVPROTO_METRIC_THROUGHPUT:       return "THROUGHPUT";
    case DEVPROTO_METRIC_LATENCY:          return "LATENCY";
    case DEVPROTO_METRIC_PACKET_LOSS:      return "PACKET_LOSS";
    case DEVPROTO_METRIC_JITTER:           return "JITTER";
    case DEVPROTO_METRIC_CONNECTION_COUNT: return "CONNECTION_COUNT";

    /* Device metrics */
    case DEVPROTO_METRIC_BATTERY_LEVEL:    return "BATTERY_LEVEL";
    case DEVPROTO_METRIC_UPTIME:           return "UPTIME";
    case DEVPROTO_METRIC_ERROR_COUNT:      return "ERROR_COUNT";

    /* 5G NR700 (n28 band) metrics */
    case DEVPROTO_METRIC_DL_THROUGHPUT_NR700: return "DL_THROUGHPUT_NR700";
    case DEVPROTO_METRIC_UL_THROUGHPUT_NR700: return "UL_THROUGHPUT_NR700";
    case DEVPROTO_METRIC_RSRP_NR700:          return "RSRP_NR700";
    case DEVPROTO_METRIC_SINR_NR700:          return "SINR_NR700";

    /* 5G NR3500 (n78 band) metrics */
    case DEVPROTO_METRIC_DL_THROUGHPUT_NR3500: return "DL_THROUGHPUT_NR3500";
    case DEVPROTO_METRIC_UL_THROUGHPUT_NR3500: return "UL_THROUGHPUT_NR3500";
    case DEVPROTO_METRIC_RSRP_NR3500:          return "RSRP_NR3500";
    case DEVPROTO_METRIC_SINR_NR3500:          return "SINR_NR3500";

    /* 5G Radio metrics */
    case DEVPROTO_METRIC_PDCP_THROUGHPUT:      return "PDCP_THROUGHPUT";
    case DEVPROTO_METRIC_RLC_THROUGHPUT:       return "RLC_THROUGHPUT";
    case DEVPROTO_METRIC_INITIAL_BLER:         return "INITIAL_BLER";
    case DEVPROTO_METRIC_AVG_MCS:              return "AVG_MCS";
    case DEVPROTO_METRIC_RB_PER_SLOT:          return "RB_PER_SLOT";
    case DEVPROTO_METRIC_RANK_INDICATOR:       return "RANK_INDICATOR";

    /* RF Quality metrics */
    case DEVPROTO_METRIC_TX_IMBALANCE:         return "TX_IMBALANCE";
    case DEVPROTO_METRIC_LATENCY_PING:         return "LATENCY_PING";
    case DEVPROTO_METRIC_HANDOVER_SUCCESS:     return "HANDOVER_SUCCESS_RATE";
    case DEVPROTO_METRIC_INTERFERENCE_LEVEL:   return "INTERFERENCE_LEVEL";

    /* Carrier Aggregation metrics */
    case DEVPROTO_METRIC_CA_DL_THROUGHPUT:     return "CA_DL_THROUGHPUT";
    case DEVPROTO_METRIC_CA_UL_THROUGHPUT:     return "CA_UL_THROUGHPUT";

    /* Special */
    case DEVPROTO_METRIC_ALL:              return "ALL_METRICS";
    default:                               return "UNKNOWN";
    }
}

/**
 * Parse metrics from response payload
 */
int devproto_metrics_parse(const uint8_t *payload, size_t payload_len,
                           devproto_metric_t *metrics, size_t max_metrics)
{
    if (!payload || !metrics || max_metrics == 0) return -1;

    /* Limit max_metrics to prevent return value truncation */
    if (max_metrics > (size_t)INT32_MAX) {
        max_metrics = (size_t)INT32_MAX;
    }

    size_t count = 0;
    size_t offset = 0;

    /* Each metric entry is 5 bytes: 1 byte type + 4 bytes float */
    while (offset + 5 <= payload_len && count < max_metrics) {
        devproto_metric_entry_t entry;
        entry.type = payload[offset];
        memcpy(entry.value_bytes, &payload[offset + 1], 4);

        if (devproto_metric_decode(&entry, &metrics[count]) == 0) {
            count++;
        }
        offset += 5;
    }

    return (int)count;
}

/**
 * Build metrics response payload
 */
int devproto_metrics_build(const devproto_metric_t *metrics, size_t num_metrics,
                           uint8_t *buffer, size_t buf_size)
{
    if (!metrics || !buffer) return -1;

    /* Prevent integer overflow: check num_metrics before multiplication */
    if (num_metrics > SIZE_MAX / 5) return -1;

    size_t needed = num_metrics * 5;
    if (buf_size < needed) return -1;

    /* Prevent return value truncation */
    if (needed > (size_t)INT32_MAX) return -1;

    size_t offset = 0;
    for (size_t i = 0; i < num_metrics; i++) {
        devproto_metric_entry_t entry;
        devproto_metric_encode(&entry, metrics[i].type, metrics[i].value);

        buffer[offset] = entry.type;
        memcpy(&buffer[offset + 1], entry.value_bytes, 4);
        offset += 5;
    }

    return (int)offset;
}
