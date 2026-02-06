/**
 * @file frame.h
 * @brief Frame parser for protocol messages
 *
 * State machine based parser that handles:
 * - Frame synchronization (finding 0xAA55 header)
 * - Partial frame assembly
 * - CRC verification
 * - Multiple messages in single buffer
 */

#ifndef DEVPROTO_FRAME_H
#define DEVPROTO_FRAME_H

#include <stdint.h>
#include <stddef.h>
#include "protocol.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Parser state machine states
 */
typedef enum {
    DEVPROTO_FRAME_STATE_IDLE,          /* Waiting for header byte 0 */
    DEVPROTO_FRAME_STATE_HEADER_LO,     /* Got 0xAA, waiting for 0x55 */
    DEVPROTO_FRAME_STATE_LENGTH_HI,     /* Waiting for length MSB */
    DEVPROTO_FRAME_STATE_LENGTH_LO,     /* Waiting for length LSB */
    DEVPROTO_FRAME_STATE_TYPE,          /* Waiting for message type */
    DEVPROTO_FRAME_STATE_SEQUENCE,      /* Waiting for sequence number */
    DEVPROTO_FRAME_STATE_PAYLOAD,       /* Receiving payload */
    DEVPROTO_FRAME_STATE_CRC_HI,        /* Waiting for CRC MSB */
    DEVPROTO_FRAME_STATE_CRC_LO,        /* Waiting for CRC LSB */
    DEVPROTO_FRAME_STATE_COMPLETE,      /* Frame complete */
    DEVPROTO_FRAME_STATE_ERROR          /* CRC or framing error */
} devproto_frame_state_t;

/**
 * Parser error codes
 */
typedef enum {
    DEVPROTO_FRAME_OK = 0,
    DEVPROTO_FRAME_ERR_CRC = -1,        /* CRC mismatch */
    DEVPROTO_FRAME_ERR_OVERFLOW = -2,   /* Payload too large */
    DEVPROTO_FRAME_ERR_INVALID = -3     /* Invalid input */
} devproto_frame_error_t;

/**
 * Frame parser context
 */
typedef struct {
    devproto_frame_state_t state;       /* Current parser state */
    uint8_t  buffer[DEVPROTO_MAX_FRAME_SIZE];  /* Frame buffer */
    size_t   buffer_pos;                /* Current position in buffer */
    uint16_t expected_length;           /* Expected payload length */
    size_t   payload_received;          /* Bytes of payload received */

    /* Current frame being parsed */
    uint8_t  msg_type;
    uint8_t  sequence;
    uint16_t crc_received;

    /* Statistics */
    uint32_t frames_parsed;
    uint32_t crc_errors;
    uint32_t sync_errors;
} devproto_frame_parser_t;

/**
 * Initialize/reset frame parser
 * @param parser  Parser context
 */
void devproto_frame_parser_init(devproto_frame_parser_t *parser);

/**
 * Reset parser to initial state (clear buffer and state)
 * @param parser  Parser context
 */
void devproto_frame_parser_reset(devproto_frame_parser_t *parser);

/**
 * Feed data to parser
 * @param parser        Parser context
 * @param data          Input data
 * @param len           Data length
 * @param out_messages  Output array for complete messages
 * @param max_messages  Maximum messages to return
 * @return              Number of complete messages, or negative on error
 *
 * Note: Caller is responsible for freeing payload buffers in out_messages
 * if they were dynamically allocated.
 */
int devproto_frame_parse(devproto_frame_parser_t *parser,
                         const uint8_t *data, size_t len,
                         devproto_message_t *out_messages,
                         size_t max_messages);

/**
 * Feed single byte to parser (for byte-by-byte processing)
 * @param parser  Parser context
 * @param byte    Input byte
 * @return        1 if frame complete, 0 if need more data, negative on error
 */
int devproto_frame_parse_byte(devproto_frame_parser_t *parser, uint8_t byte);

/**
 * Get completed message after parse_byte returns 1
 * @param parser  Parser context
 * @param msg     Output message structure
 * @return        0 on success, -1 if no complete message
 */
int devproto_frame_get_message(devproto_frame_parser_t *parser,
                               devproto_message_t *msg);

/**
 * Build a frame from message (serialize)
 * @param msg       Message to serialize
 * @param buffer    Output buffer
 * @param buf_size  Buffer size
 * @return          Frame length on success, negative on error
 */
int devproto_frame_build(const devproto_message_t *msg,
                         uint8_t *buffer, size_t buf_size);

/**
 * Get parser statistics
 */
static inline uint32_t devproto_frame_get_parsed_count(const devproto_frame_parser_t *parser) {
    return parser->frames_parsed;
}

static inline uint32_t devproto_frame_get_crc_errors(const devproto_frame_parser_t *parser) {
    return parser->crc_errors;
}

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_FRAME_H */
