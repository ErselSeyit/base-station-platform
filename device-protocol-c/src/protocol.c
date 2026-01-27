/**
 * @file protocol.c
 * @brief High-level protocol message helpers
 */

#include <string.h>
#include "devproto/protocol.h"
#include "devproto/frame.h"
#include "devproto/metrics.h"
#include "devproto/error.h"

/**
 * Initialize a message structure
 */
void devproto_message_init(devproto_message_t *msg)
{
    if (!msg) return;

    memset(msg, 0, sizeof(*msg));
}

/**
 * Create a PING message
 */
void devproto_create_ping(devproto_message_t *msg, uint8_t sequence)
{
    if (!msg) return;

    msg->msg_type = DEVPROTO_MSG_PING;
    msg->sequence = sequence;
    msg->payload_len = 0;
    msg->payload = NULL;
}

/**
 * Create a PONG response
 */
void devproto_create_pong(devproto_message_t *msg, uint8_t sequence)
{
    if (!msg) return;

    msg->msg_type = DEVPROTO_MSG_PONG;
    msg->sequence = sequence;
    msg->payload_len = 0;
    msg->payload = NULL;
}

/**
 * Create a metrics request
 */
void devproto_create_metrics_request(devproto_message_t *msg, uint8_t sequence,
                                     const uint8_t *types, size_t num_types)
{
    if (!msg) return;

    msg->msg_type = DEVPROTO_MSG_REQUEST_METRICS;
    msg->sequence = sequence;

    if (types && num_types > 0) {
        msg->payload = (uint8_t *)types;
        msg->payload_len = (uint16_t)num_types;
    } else {
        /* Request all metrics */
        static uint8_t all_metrics = DEVPROTO_METRIC_ALL;
        msg->payload = &all_metrics;
        msg->payload_len = 1;
    }
}

/**
 * Create a status request
 */
void devproto_create_status_request(devproto_message_t *msg, uint8_t sequence)
{
    if (!msg) return;

    msg->msg_type = DEVPROTO_MSG_GET_STATUS;
    msg->sequence = sequence;
    msg->payload_len = 0;
    msg->payload = NULL;
}

/**
 * Create a command execution request
 * Note: Caller must provide a buffer where params[0] will be overwritten with cmd_type
 */
void devproto_create_command(devproto_message_t *msg, uint8_t sequence,
                             devproto_cmd_type_t cmd_type,
                             const uint8_t *params, size_t params_len)
{
    if (!msg) return;

    msg->msg_type = DEVPROTO_MSG_EXECUTE_COMMAND;
    msg->sequence = sequence;

    /* Command payload format: [cmd_type][params...] */
    /* For simplicity, we store cmd_type in the message and expect caller
     * to have allocated buffer starting with cmd_type */
    (void)cmd_type;  /* Used by caller to build payload buffer */

    msg->payload = (uint8_t *)params;
    msg->payload_len = (uint16_t)params_len;
}

/**
 * Serialize a message to wire format
 */
int devproto_message_serialize(const devproto_message_t *msg,
                               uint8_t *buffer, size_t buf_size)
{
    return devproto_frame_build(msg, buffer, buf_size);
}

/**
 * Get error description string
 */
const char *devproto_strerror(devproto_error_t err)
{
    switch (err) {
    case DEVPROTO_OK:           return "Success";
    case DEVPROTO_ERR_INVALID:  return "Invalid argument";
    case DEVPROTO_ERR_NOMEM:    return "Out of memory";
    case DEVPROTO_ERR_CRC:      return "CRC check failed";
    case DEVPROTO_ERR_OVERFLOW: return "Buffer overflow";
    case DEVPROTO_ERR_TIMEOUT:  return "Operation timed out";
    case DEVPROTO_ERR_IO:       return "I/O error";
    case DEVPROTO_ERR_CLOSED:   return "Connection closed";
    case DEVPROTO_ERR_PROTOCOL: return "Protocol error";
    case DEVPROTO_ERR_NOT_FOUND:return "Not found";
    case DEVPROTO_ERR_BUSY:     return "Resource busy";
    default:                    return "Unknown error";
    }
}
