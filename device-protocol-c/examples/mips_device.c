/**
 * @file mips_device.c
 * @brief Example MIPS device firmware using the protocol library
 *
 * This demonstrates how a MIPS embedded device would use the protocol
 * to communicate with the monitoring platform.
 *
 * For actual MIPS deployment, you would:
 * 1. Replace read_*() functions with actual hardware reads
 * 2. Use /dev/ttyS0 or similar for UART
 * 3. Cross-compile with: make mips CROSS_COMPILE=mips-linux-gnu-
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>

#include "devproto/protocol.h"
#include "devproto/frame.h"
#include "devproto/transport.h"
#include "devproto/metrics.h"
#include "devproto/crc16.h"

/* Configuration */
#define DEFAULT_SERIAL_PORT "/dev/ttyS0"
#define DEFAULT_TCP_PORT 9999
#define ALERT_CHECK_INTERVAL 5  /* seconds */

/* Simulated device state */
static struct {
    float cpu_temp;
    float cpu_usage;
    float memory_usage;
    float fan_speed;
    float signal_strength;
    uint32_t uptime;
    int errors;
    int warnings;
} device_state = {
    .cpu_temp = 55.0f,
    .cpu_usage = 25.0f,
    .memory_usage = 45.0f,
    .fan_speed = 3000.0f,
    .signal_strength = -75.0f,
    .uptime = 0,
    .errors = 0,
    .warnings = 0
};

/* Global state */
static devproto_transport_t *transport = NULL;
static devproto_frame_parser_t parser;
static volatile int running = 1;

/**
 * Signal handler for graceful shutdown
 */
static void signal_handler(int sig)
{
    (void)sig;
    running = 0;
}

/**
 * Simulate reading hardware metrics
 * In real firmware, these would read from actual hardware
 */
static float read_cpu_temperature(void)
{
    /* Add some random variation */
    return device_state.cpu_temp + ((float)(rand() % 100) / 100.0f - 0.5f) * 5.0f;
}

static float read_cpu_usage(void)
{
    return device_state.cpu_usage + ((float)(rand() % 100) / 100.0f - 0.5f) * 10.0f;
}

static float read_memory_usage(void)
{
    return device_state.memory_usage + ((float)(rand() % 100) / 100.0f - 0.5f) * 5.0f;
}

static float read_fan_speed(void)
{
    return device_state.fan_speed + ((float)(rand() % 100) - 50.0f);
}

static float read_signal_strength(void)
{
    return device_state.signal_strength + ((float)(rand() % 100) / 100.0f - 0.5f) * 3.0f;
}

/**
 * Collect all metrics
 */
static int collect_metrics(devproto_metric_t *metrics, size_t max_metrics)
{
    if (max_metrics < 5) return -1;

    metrics[0].type = DEVPROTO_METRIC_TEMPERATURE;
    metrics[0].value = read_cpu_temperature();

    metrics[1].type = DEVPROTO_METRIC_CPU_USAGE;
    metrics[1].value = read_cpu_usage();

    metrics[2].type = DEVPROTO_METRIC_MEMORY_USAGE;
    metrics[2].value = read_memory_usage();

    metrics[3].type = DEVPROTO_METRIC_FAN_SPEED;
    metrics[3].value = read_fan_speed();

    metrics[4].type = DEVPROTO_METRIC_SIGNAL_STRENGTH;
    metrics[4].value = read_signal_strength();

    return 5;
}

/**
 * Send response
 */
static int send_response(devproto_message_t *msg)
{
    uint8_t buffer[DEVPROTO_MAX_FRAME_SIZE];
    int len = devproto_frame_build(msg, buffer, sizeof(buffer));

    if (len < 0) {
        fprintf(stderr, "Failed to build response frame\n");
        return -1;
    }

    if (devproto_transport_send(transport, buffer, len) != len) {
        fprintf(stderr, "Failed to send response\n");
        return -1;
    }

    return 0;
}

/**
 * Handle PING request
 */
static void handle_ping(uint8_t sequence)
{
    printf("  -> Received PING, sending PONG\n");

    devproto_message_t response;
    devproto_create_pong(&response, sequence);
    send_response(&response);
}

/**
 * Handle METRICS request
 */
static void handle_metrics_request(uint8_t sequence)
{
    printf("  -> Received METRICS request\n");

    devproto_metric_t metrics[16];
    int count = collect_metrics(metrics, 16);

    uint8_t payload[256];
    int payload_len = devproto_metrics_build(metrics, count, payload, sizeof(payload));

    if (payload_len < 0) {
        fprintf(stderr, "Failed to build metrics payload\n");
        return;
    }

    devproto_message_t response = {
        .msg_type = DEVPROTO_MSG_METRICS_RESPONSE,
        .sequence = sequence,
        .payload = payload,
        .payload_len = (uint16_t)payload_len
    };

    send_response(&response);
    printf("  -> Sent %d metrics\n", count);
}

/**
 * Handle STATUS request
 */
static void handle_status_request(uint8_t sequence)
{
    printf("  -> Received STATUS request\n");

    uint8_t payload[16];

    /* Status code */
    payload[0] = DEVPROTO_STATUS_OK;

    /* Uptime (big-endian) */
    payload[1] = (device_state.uptime >> 24) & 0xFF;
    payload[2] = (device_state.uptime >> 16) & 0xFF;
    payload[3] = (device_state.uptime >> 8) & 0xFF;
    payload[4] = device_state.uptime & 0xFF;

    /* Errors (big-endian) */
    payload[5] = (device_state.errors >> 8) & 0xFF;
    payload[6] = device_state.errors & 0xFF;

    /* Warnings (big-endian) */
    payload[7] = (device_state.warnings >> 8) & 0xFF;
    payload[8] = device_state.warnings & 0xFF;

    devproto_message_t response = {
        .msg_type = DEVPROTO_MSG_STATUS_RESPONSE,
        .sequence = sequence,
        .payload = payload,
        .payload_len = 9
    };

    send_response(&response);
}

/**
 * Handle COMMAND execution
 */
static void handle_command(uint8_t sequence, const uint8_t *payload, size_t len)
{
    if (len < 1) return;

    uint8_t cmd_type = payload[0];
    const char *params = (len > 1) ? (const char *)&payload[1] : "";

    printf("  -> Received COMMAND (type: 0x%02X, params: %s)\n", cmd_type, params);

    /* Build response */
    uint8_t response_payload[256];
    response_payload[0] = 0x00;  /* Success */
    response_payload[1] = 0x00;  /* Return code */

    const char *output = "Command executed successfully";
    size_t output_len = strlen(output);
    memcpy(&response_payload[2], output, output_len);

    devproto_message_t response = {
        .msg_type = DEVPROTO_MSG_COMMAND_RESULT,
        .sequence = sequence,
        .payload = response_payload,
        .payload_len = (uint16_t)(2 + output_len)
    };

    send_response(&response);
}

/**
 * Handle incoming message
 */
static void handle_message(devproto_message_t *msg)
{
    printf("Received message: type=0x%02X, seq=%d, len=%d\n",
           msg->msg_type, msg->sequence, msg->payload_len);

    switch (msg->msg_type) {
    case DEVPROTO_MSG_PING:
        handle_ping(msg->sequence);
        break;

    case DEVPROTO_MSG_REQUEST_METRICS:
        handle_metrics_request(msg->sequence);
        break;

    case DEVPROTO_MSG_GET_STATUS:
        handle_status_request(msg->sequence);
        break;

    case DEVPROTO_MSG_EXECUTE_COMMAND:
        handle_command(msg->sequence, msg->payload, msg->payload_len);
        break;

    default:
        printf("  -> Unknown message type\n");
        break;
    }
}

/**
 * Check thresholds and send alerts if needed
 */
static void check_thresholds(void)
{
    float temp = read_cpu_temperature();

    if (temp > 80.0f) {
        printf("ALERT: Temperature threshold exceeded: %.1f°C\n", temp);
        /* In real firmware, would send THRESHOLD_EXCEEDED event */
    }
}

/**
 * Main loop
 */
static void main_loop(void)
{
    uint8_t rx_buffer[1024];
    time_t last_check = time(NULL);

    printf("Device ready, waiting for commands...\n\n");

    while (running) {
        /* Receive data */
        int n = devproto_transport_recv(transport, rx_buffer, sizeof(rx_buffer), 100);

        if (n > 0) {
            devproto_message_t msgs[4];
            int count = devproto_frame_parse(&parser, rx_buffer, n, msgs, 4);

            for (int i = 0; i < count; i++) {
                handle_message(&msgs[i]);
            }
            devproto_frame_parser_reset(&parser);
        } else if (n < 0) {
            fprintf(stderr, "Receive error, reconnecting...\n");
            break;
        }

        /* Periodic tasks */
        time_t now = time(NULL);
        if (now - last_check >= ALERT_CHECK_INTERVAL) {
            device_state.uptime += ALERT_CHECK_INTERVAL;
            check_thresholds();
            last_check = now;
        }
    }
}

/**
 * Print usage
 */
static void usage(const char *prog)
{
    printf("Usage: %s [options]\n", prog);
    printf("\n");
    printf("Options:\n");
    printf("  --serial DEV   Serial device (default: %s)\n", DEFAULT_SERIAL_PORT);
    printf("  --baud RATE    Serial baud rate (default: 115200)\n");
    printf("  --tcp PORT     Run as TCP server on PORT (for testing)\n");
    printf("  --help         Show this help\n");
    printf("\n");
    printf("This is an example MIPS device firmware that responds to\n");
    printf("protocol commands from the monitoring platform.\n");
}

int main(int argc, char *argv[])
{
    char *serial_device = NULL;
    int baudrate = 115200;
    int tcp_port = 0;

    /* Parse arguments */
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--serial") == 0 && i + 1 < argc) {
            serial_device = argv[++i];
        } else if (strcmp(argv[i], "--baud") == 0 && i + 1 < argc) {
            baudrate = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--tcp") == 0 && i + 1 < argc) {
            tcp_port = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--help") == 0) {
            usage(argv[0]);
            return 0;
        }
    }

    /* Setup signal handlers */
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    /* Seed random for simulation */
    srand(time(NULL));

    /* For testing, use TCP client to connect to Python simulator */
    if (tcp_port > 0) {
        printf("Note: TCP server mode not implemented in this example\n");
        printf("Use host_client to connect to Python DeviceSimulator\n");
        return 1;
    }

    /* Create serial transport */
    if (!serial_device) {
        serial_device = DEFAULT_SERIAL_PORT;
    }

    printf("MIPS Device Firmware Example\n");
    printf("============================\n");
    printf("Opening serial: %s @ %d baud\n", serial_device, baudrate);

    transport = devproto_transport_serial_create(serial_device, baudrate);
    if (!transport) {
        fprintf(stderr, "Failed to create transport\n");
        return 1;
    }

    if (devproto_transport_open(transport) != 0) {
        fprintf(stderr, "Failed to open serial port: %s\n", serial_device);
        fprintf(stderr, "Tip: For testing, run host_client with Python DeviceSimulator\n");
        devproto_transport_destroy(transport);
        return 1;
    }

    /* Initialize parser */
    devproto_frame_parser_init(&parser);

    /* Run main loop */
    main_loop();

    /* Cleanup */
    printf("\nShutting down...\n");
    devproto_transport_close(transport);
    devproto_transport_destroy(transport);

    return 0;
}
