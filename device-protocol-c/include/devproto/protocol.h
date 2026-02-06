/**
 * @file protocol.h
 * @brief Device Communication Protocol for Base Station Platform
 *
 * Binary protocol for PC <-> MIPS device communication.
 * Compatible with Python implementation in testing/device_protocol.py
 *
 * Frame Format:
 * +--------+--------+--------+--------+-------------+--------+
 * | 0xAA55 | LENGTH |  TYPE  |  SEQ   | PAYLOAD     |  CRC   |
 * +--------+--------+--------+--------+-------------+--------+
 * | 2 bytes| 2 bytes| 1 byte | 1 byte | 0-4096 bytes| 2 bytes|
 *          (big-endian)                              (CRC-16-CCITT)
 */

#ifndef DEVPROTO_PROTOCOL_H
#define DEVPROTO_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Protocol constants */
#define DEVPROTO_HEADER_BYTE0       0xAA
#define DEVPROTO_HEADER_BYTE1       0x55
#define DEVPROTO_HEADER_MAGIC       0xAA55
#define DEVPROTO_VERSION            0x01
#define DEVPROTO_MAX_PAYLOAD_SIZE   4096
#define DEVPROTO_HEADER_SIZE        6       /* header(2) + length(2) + type(1) + seq(1) */
#define DEVPROTO_CRC_SIZE           2
#define DEVPROTO_MIN_FRAME_SIZE     8       /* header + crc, no payload */
#define DEVPROTO_MAX_FRAME_SIZE     (DEVPROTO_HEADER_SIZE + DEVPROTO_MAX_PAYLOAD_SIZE + DEVPROTO_CRC_SIZE)

/**
 * Message types - compatible with Python MessageType enum
 */
typedef enum {
    /* Requests (PC -> Device) */
    DEVPROTO_MSG_PING            = 0x01,
    DEVPROTO_MSG_REQUEST_METRICS = 0x02,
    DEVPROTO_MSG_EXECUTE_COMMAND = 0x03,
    DEVPROTO_MSG_SET_CONFIG      = 0x04,
    DEVPROTO_MSG_GET_STATUS      = 0x05,
    DEVPROTO_MSG_REBOOT          = 0x06,
    DEVPROTO_MSG_UPDATE_FIRMWARE = 0x07,

    /* Responses (Device -> PC) */
    DEVPROTO_MSG_PONG            = 0x81,
    DEVPROTO_MSG_METRICS_RESPONSE= 0x82,
    DEVPROTO_MSG_COMMAND_RESULT  = 0x83,
    DEVPROTO_MSG_CONFIG_ACK      = 0x84,
    DEVPROTO_MSG_STATUS_RESPONSE = 0x85,
    DEVPROTO_MSG_REBOOT_ACK      = 0x86,

    /* Async Events (Device -> PC, unsolicited) */
    DEVPROTO_MSG_ALERT_EVENT        = 0xA1,
    DEVPROTO_MSG_THRESHOLD_EXCEEDED = 0xA2,
    DEVPROTO_MSG_HARDWARE_FAULT     = 0xA3,
    DEVPROTO_MSG_CONNECTION_LOST    = 0xA4
} devproto_msg_type_t;

/**
 * Device status codes - compatible with Python DeviceStatus enum
 */
typedef enum {
    DEVPROTO_STATUS_OK          = 0x00,
    DEVPROTO_STATUS_WARNING     = 0x01,
    DEVPROTO_STATUS_ERROR       = 0x02,
    DEVPROTO_STATUS_CRITICAL    = 0x03,
    DEVPROTO_STATUS_MAINTENANCE = 0x04,
    DEVPROTO_STATUS_OFFLINE     = 0x05
} devproto_status_t;

/**
 * Command types - compatible with Python CommandType enum
 */
typedef enum {
    DEVPROTO_CMD_RESTART_SERVICE   = 0x01,
    DEVPROTO_CMD_CLEAR_CACHE       = 0x02,
    DEVPROTO_CMD_ROTATE_LOGS       = 0x03,
    DEVPROTO_CMD_SET_FAN_SPEED     = 0x04,
    DEVPROTO_CMD_SET_POWER_MODE    = 0x05,
    DEVPROTO_CMD_CALIBRATE_ANTENNA = 0x06,
    DEVPROTO_CMD_SWITCH_CHANNEL    = 0x07,
    DEVPROTO_CMD_ENABLE_FILTER     = 0x08,
    DEVPROTO_CMD_BLOCK_IP          = 0x09,
    DEVPROTO_CMD_RUN_DIAGNOSTIC    = 0x0A,
    DEVPROTO_CMD_CUSTOM_SHELL      = 0xFF
} devproto_cmd_type_t;

/**
 * Protocol message structure
 */
typedef struct {
    uint8_t  msg_type;      /* Message type (devproto_msg_type_t) */
    uint8_t  sequence;      /* Sequence number (0-255) */
    uint16_t payload_len;   /* Payload length */
    uint8_t  *payload;      /* Pointer to payload data (can be NULL) */
} devproto_message_t;

/**
 * Frame header structure (packed for wire format)
 */
typedef struct __attribute__((packed)) {
    uint8_t  header_hi;     /* 0xAA */
    uint8_t  header_lo;     /* 0x55 */
    uint8_t  length_hi;     /* Payload length MSB */
    uint8_t  length_lo;     /* Payload length LSB */
    uint8_t  msg_type;      /* Message type */
    uint8_t  sequence;      /* Sequence number */
} devproto_frame_header_t;

/**
 * Status response payload structure
 */
typedef struct __attribute__((packed)) {
    uint8_t  status;        /* Device status (devproto_status_t) */
    uint32_t uptime;        /* Uptime in seconds (big-endian) */
    uint16_t error_count;   /* Error count (big-endian) */
    uint16_t warning_count; /* Warning count (big-endian) */
} devproto_status_payload_t;

/**
 * Command result payload header
 */
typedef struct __attribute__((packed)) {
    uint8_t  success;       /* 0x00 = success, 0x01 = failure */
    uint8_t  return_code;   /* Shell return code */
    /* Followed by UTF-8 output string */
} devproto_cmd_result_t;

/* Message helper functions - implemented in protocol.c */

/**
 * Initialize a message structure
 */
void devproto_message_init(devproto_message_t *msg);

/**
 * Create a PING message
 * @param msg       Message structure to fill
 * @param sequence  Sequence number
 */
void devproto_create_ping(devproto_message_t *msg, uint8_t sequence);

/**
 * Create a PONG response
 * @param msg       Message structure to fill
 * @param sequence  Sequence number (should match request)
 */
void devproto_create_pong(devproto_message_t *msg, uint8_t sequence);

/**
 * Create a metrics request
 * @param msg       Message structure to fill
 * @param sequence  Sequence number
 * @param types     Array of metric types to request (NULL for all)
 * @param num_types Number of metric types
 */
void devproto_create_metrics_request(devproto_message_t *msg, uint8_t sequence,
                                     const uint8_t *types, size_t num_types);

/**
 * Create a status request
 * @param msg       Message structure to fill
 * @param sequence  Sequence number
 */
void devproto_create_status_request(devproto_message_t *msg, uint8_t sequence);

/**
 * Create a command execution request
 * @param msg       Message structure to fill
 * @param sequence  Sequence number
 * @param cmd_type  Command type
 * @param params    Command parameters (can be NULL)
 * @param params_len Length of parameters
 */
void devproto_create_command(devproto_message_t *msg, uint8_t sequence,
                             devproto_cmd_type_t cmd_type,
                             const uint8_t *params, size_t params_len);

/**
 * Serialize a message to wire format
 * @param msg       Message to serialize
 * @param buffer    Output buffer
 * @param buf_size  Buffer size
 * @return          Number of bytes written, or -1 on error
 */
int devproto_message_serialize(const devproto_message_t *msg,
                               uint8_t *buffer, size_t buf_size);

/**
 * Check if message type is a response
 */
static inline bool devproto_is_response(uint8_t msg_type) {
    return (msg_type >= 0x80 && msg_type < 0xA0);
}

/**
 * Check if message type is an async event
 */
static inline bool devproto_is_event(uint8_t msg_type) {
    return (msg_type >= 0xA0);
}

/**
 * Get response type for a request
 */
static inline uint8_t devproto_response_type(uint8_t request_type) {
    return request_type | 0x80;
}

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_PROTOCOL_H */
