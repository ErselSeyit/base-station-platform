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

    /* Power & Energy metrics (0x80-0x8F) */
    case DEVPROTO_METRIC_UTILITY_VOLTAGE_L1:   return "UTILITY_VOLTAGE_L1";
    case DEVPROTO_METRIC_UTILITY_VOLTAGE_L2:   return "UTILITY_VOLTAGE_L2";
    case DEVPROTO_METRIC_UTILITY_VOLTAGE_L3:   return "UTILITY_VOLTAGE_L3";
    case DEVPROTO_METRIC_POWER_FACTOR:         return "POWER_FACTOR";
    case DEVPROTO_METRIC_GENERATOR_FUEL_LEVEL: return "GENERATOR_FUEL_LEVEL";
    case DEVPROTO_METRIC_GENERATOR_RUNTIME:    return "GENERATOR_RUNTIME";
    case DEVPROTO_METRIC_BATTERY_SOC:          return "BATTERY_SOC";
    case DEVPROTO_METRIC_BATTERY_DOD:          return "BATTERY_DOD";
    case DEVPROTO_METRIC_BATTERY_CELL_TEMP_MIN:return "BATTERY_CELL_TEMP_MIN";
    case DEVPROTO_METRIC_BATTERY_CELL_TEMP_MAX:return "BATTERY_CELL_TEMP_MAX";
    case DEVPROTO_METRIC_SOLAR_PANEL_VOLTAGE:  return "SOLAR_PANEL_VOLTAGE";
    case DEVPROTO_METRIC_SOLAR_CHARGE_CURRENT: return "SOLAR_CHARGE_CURRENT";
    case DEVPROTO_METRIC_SITE_POWER_KWH:       return "SITE_POWER_KWH";

    /* Environmental & Safety metrics (0x90-0x9F) */
    case DEVPROTO_METRIC_WIND_SPEED:           return "WIND_SPEED";
    case DEVPROTO_METRIC_WIND_DIRECTION:       return "WIND_DIRECTION";
    case DEVPROTO_METRIC_PRECIPITATION:        return "PRECIPITATION";
    case DEVPROTO_METRIC_LIGHTNING_DISTANCE:   return "LIGHTNING_DISTANCE";
    case DEVPROTO_METRIC_TILT_ANGLE:           return "TILT_ANGLE";
    case DEVPROTO_METRIC_VIBRATION_LEVEL:      return "VIBRATION_LEVEL";
    case DEVPROTO_METRIC_WATER_LEVEL:          return "WATER_LEVEL";
    case DEVPROTO_METRIC_PM25_LEVEL:           return "PM25_LEVEL";
    case DEVPROTO_METRIC_SMOKE_DETECTED:       return "SMOKE_DETECTED";
    case DEVPROTO_METRIC_CO_LEVEL:             return "CO_LEVEL";
    case DEVPROTO_METRIC_DOOR_STATUS:          return "DOOR_STATUS";
    case DEVPROTO_METRIC_MOTION_DETECTED:      return "MOTION_DETECTED";

    /* Transport/Backhaul metrics (0xA0-0xAF) */
    case DEVPROTO_METRIC_FIBER_RX_POWER:       return "FIBER_RX_POWER";
    case DEVPROTO_METRIC_FIBER_TX_POWER:       return "FIBER_TX_POWER";
    case DEVPROTO_METRIC_FIBER_BER:            return "FIBER_BER";
    case DEVPROTO_METRIC_FIBER_OSNR:           return "FIBER_OSNR";
    case DEVPROTO_METRIC_MW_RSL:               return "MW_RSL";
    case DEVPROTO_METRIC_MW_SNR:               return "MW_SNR";
    case DEVPROTO_METRIC_MW_MODULATION:        return "MW_MODULATION";
    case DEVPROTO_METRIC_ETH_UTILIZATION:      return "ETH_UTILIZATION";
    case DEVPROTO_METRIC_ETH_ERRORS:           return "ETH_ERRORS";
    case DEVPROTO_METRIC_ETH_LATENCY:          return "ETH_LATENCY";
    case DEVPROTO_METRIC_PTP_OFFSET:           return "PTP_OFFSET";
    case DEVPROTO_METRIC_GPS_SATELLITES:       return "GPS_SATELLITES";

    /* Advanced Radio metrics (0xB0-0xBF) */
    case DEVPROTO_METRIC_BEAM_WEIGHT_MAG:      return "BEAM_WEIGHT_MAG";
    case DEVPROTO_METRIC_BEAM_WEIGHT_PHASE:    return "BEAM_WEIGHT_PHASE";
    case DEVPROTO_METRIC_PRECODING_RANK:       return "PRECODING_RANK";
    case DEVPROTO_METRIC_PIM_LEVEL:            return "PIM_LEVEL";
    case DEVPROTO_METRIC_CO_CHANNEL_INTERF:    return "CO_CHANNEL_INTERFERENCE";
    case DEVPROTO_METRIC_OCCUPIED_BANDWIDTH:   return "OCCUPIED_BANDWIDTH";
    case DEVPROTO_METRIC_ACLR:                 return "ACLR";
    case DEVPROTO_METRIC_GTP_THROUGHPUT:       return "GTP_THROUGHPUT";
    case DEVPROTO_METRIC_PACKET_DELAY:         return "PACKET_DELAY";
    case DEVPROTO_METRIC_RRC_SETUP_SUCCESS:    return "RRC_SETUP_SUCCESS";
    case DEVPROTO_METRIC_PAGING_SUCCESS:       return "PAGING_SUCCESS";

    /* Network Slicing metrics (0xC0-0xCF) */
    case DEVPROTO_METRIC_SLICE_THROUGHPUT:     return "SLICE_THROUGHPUT";
    case DEVPROTO_METRIC_SLICE_LATENCY:        return "SLICE_LATENCY";
    case DEVPROTO_METRIC_SLICE_PACKET_LOSS:    return "SLICE_PACKET_LOSS";
    case DEVPROTO_METRIC_SLICE_PRB_UTIL:       return "SLICE_PRB_UTIL";
    case DEVPROTO_METRIC_SLICE_SLA_COMPLIANCE: return "SLICE_SLA_COMPLIANCE";

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
