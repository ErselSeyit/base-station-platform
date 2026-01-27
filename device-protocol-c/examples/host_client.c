/**
 * @file host_client.c
 * @brief PC-side client example for testing with Python DeviceSimulator
 *
 * Usage:
 *   ./host_client --host 127.0.0.1 --port 9999
 *   ./host_client --serial /dev/ttyUSB0
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <getopt.h>

#include "devproto/protocol.h"
#include "devproto/frame.h"
#include "devproto/transport.h"
#include "devproto/metrics.h"
#include "devproto/crc16.h"

/* Global state */
static devproto_transport_t *transport = NULL;
static devproto_frame_parser_t parser;
static uint8_t sequence = 0;

/**
 * Send a message and wait for response
 */
static int send_and_receive(devproto_message_t *request, devproto_message_t *response)
{
    uint8_t tx_buffer[DEVPROTO_MAX_FRAME_SIZE];
    uint8_t rx_buffer[1024];

    /* Build and send frame */
    int tx_len = devproto_frame_build(request, tx_buffer, sizeof(tx_buffer));
    if (tx_len < 0) {
        fprintf(stderr, "Failed to build frame\n");
        return -1;
    }

    if (devproto_transport_send(transport, tx_buffer, tx_len) != tx_len) {
        fprintf(stderr, "Failed to send frame\n");
        return -1;
    }

    /* Receive response */
    devproto_frame_parser_reset(&parser);

    int timeout_ms = 5000;
    int elapsed = 0;

    while (elapsed < timeout_ms) {
        int n = devproto_transport_recv(transport, rx_buffer, sizeof(rx_buffer), 100);

        if (n < 0) {
            fprintf(stderr, "Receive error\n");
            return -1;
        }

        if (n > 0) {
            int count = devproto_frame_parse(&parser, rx_buffer, n, response, 1);
            if (count > 0) {
                return 0;  /* Success */
            }
        }

        elapsed += 100;
    }

    fprintf(stderr, "Response timeout\n");
    return -1;
}

/**
 * Test ping
 */
static int test_ping(void)
{
    printf("Testing PING... ");
    fflush(stdout);

    devproto_message_t request, response;
    devproto_create_ping(&request, ++sequence);

    if (send_and_receive(&request, &response) != 0) {
        printf("FAILED\n");
        return -1;
    }

    if (response.msg_type != DEVPROTO_MSG_PONG) {
        printf("FAILED (wrong response type: 0x%02X)\n", response.msg_type);
        return -1;
    }

    printf("OK (PONG received)\n");
    return 0;
}

/**
 * Test metrics request
 */
static int test_metrics(void)
{
    printf("Testing METRICS... ");
    fflush(stdout);

    devproto_message_t request, response;
    devproto_create_metrics_request(&request, ++sequence, NULL, 0);

    if (send_and_receive(&request, &response) != 0) {
        printf("FAILED\n");
        return -1;
    }

    if (response.msg_type != DEVPROTO_MSG_METRICS_RESPONSE) {
        printf("FAILED (wrong response type: 0x%02X)\n", response.msg_type);
        return -1;
    }

    /* Parse metrics */
    devproto_metric_t metrics[32];
    int count = devproto_metrics_parse(response.payload, response.payload_len,
                                       metrics, 32);

    printf("OK (%d metrics received)\n", count);

    for (int i = 0; i < count && i < 10; i++) {
        printf("  %s: %.4f\n",
               devproto_metric_name(metrics[i].type),
               metrics[i].value);
    }

    if (count > 10) {
        printf("  ... and %d more\n", count - 10);
    }

    return 0;
}

/**
 * Test status request
 */
static int test_status(void)
{
    printf("Testing STATUS... ");
    fflush(stdout);

    devproto_message_t request, response;
    devproto_create_status_request(&request, ++sequence);

    if (send_and_receive(&request, &response) != 0) {
        printf("FAILED\n");
        return -1;
    }

    if (response.msg_type != DEVPROTO_MSG_STATUS_RESPONSE) {
        printf("FAILED (wrong response type: 0x%02X)\n", response.msg_type);
        return -1;
    }

    if (response.payload_len >= sizeof(devproto_status_payload_t)) {
        devproto_status_payload_t *status = (devproto_status_payload_t *)response.payload;

        /* Convert from big-endian */
        uint32_t uptime = ((uint32_t)response.payload[1] << 24) |
                          ((uint32_t)response.payload[2] << 16) |
                          ((uint32_t)response.payload[3] << 8) |
                          ((uint32_t)response.payload[4]);

        printf("OK\n");
        printf("  Status: %d\n", status->status);
        printf("  Uptime: %u seconds\n", uptime);
    } else {
        printf("OK (payload: %d bytes)\n", response.payload_len);
    }

    return 0;
}

/**
 * Print usage
 */
static void usage(const char *prog)
{
    printf("Usage: %s [options]\n", prog);
    printf("\n");
    printf("Options:\n");
    printf("  --host HOST    TCP host (default: 127.0.0.1)\n");
    printf("  --port PORT    TCP port (default: 9999)\n");
    printf("  --serial DEV   Serial device (e.g., /dev/ttyUSB0)\n");
    printf("  --baud RATE    Serial baud rate (default: 115200)\n");
    printf("  --help         Show this help\n");
    printf("\n");
    printf("Examples:\n");
    printf("  %s --host 127.0.0.1 --port 9999\n", prog);
    printf("  %s --serial /dev/ttyUSB0 --baud 115200\n", prog);
}

int main(int argc, char *argv[])
{
    char *host = "127.0.0.1";
    int port = 9999;
    char *serial_device = NULL;
    int baudrate = 115200;

    static struct option long_options[] = {
        {"host",   required_argument, 0, 'h'},
        {"port",   required_argument, 0, 'p'},
        {"serial", required_argument, 0, 's'},
        {"baud",   required_argument, 0, 'b'},
        {"help",   no_argument,       0, '?'},
        {0, 0, 0, 0}
    };

    int opt;
    while ((opt = getopt_long(argc, argv, "h:p:s:b:", long_options, NULL)) != -1) {
        switch (opt) {
        case 'h':
            host = optarg;
            break;
        case 'p':
            port = atoi(optarg);
            break;
        case 's':
            serial_device = optarg;
            break;
        case 'b':
            baudrate = atoi(optarg);
            break;
        case '?':
        default:
            usage(argv[0]);
            return 0;
        }
    }

    /* Create transport */
    if (serial_device) {
        printf("Connecting to serial: %s @ %d baud\n", serial_device, baudrate);
        transport = devproto_transport_serial_create(serial_device, baudrate);
    } else {
        printf("Connecting to TCP: %s:%d\n", host, port);
        transport = devproto_transport_tcp_create(host, port);
    }

    if (!transport) {
        fprintf(stderr, "Failed to create transport\n");
        return 1;
    }

    /* Connect */
    if (devproto_transport_open(transport) != 0) {
        fprintf(stderr, "Failed to connect\n");
        devproto_transport_destroy(transport);
        return 1;
    }

    printf("Connected!\n\n");

    /* Initialize parser */
    devproto_frame_parser_init(&parser);

    /* Run tests */
    int failures = 0;

    if (test_ping() != 0) failures++;
    if (test_metrics() != 0) failures++;
    if (test_status() != 0) failures++;

    printf("\n");
    if (failures == 0) {
        printf("All tests passed!\n");
    } else {
        printf("%d test(s) failed\n", failures);
    }

    /* Cleanup */
    devproto_transport_close(transport);
    devproto_transport_destroy(transport);

    return failures > 0 ? 1 : 0;
}
