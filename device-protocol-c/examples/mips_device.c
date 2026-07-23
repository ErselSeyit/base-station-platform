/**
 * @file mips_device.c
 * @brief MIPS base station device firmware (C equivalent of Python mips_device.py)
 *
 * Full-featured firmware that:
 * - Runs as TCP server on port 9999 (or serial via --serial)
 * - Generates 21 metrics matching the Python device-simulator
 * - Handles all command types: RESTART, SET_PARAMETER, RUN_DIAGNOSTIC, etc.
 * - Supports fault injection/clearing via SET_PARAMETER commands
 * - Implements a state machine: NORMAL -> DEGRADED -> FAULTED -> RESTARTING
 * - Applies cascading fault effects (temp->throttle, signal->BLER)
 * - Has realistic restart downtime (30-60s) with probabilistic failure
 *
 * Cross-compile for MIPS: make mips CROSS_COMPILE=mips-linux-gnu-
 * Run on QEMU:            qemu-mips-static ./build/mips/mips_device --tcp 9999
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>

#include "devproto/protocol.h"
#include "devproto/frame.h"
#include "devproto/metrics.h"
#include "devproto/crc16.h"

/* ========================================================================
 * Configuration
 * ======================================================================== */

#define DEFAULT_TCP_PORT      9999
#define DEFAULT_SERIAL_PORT   "/dev/ttyS0"
#define ALERT_CHECK_INTERVAL  5    /* seconds */
#define MAX_FAULTS            16
#define MAX_METRICS           32
#define RESTART_MIN_SECS      30
#define RESTART_MAX_SECS      60

/* ========================================================================
 * Device Mode (state machine)
 * ======================================================================== */

typedef enum {
    MODE_NORMAL     = 0,
    MODE_DEGRADED   = 1,  /* WARNING-level fault active */
    MODE_FAULTED    = 2,  /* CRITICAL-level fault active */
    MODE_RESTARTING = 3   /* Restart in progress, no metrics */
} device_mode_t;

static const char *mode_name(device_mode_t m)
{
    switch (m) {
    case MODE_NORMAL:     return "NORMAL";
    case MODE_DEGRADED:   return "DEGRADED";
    case MODE_FAULTED:    return "FAULTED";
    case MODE_RESTARTING: return "RESTARTING";
    default:              return "UNKNOWN";
    }
}

/* Restart success probability by mode */
static float restart_success_rate(device_mode_t m)
{
    switch (m) {
    case MODE_NORMAL:     return 1.0f;
    case MODE_DEGRADED:   return 0.95f;
    case MODE_FAULTED:    return 0.90f;
    case MODE_RESTARTING: return 0.0f;
    default:              return 0.95f;
    }
}

/* ========================================================================
 * Fault System
 * ======================================================================== */

typedef struct {
    devproto_metric_type_t type;
    float min_val;
    float max_val;
} metric_override_t;

typedef struct {
    char  name[32];
    int   active;
    int   severity;  /* DEVPROTO_STATUS_WARNING or DEVPROTO_STATUS_CRITICAL */
    metric_override_t overrides[8];
    int   num_overrides;
} fault_t;

static fault_t fault_table[MAX_FAULTS];
static int num_faults = 0;

static void define_fault(const char *name, int severity,
                         const metric_override_t *overrides, int count)
{
    if (num_faults >= MAX_FAULTS) return;
    fault_t *f = &fault_table[num_faults++];
    strncpy(f->name, name, sizeof(f->name) - 1);
    f->name[sizeof(f->name) - 1] = '\0';
    f->active = 0;
    f->severity = severity;
    f->num_overrides = count > 8 ? 8 : count;
    memcpy(f->overrides, overrides, (size_t)f->num_overrides * sizeof(metric_override_t));
}

static void init_fault_table(void)
{
    metric_override_t o[8];

    o[0] = (metric_override_t){DEVPROTO_METRIC_CPU_USAGE, 92, 99};
    o[1] = (metric_override_t){DEVPROTO_METRIC_TEMPERATURE, 82, 95};
    define_fault("CPU_OVERHEAT", DEVPROTO_STATUS_CRITICAL, o, 2);

    o[0] = (metric_override_t){DEVPROTO_METRIC_MEMORY_USAGE, 96, 99};
    o[1] = (metric_override_t){DEVPROTO_METRIC_CPU_USAGE, 70, 85};
    define_fault("MEMORY_PRESSURE", DEVPROTO_STATUS_CRITICAL, o, 2);

    o[0] = (metric_override_t){DEVPROTO_METRIC_SIGNAL_STRENGTH, -105, -95};
    o[1] = (metric_override_t){DEVPROTO_METRIC_RSRP_NR3500, -110, -100};
    o[2] = (metric_override_t){DEVPROTO_METRIC_SINR_NR3500, 2, 8};
    define_fault("SIGNAL_DEGRADATION", DEVPROTO_STATUS_WARNING, o, 3);

    o[0] = (metric_override_t){DEVPROTO_METRIC_LATENCY_PING, 110, 200};
    o[1] = (metric_override_t){DEVPROTO_METRIC_THROUGHPUT, 30, 45};
    define_fault("HIGH_LATENCY", DEVPROTO_STATUS_CRITICAL, o, 2);

    o[0] = (metric_override_t){DEVPROTO_METRIC_POWER, 720, 900};
    o[1] = (metric_override_t){DEVPROTO_METRIC_TEMPERATURE, 72, 82};
    define_fault("HIGH_POWER_CONSUMPTION", DEVPROTO_STATUS_CRITICAL, o, 2);

    o[0] = (metric_override_t){DEVPROTO_METRIC_INTERFERENCE_LEVEL, -68, -60};
    o[1] = (metric_override_t){DEVPROTO_METRIC_SINR_NR3500, -2, 5};
    o[2] = (metric_override_t){DEVPROTO_METRIC_SINR_NR700, 0, 6};
    define_fault("HIGH_INTERFERENCE", DEVPROTO_STATUS_WARNING, o, 3);

    o[0] = (metric_override_t){DEVPROTO_METRIC_INITIAL_BLER, 32, 50};
    o[1] = (metric_override_t){DEVPROTO_METRIC_SINR_NR3500, 3, 8};
    define_fault("HIGH_BLOCK_ERROR_RATE", DEVPROTO_STATUS_CRITICAL, o, 2);

    o[0] = (metric_override_t){DEVPROTO_METRIC_BATTERY_SOC, 5, 9};
    define_fault("LOW_BATTERY", DEVPROTO_STATUS_CRITICAL, o, 1);

    o[0] = (metric_override_t){DEVPROTO_METRIC_THROUGHPUT, 15, 19};
    o[1] = (metric_override_t){DEVPROTO_METRIC_LATENCY_PING, 40, 60};
    define_fault("LOW_THROUGHPUT", DEVPROTO_STATUS_CRITICAL, o, 2);

    o[0] = (metric_override_t){DEVPROTO_METRIC_HANDOVER_SUCCESS, 85, 89};
    o[1] = (metric_override_t){DEVPROTO_METRIC_SIGNAL_STRENGTH, -90, -80};
    define_fault("HANDOVER_FAILURE", DEVPROTO_STATUS_CRITICAL, o, 2);
}

static fault_t *find_fault(const char *name)
{
    for (int i = 0; i < num_faults; i++) {
        if (strcasecmp(fault_table[i].name, name) == 0)
            return &fault_table[i];
    }
    return NULL;
}

/* ========================================================================
 * Device State
 * ======================================================================== */

static struct {
    device_mode_t mode;
    time_t restart_until;

    float cpu_usage;
    float memory_usage;
    float temperature;
    float power_consumption;
    float signal_strength;

    float dl_throughput_nr3500;
    float ul_throughput_nr3500;
    float rsrp_nr3500;
    float sinr_nr3500;

    float dl_throughput_nr700;
    float ul_throughput_nr700;
    float rsrp_nr700;
    float sinr_nr700;

    float latency_ping;
    float handover_success_rate;
    float interference_level;
    float initial_bler;
    float data_throughput;

    float battery_soc;
    float battery_dod;

    uint32_t uptime;
    int      errors;
    int      warnings;
    time_t   start_time;
} dev;

/* ========================================================================
 * Helpers
 * ======================================================================== */

static volatile int running = 1;
static devproto_frame_parser_t parser;

static void signal_handler(int sig) { (void)sig; running = 0; }

static float randf(float lo, float hi)
{
    return lo + ((float)rand() / (float)RAND_MAX) * (hi - lo);
}

static float clampf(float v, float lo, float hi)
{
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

static void init_device_state(void)
{
    memset(&dev, 0, sizeof(dev));
    dev.mode = MODE_NORMAL;
    dev.cpu_usage = 35.0f;
    dev.memory_usage = 45.0f;
    dev.temperature = 42.0f;
    dev.power_consumption = 1500.0f;
    dev.signal_strength = -65.0f;
    dev.dl_throughput_nr3500 = 1200.0f;
    dev.ul_throughput_nr3500 = 85.0f;
    dev.rsrp_nr3500 = -78.0f;
    dev.sinr_nr3500 = 18.0f;
    dev.dl_throughput_nr700 = 65.0f;
    dev.ul_throughput_nr700 = 25.0f;
    dev.rsrp_nr700 = -82.0f;
    dev.sinr_nr700 = 12.0f;
    dev.latency_ping = 8.5f;
    dev.handover_success_rate = 98.5f;
    dev.interference_level = -85.0f;
    dev.initial_bler = 2.0f;
    dev.data_throughput = 180.0f;
    dev.battery_soc = 85.0f;
    dev.battery_dod = 15.0f;
    dev.start_time = time(NULL);
}

/* ========================================================================
 * State Machine
 * ======================================================================== */

static void update_mode(void)
{
    if (dev.mode == MODE_RESTARTING) {
        if (dev.restart_until > 0 && time(NULL) >= dev.restart_until) {
            dev.restart_until = 0;
            dev.mode = MODE_NORMAL;
            dev.start_time = time(NULL);
            printf("[STATE] Restart complete -> NORMAL\n");
        }
        return;
    }

    int worst = DEVPROTO_STATUS_OK;
    for (int i = 0; i < num_faults; i++) {
        if (fault_table[i].active && fault_table[i].severity > worst)
            worst = fault_table[i].severity;
    }

    if (worst >= DEVPROTO_STATUS_CRITICAL)
        dev.mode = MODE_FAULTED;
    else if (worst >= DEVPROTO_STATUS_WARNING)
        dev.mode = MODE_DEGRADED;
    else
        dev.mode = MODE_NORMAL;
}

/* ========================================================================
 * Metric Simulation
 * ======================================================================== */

static void apply_fault_overrides(devproto_metric_t *metrics, int count)
{
    for (int i = 0; i < num_faults; i++) {
        if (!fault_table[i].active) continue;
        for (int j = 0; j < fault_table[i].num_overrides; j++) {
            metric_override_t *ov = &fault_table[i].overrides[j];
            for (int k = 0; k < count; k++) {
                if ((int)metrics[k].type == (int)ov->type)
                    metrics[k].value = randf(ov->min_val, ov->max_val);
            }
        }
    }
}

static void apply_cascading_effects(devproto_metric_t *metrics, int count)
{
    float temp = 0, rsrp3500 = 0, mem = 0;
    int idx_dl3500 = -1, idx_ul3500 = -1, idx_data = -1;
    int idx_bler = -1, idx_handover = -1, idx_latency = -1;

    for (int i = 0; i < count; i++) {
        switch ((int)metrics[i].type) {
        case DEVPROTO_METRIC_TEMPERATURE:          temp = metrics[i].value; break;
        case DEVPROTO_METRIC_RSRP_NR3500:          rsrp3500 = metrics[i].value; break;
        case DEVPROTO_METRIC_MEMORY_USAGE:          mem = metrics[i].value; break;
        case DEVPROTO_METRIC_DL_THROUGHPUT_NR3500: idx_dl3500 = i; break;
        case DEVPROTO_METRIC_UL_THROUGHPUT_NR3500: idx_ul3500 = i; break;
        case DEVPROTO_METRIC_THROUGHPUT:           idx_data = i; break;
        case DEVPROTO_METRIC_INITIAL_BLER:         idx_bler = i; break;
        case DEVPROTO_METRIC_HANDOVER_SUCCESS:     idx_handover = i; break;
        case DEVPROTO_METRIC_LATENCY_PING:         idx_latency = i; break;
        default: break;
        }
    }

    /* Temperature -> CPU throttling -> throughput drop */
    if (temp > 75.0f) {
        float factor = clampf(1.0f - (temp - 75.0f) / 50.0f, 0.4f, 1.0f);
        if (idx_dl3500 >= 0) metrics[idx_dl3500].value *= factor;
        if (idx_ul3500 >= 0) metrics[idx_ul3500].value *= factor;
        if (idx_data >= 0)   metrics[idx_data].value *= factor;
    }

    /* Signal degradation -> BLER increase, handover drop */
    if (rsrp3500 < -95.0f) {
        float penalty = clampf((-95.0f - rsrp3500) / 15.0f, 0.0f, 1.0f);
        if (idx_bler >= 0)     metrics[idx_bler].value += penalty * 25.0f;
        if (idx_handover >= 0) metrics[idx_handover].value -= penalty * 10.0f;
        if (idx_dl3500 >= 0)   metrics[idx_dl3500].value *= (1.0f - penalty * 0.5f);
        if (idx_data >= 0)     metrics[idx_data].value *= (1.0f - penalty * 0.4f);
    }

    /* Memory pressure -> latency spike */
    if (mem > 90.0f && idx_latency >= 0) {
        float pressure = (mem - 90.0f) / 10.0f;
        metrics[idx_latency].value += pressure * 80.0f;
    }

    /* Clamp to physical bounds */
    for (int i = 0; i < count; i++) {
        if ((int)metrics[i].type == DEVPROTO_METRIC_INITIAL_BLER)
            metrics[i].value = clampf(metrics[i].value, 0, 100);
        if ((int)metrics[i].type == DEVPROTO_METRIC_HANDOVER_SUCCESS)
            metrics[i].value = clampf(metrics[i].value, 50, 100);
        if ((int)metrics[i].type == DEVPROTO_METRIC_LATENCY_PING)
            metrics[i].value = clampf(metrics[i].value, 1, 999);
    }
}

static int collect_metrics(devproto_metric_t *metrics, int max_metrics)
{
    update_mode();

    if (dev.mode == MODE_RESTARTING)
        return 0;

    if (max_metrics < 21)
        return -1;

    time_t now = time(NULL);
    struct tm *lt = localtime(&now);
    int hour = lt->tm_hour;
    float load = (hour >= 8 && hour <= 18) ? 1.2f : (hour <= 6 ? 0.7f : 0.9f);

    dev.uptime = (uint32_t)(now - dev.start_time);

    int n = 0;
    metrics[n].type = DEVPROTO_METRIC_CPU_USAGE;
    metrics[n].value = clampf(35 * load + randf(-5, 8), 15, 85);
    n++;

    metrics[n].type = DEVPROTO_METRIC_MEMORY_USAGE;
    metrics[n].value = clampf(45 + randf(-3, 5), 30, 80);
    n++;

    metrics[n].type = DEVPROTO_METRIC_TEMPERATURE;
    metrics[n].value = clampf(42 + metrics[0].value * 0.2f + randf(-2, 3), 35, 70);
    n++;

    metrics[n].type = DEVPROTO_METRIC_POWER;
    metrics[n].value = clampf(1200 + metrics[0].value * 15 + randf(-50, 50), 1000, 3500);
    n++;

    metrics[n].type = DEVPROTO_METRIC_SIGNAL_STRENGTH;
    metrics[n].value = clampf(-65 + randf(-5, 5), -90, -50);
    n++;

    /* 5G NR3500 */
    metrics[n].type = DEVPROTO_METRIC_DL_THROUGHPUT_NR3500;
    metrics[n].value = clampf(1200 * load + randf(-100, 150), 500, 2000);
    n++;

    metrics[n].type = DEVPROTO_METRIC_UL_THROUGHPUT_NR3500;
    metrics[n].value = clampf(85 * load + randf(-10, 15), 40, 150);
    n++;

    metrics[n].type = DEVPROTO_METRIC_RSRP_NR3500;
    metrics[n].value = clampf(-78 + randf(-5, 5), -95, -65);
    n++;

    metrics[n].type = DEVPROTO_METRIC_SINR_NR3500;
    metrics[n].value = clampf(18 + randf(-3, 3), 5, 30);
    n++;

    /* 5G NR700 */
    metrics[n].type = DEVPROTO_METRIC_DL_THROUGHPUT_NR700;
    metrics[n].value = clampf(65 * load + randf(-8, 10), 30, 100);
    n++;

    metrics[n].type = DEVPROTO_METRIC_UL_THROUGHPUT_NR700;
    metrics[n].value = clampf(25 * load + randf(-3, 5), 10, 40);
    n++;

    metrics[n].type = DEVPROTO_METRIC_RSRP_NR700;
    metrics[n].value = clampf(-82 + randf(-4, 4), -100, -70);
    n++;

    metrics[n].type = DEVPROTO_METRIC_SINR_NR700;
    metrics[n].value = clampf(12 + randf(-2, 2), 3, 20);
    n++;

    /* Quality */
    metrics[n].type = DEVPROTO_METRIC_LATENCY_PING;
    metrics[n].value = clampf(8.5f + randf(-2, 3), 3, 25);
    n++;

    metrics[n].type = DEVPROTO_METRIC_HANDOVER_SUCCESS;
    metrics[n].value = clampf(98.5f + randf(-1.5f, 1), 92, 99.9f);
    n++;

    metrics[n].type = DEVPROTO_METRIC_INTERFERENCE_LEVEL;
    metrics[n].value = clampf(-85 + randf(-5, 5), -100, -70);
    n++;

    metrics[n].type = DEVPROTO_METRIC_INITIAL_BLER;
    metrics[n].value = clampf(2.0f + randf(-0.5f, 1), 0.5f, 8);
    n++;

    metrics[n].type = DEVPROTO_METRIC_THROUGHPUT;
    metrics[n].value = clampf(180 * load + randf(-20, 25), 100, 300);
    n++;

    /* Battery */
    metrics[n].type = DEVPROTO_METRIC_BATTERY_SOC;
    metrics[n].value = clampf(85 + randf(-2, 2), 20, 100);
    n++;

    metrics[n].type = DEVPROTO_METRIC_BATTERY_DOD;
    metrics[n].value = 100 - metrics[n - 1].value;
    n++;

    apply_fault_overrides(metrics, n);
    apply_cascading_effects(metrics, n);

    return n;
}

/* ========================================================================
 * Frame I/O
 * ======================================================================== */

static int sock_send(int fd, const uint8_t *data, size_t len)
{
    size_t sent = 0;
    while (sent < len) {
        ssize_t n = send(fd, data + sent, len - sent, 0);
        if (n <= 0) return -1;
        sent += (size_t)n;
    }
    return (int)sent;
}

static int send_frame(int fd, devproto_message_t *msg)
{
    uint8_t buf[DEVPROTO_MAX_FRAME_SIZE];
    int len = devproto_frame_build(msg, buf, sizeof(buf));
    if (len < 0) return -1;
    return sock_send(fd, buf, (size_t)len);
}

static int send_command_result(int fd, uint8_t seq, int success, uint8_t code)
{
    uint8_t payload[2];
    payload[0] = success ? 0x00 : 0x01;
    payload[1] = code;

    devproto_message_t resp = {
        .msg_type = DEVPROTO_MSG_COMMAND_RESULT,
        .sequence = seq,
        .payload = payload,
        .payload_len = 2
    };
    return send_frame(fd, &resp);
}

/* ========================================================================
 * Message Handlers
 * ======================================================================== */

static void handle_ping(int fd, uint8_t seq)
{
    devproto_message_t resp;
    devproto_create_pong(&resp, seq);
    send_frame(fd, &resp);
}

static void handle_metrics_request(int fd, uint8_t seq)
{
    devproto_metric_t metrics[MAX_METRICS];
    int count = collect_metrics(metrics, MAX_METRICS);

    if (count <= 0) {
        devproto_message_t resp = {
            .msg_type = DEVPROTO_MSG_METRICS_RESPONSE,
            .sequence = seq,
            .payload = NULL,
            .payload_len = 0
        };
        send_frame(fd, &resp);
        return;
    }

    uint8_t payload[512];
    int payload_len = devproto_metrics_build(metrics, (size_t)count, payload, sizeof(payload));
    if (payload_len < 0) return;

    devproto_message_t resp = {
        .msg_type = DEVPROTO_MSG_METRICS_RESPONSE,
        .sequence = seq,
        .payload = payload,
        .payload_len = (uint16_t)payload_len
    };
    send_frame(fd, &resp);
    printf("  -> Sent %d metrics (mode=%s)\n", count, mode_name(dev.mode));
}

static void handle_status_request(int fd, uint8_t seq)
{
    uint8_t payload[9];
    uint8_t status = DEVPROTO_STATUS_OK;

    if (dev.mode == MODE_RESTARTING) status = DEVPROTO_STATUS_OFFLINE;
    else if (dev.mode == MODE_FAULTED) status = DEVPROTO_STATUS_CRITICAL;
    else if (dev.mode == MODE_DEGRADED) status = DEVPROTO_STATUS_WARNING;

    payload[0] = status;
    payload[1] = (dev.uptime >> 24) & 0xFF;
    payload[2] = (dev.uptime >> 16) & 0xFF;
    payload[3] = (dev.uptime >> 8)  & 0xFF;
    payload[4] = dev.uptime & 0xFF;
    payload[5] = (uint8_t)((dev.errors >> 8) & 0xFF);
    payload[6] = (uint8_t)(dev.errors & 0xFF);
    payload[7] = (uint8_t)((dev.warnings >> 8) & 0xFF);
    payload[8] = (uint8_t)(dev.warnings & 0xFF);

    devproto_message_t resp = {
        .msg_type = DEVPROTO_MSG_STATUS_RESPONSE,
        .sequence = seq,
        .payload = payload,
        .payload_len = 9
    };
    send_frame(fd, &resp);
}

/* ========================================================================
 * Command Handlers
 * ======================================================================== */

/* Minimal JSON string extractor: {"key":"value",...} -> value */
static int json_get_string(const char *json, const char *key, char *out, size_t out_sz)
{
    char needle[64];
    snprintf(needle, sizeof(needle), "\"%s\":", key);
    const char *p = strstr(json, needle);
    if (!p) return -1;

    p += strlen(needle);
    while (*p == ' ' || *p == '\t') p++;
    if (*p != '"') return -1;
    p++;

    size_t i = 0;
    while (*p && *p != '"' && i < out_sz - 1)
        out[i++] = *p++;
    out[i] = '\0';
    return 0;
}

static void cmd_restart(int fd, uint8_t seq)
{
    float rate = restart_success_rate(dev.mode);
    float roll = (float)rand() / (float)RAND_MAX;

    if (roll > rate) {
        printf("  -> RESTART failed (mode=%s, rate=%.0f%%)\n", mode_name(dev.mode), rate * 100);
        dev.errors++;
        send_command_result(fd, seq, 0, 0x10);
        return;
    }

    int duration = RESTART_MIN_SECS + rand() % (RESTART_MAX_SECS - RESTART_MIN_SECS + 1);
    printf("  -> RESTART accepted: downtime=%ds (mode=%s)\n", duration, mode_name(dev.mode));

    for (int i = 0; i < num_faults; i++)
        fault_table[i].active = 0;

    dev.mode = MODE_RESTARTING;
    dev.restart_until = time(NULL) + duration;
    dev.errors = 0;
    dev.warnings = 0;

    send_command_result(fd, seq, 1, 0);
}

static void cmd_set_parameter(int fd, uint8_t seq, const char *params_json)
{
    char action[32] = {0};
    char fault_type[32] = {0};

    if (json_get_string(params_json, "action", action, sizeof(action)) < 0) {
        printf("  -> SET_PARAMETER: missing 'action'\n");
        send_command_result(fd, seq, 0, 0x03);
        return;
    }

    if (strcmp(action, "clear_fault") == 0) {
        json_get_string(params_json, "fault_type", fault_type, sizeof(fault_type));
        fault_t *f = find_fault(fault_type);
        if (f && f->active) {
            f->active = 0;
            update_mode();
            printf("  -> Fault cleared: %s -> %s\n", fault_type, mode_name(dev.mode));
            send_command_result(fd, seq, 1, 0);
        } else {
            printf("  -> Fault not active: %s\n", fault_type);
            send_command_result(fd, seq, 0, 0x03);
        }
    } else if (strcmp(action, "inject_fault") == 0) {
        json_get_string(params_json, "fault_type", fault_type, sizeof(fault_type));
        fault_t *f = find_fault(fault_type);
        if (f) {
            f->active = 1;
            update_mode();
            printf("  -> Fault injected: %s -> %s\n", fault_type, mode_name(dev.mode));
            send_command_result(fd, seq, 1, 0);
        } else {
            printf("  -> Unknown fault: %s\n", fault_type);
            send_command_result(fd, seq, 0, 0x03);
        }
    } else if (strcmp(action, "clear_all_faults") == 0) {
        for (int i = 0; i < num_faults; i++)
            fault_table[i].active = 0;
        update_mode();
        printf("  -> All faults cleared -> %s\n", mode_name(dev.mode));
        send_command_result(fd, seq, 1, 0);
    } else {
        printf("  -> Unknown action: %s\n", action);
        send_command_result(fd, seq, 0, 0x03);
    }
}

static void cmd_run_diagnostic(int fd, uint8_t seq)
{
    char diag[512];
    int len = snprintf(diag, sizeof(diag),
        "{\"mode\":\"%s\",\"uptime\":%u,\"errors\":%d,"
        "\"cpu\":%.1f,\"mem\":%.1f,\"temp\":%.1f,"
        "\"active_faults\":[",
        mode_name(dev.mode), dev.uptime, dev.errors,
        dev.cpu_usage, dev.memory_usage, dev.temperature);

    int first = 1;
    for (int i = 0; i < num_faults; i++) {
        if (fault_table[i].active) {
            len += snprintf(diag + len, sizeof(diag) - (size_t)len,
                "%s\"%s\"", first ? "" : ",", fault_table[i].name);
            first = 0;
        }
    }
    len += snprintf(diag + len, sizeof(diag) - (size_t)len, "]}");

    uint8_t payload[600];
    payload[0] = 0x00;  /* success */
    payload[1] = 0x00;  /* return code */
    memcpy(&payload[2], diag, (size_t)len);

    devproto_message_t resp = {
        .msg_type = DEVPROTO_MSG_COMMAND_RESULT,
        .sequence = seq,
        .payload = payload,
        .payload_len = (uint16_t)(2 + len)
    };
    send_frame(fd, &resp);
}

static void cmd_reset_config(int fd, uint8_t seq)
{
    printf("  -> RESET_CONFIG: restoring defaults\n");
    for (int i = 0; i < num_faults; i++)
        fault_table[i].active = 0;
    dev.mode = MODE_NORMAL;
    dev.restart_until = 0;
    dev.errors = 0;
    dev.warnings = 0;
    dev.start_time = time(NULL);
    send_command_result(fd, seq, 1, 0);
}

static void handle_command(int fd, uint8_t seq, const uint8_t *payload, size_t len)
{
    if (len < 2) {
        send_command_result(fd, seq, 0, 0x01);
        return;
    }

    uint8_t cmd_type = payload[0];
    uint8_t param_len = payload[1];
    char params_json[256] = {0};

    if (param_len > 0 && len >= 2u + param_len) {
        size_t copy_len = param_len < sizeof(params_json) - 1
                        ? param_len : sizeof(params_json) - 1;
        memcpy(params_json, &payload[2], copy_len);
    }

    printf("  -> COMMAND sub=0x%02X params=%s (mode=%s)\n",
           cmd_type, params_json[0] ? params_json : "(none)", mode_name(dev.mode));

    if (dev.mode == MODE_RESTARTING) {
        printf("  -> Rejected: device RESTARTING\n");
        send_command_result(fd, seq, 0, 0x04);
        return;
    }

    switch (cmd_type) {
    case DEVPROTO_CMD_RESTART:
        cmd_restart(fd, seq);
        break;
    case DEVPROTO_CMD_SET_PARAMETER:
        cmd_set_parameter(fd, seq, params_json);
        break;
    case DEVPROTO_CMD_RUN_DIAGNOSTIC:
        cmd_run_diagnostic(fd, seq);
        break;
    case DEVPROTO_CMD_RESET_CONFIG:
        cmd_reset_config(fd, seq);
        break;
    case DEVPROTO_CMD_SHUTDOWN:
        printf("  -> SHUTDOWN acknowledged\n");
        send_command_result(fd, seq, 1, 0);
        running = 0;
        break;
    default:
        printf("  -> Unknown command: 0x%02X\n", cmd_type);
        send_command_result(fd, seq, 0, 0x02);
        break;
    }
}

/* ========================================================================
 * Message Dispatch
 * ======================================================================== */

static void handle_message(int fd, devproto_message_t *msg)
{
    printf("[%s] msg=0x%02X seq=%d len=%d\n",
           mode_name(dev.mode), msg->msg_type, msg->sequence, msg->payload_len);

    switch (msg->msg_type) {
    case DEVPROTO_MSG_PING:
        handle_ping(fd, msg->sequence);
        break;
    case DEVPROTO_MSG_REQUEST_METRICS:
        handle_metrics_request(fd, msg->sequence);
        break;
    case DEVPROTO_MSG_GET_STATUS:
        handle_status_request(fd, msg->sequence);
        break;
    case DEVPROTO_MSG_EXECUTE_COMMAND:
        handle_command(fd, msg->sequence, msg->payload, msg->payload_len);
        break;
    default:
        printf("  -> Unknown message type 0x%02X\n", msg->msg_type);
        break;
    }
}

/* ========================================================================
 * TCP Server
 * ======================================================================== */

static int create_tcp_server(int port)
{
    int sfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sfd < 0) { perror("socket"); return -1; }

    int opt = 1;
    setsockopt(sfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons((uint16_t)port);

    if (bind(sfd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("bind");
        close(sfd);
        return -1;
    }

    if (listen(sfd, 5) < 0) {
        perror("listen");
        close(sfd);
        return -1;
    }

    return sfd;
}

static void handle_client(int client_fd)
{
    uint8_t rx_buf[1024];
    time_t last_check = time(NULL);

    printf("Client connected (fd=%d)\n", client_fd);

    while (running) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(client_fd, &rfds);

        struct timeval tv = { .tv_sec = 0, .tv_usec = 100000 };
        int ready = select(client_fd + 1, &rfds, NULL, NULL, &tv);

        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (ready > 0 && FD_ISSET(client_fd, &rfds)) {
            ssize_t n = recv(client_fd, rx_buf, sizeof(rx_buf), 0);
            if (n <= 0) break;

            devproto_message_t msgs[4];
            /* Payloads are copied into this pool so that every message stays
             * valid: devproto_frame_parse() would hand back pointers into the
             * parser's buffer, and all but the last would already have been
             * overwritten by the following frame. One read cannot yield more
             * payload bytes than it read. */
            uint8_t payload_pool[sizeof(rx_buf)];
            int count = devproto_frame_parse_into(&parser, rx_buf, (size_t)n,
                                                  msgs, 4,
                                                  payload_pool, sizeof(payload_pool));
            for (int i = 0; i < count; i++)
                handle_message(client_fd, &msgs[i]);
            devproto_frame_parser_reset(&parser);
        }

        time_t now = time(NULL);
        if (now - last_check >= ALERT_CHECK_INTERVAL) {
            if (dev.mode != MODE_RESTARTING)
                dev.uptime = (uint32_t)(now - dev.start_time);
            update_mode();
            last_check = now;
        }
    }

    printf("Client disconnected (fd=%d)\n", client_fd);
    close(client_fd);
}

static void tcp_server_loop(int server_fd)
{
    printf("Waiting for connections...\n\n");

    while (running) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(server_fd, &rfds);

        struct timeval tv = { .tv_sec = 1, .tv_usec = 0 };
        int ready = select(server_fd + 1, &rfds, NULL, NULL, &tv);

        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (ready > 0 && FD_ISSET(server_fd, &rfds)) {
            struct sockaddr_in client_addr;
            socklen_t addr_len = sizeof(client_addr);
            int client_fd = accept(server_fd,
                (struct sockaddr *)&client_addr, &addr_len);
            if (client_fd < 0) continue;

            int nodelay = 1;
            setsockopt(client_fd, IPPROTO_TCP, TCP_NODELAY,
                       &nodelay, sizeof(nodelay));

            printf("Accepted connection from %s:%d\n",
                   inet_ntoa(client_addr.sin_addr),
                   ntohs(client_addr.sin_port));

            handle_client(client_fd);
        }
    }
}

/* ========================================================================
 * Main
 * ======================================================================== */

static void usage(const char *prog)
{
    printf("MIPS Base Station Device Firmware (C)\n\n");
    printf("Usage: %s [options]\n\n", prog);
    printf("Options:\n");
    printf("  --tcp PORT    TCP server port (default: %d)\n", DEFAULT_TCP_PORT);
    printf("  --serial DEV  Serial device (default: %s)\n", DEFAULT_SERIAL_PORT);
    printf("  --help        Show this help\n\n");
    printf("Features: 21 metrics, 10 fault scenarios, state machine,\n");
    printf("          cascading effects, full command handling.\n");
}

int main(int argc, char *argv[])
{
    int tcp_port = DEFAULT_TCP_PORT;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--tcp") == 0 && i + 1 < argc) {
            tcp_port = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--help") == 0) {
            usage(argv[0]);
            return 0;
        }
    }

    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    srand((unsigned int)time(NULL));

    init_fault_table();
    init_device_state();
    devproto_frame_parser_init(&parser);

    printf("MIPS Base Station Device Firmware (C)\n");
    printf("======================================\n");
    printf("Metrics: 21 (system, 5G NR3500, NR700, quality, battery)\n");
    printf("Faults:  %d scenarios defined\n", num_faults);
    printf("Mode:    %s\n", mode_name(dev.mode));
    printf("TCP:     0.0.0.0:%d\n\n", tcp_port);

    int server_fd = create_tcp_server(tcp_port);
    if (server_fd < 0) return 1;

    tcp_server_loop(server_fd);
    close(server_fd);

    printf("\nShutting down.\n");
    return 0;
}
