/**
 * @file frame.c
 * @brief Frame parser state machine implementation
 */

#include <string.h>
#include <stdint.h>
#include <limits.h>
#include "devproto/frame.h"
#include "devproto/crc16.h"

/**
 * Initialize frame parser
 */
void devproto_frame_parser_init(devproto_frame_parser_t *parser)
{
    if (!parser) return;

    memset(parser, 0, sizeof(*parser));
    parser->state = DEVPROTO_FRAME_STATE_IDLE;
}

/**
 * Reset parser to initial state
 */
void devproto_frame_parser_reset(devproto_frame_parser_t *parser)
{
    if (!parser) return;

    parser->state = DEVPROTO_FRAME_STATE_IDLE;
    parser->buffer_pos = 0;
    parser->expected_length = 0;
    parser->payload_received = 0;
    parser->msg_type = 0;
    parser->sequence = 0;
    parser->crc_received = 0;
}

/**
 * Process single byte through state machine
 */
int devproto_frame_parse_byte(devproto_frame_parser_t *parser, uint8_t byte)
{
    if (!parser) return DEVPROTO_FRAME_ERR_INVALID;

    switch (parser->state) {
    case DEVPROTO_FRAME_STATE_IDLE:
        if (byte == DEVPROTO_HEADER_BYTE0) {
            parser->buffer[0] = byte;
            parser->buffer_pos = 1;
            parser->state = DEVPROTO_FRAME_STATE_HEADER_LO;
        }
        break;

    case DEVPROTO_FRAME_STATE_HEADER_LO:
        if (byte == DEVPROTO_HEADER_BYTE1) {
            parser->buffer[1] = byte;
            parser->buffer_pos = 2;
            parser->state = DEVPROTO_FRAME_STATE_LENGTH_HI;
        } else if (byte == DEVPROTO_HEADER_BYTE0) {
            /* Might be start of new frame */
            parser->buffer[0] = byte;
            parser->buffer_pos = 1;
        } else {
            parser->sync_errors++;
            devproto_frame_parser_reset(parser);
        }
        break;

    case DEVPROTO_FRAME_STATE_LENGTH_HI:
        parser->buffer[2] = byte;
        parser->buffer_pos = 3;
        parser->expected_length = (uint16_t)byte << 8;
        parser->state = DEVPROTO_FRAME_STATE_LENGTH_LO;
        break;

    case DEVPROTO_FRAME_STATE_LENGTH_LO:
        parser->buffer[3] = byte;
        parser->buffer_pos = 4;
        parser->expected_length |= byte;

        /* Validate payload length */
        if (parser->expected_length > DEVPROTO_MAX_PAYLOAD_SIZE) {
            parser->sync_errors++;
            devproto_frame_parser_reset(parser);
            return DEVPROTO_FRAME_ERR_OVERFLOW;
        }

        parser->state = DEVPROTO_FRAME_STATE_TYPE;
        break;

    case DEVPROTO_FRAME_STATE_TYPE:
        parser->buffer[4] = byte;
        parser->buffer_pos = 5;
        parser->msg_type = byte;
        parser->state = DEVPROTO_FRAME_STATE_SEQUENCE;
        break;

    case DEVPROTO_FRAME_STATE_SEQUENCE:
        parser->buffer[5] = byte;
        parser->buffer_pos = 6;
        parser->sequence = byte;
        parser->payload_received = 0;

        if (parser->expected_length == 0) {
            /* No payload, go to CRC */
            parser->state = DEVPROTO_FRAME_STATE_CRC_HI;
        } else {
            parser->state = DEVPROTO_FRAME_STATE_PAYLOAD;
        }
        break;

    case DEVPROTO_FRAME_STATE_PAYLOAD:
        if (parser->buffer_pos < DEVPROTO_MAX_FRAME_SIZE) {
            parser->buffer[parser->buffer_pos++] = byte;
            parser->payload_received++;

            if (parser->payload_received >= parser->expected_length) {
                parser->state = DEVPROTO_FRAME_STATE_CRC_HI;
            }
        } else {
            parser->sync_errors++;
            devproto_frame_parser_reset(parser);
            return DEVPROTO_FRAME_ERR_OVERFLOW;
        }
        break;

    case DEVPROTO_FRAME_STATE_CRC_HI:
        parser->crc_received = (uint16_t)byte << 8;
        parser->state = DEVPROTO_FRAME_STATE_CRC_LO;
        break;

    case DEVPROTO_FRAME_STATE_CRC_LO:
        parser->crc_received |= byte;

        /* Verify CRC (calculated over header + payload) */
        {
            size_t data_len = DEVPROTO_HEADER_SIZE + parser->expected_length;
            uint16_t crc_calc = devproto_crc16(parser->buffer, data_len);

            if (crc_calc == parser->crc_received) {
                parser->state = DEVPROTO_FRAME_STATE_COMPLETE;
                parser->frames_parsed++;
                return 1; /* Frame complete */
            } else {
                parser->crc_errors++;
                parser->state = DEVPROTO_FRAME_STATE_ERROR;
                devproto_frame_parser_reset(parser);
                return DEVPROTO_FRAME_ERR_CRC;
            }
        }

    case DEVPROTO_FRAME_STATE_COMPLETE:
    case DEVPROTO_FRAME_STATE_ERROR:
        /* Should call reset before continuing */
        devproto_frame_parser_reset(parser);
        if (byte == DEVPROTO_HEADER_BYTE0) {
            parser->buffer[0] = byte;
            parser->buffer_pos = 1;
            parser->state = DEVPROTO_FRAME_STATE_HEADER_LO;
        }
        break;
    }

    return 0; /* Need more data */
}

/**
 * Get completed message after parse_byte returns 1
 */
int devproto_frame_get_message(devproto_frame_parser_t *parser,
                               devproto_message_t *msg)
{
    if (!parser || !msg) return -1;
    if (parser->state != DEVPROTO_FRAME_STATE_COMPLETE) return -1;

    msg->msg_type = parser->msg_type;
    msg->sequence = parser->sequence;
    msg->payload_len = parser->expected_length;

    /* Payload starts at offset 6 in buffer */
    if (parser->expected_length > 0) {
        msg->payload = &parser->buffer[DEVPROTO_HEADER_SIZE];
    } else {
        msg->payload = NULL;
    }

    return 0;
}

/**
 * Parse multiple bytes, returning complete messages
 */
int devproto_frame_parse(devproto_frame_parser_t *parser,
                         const uint8_t *data, size_t len,
                         devproto_message_t *out_messages,
                         size_t max_messages)
{
    if (!parser || !data || !out_messages || max_messages == 0) {
        return DEVPROTO_FRAME_ERR_INVALID;
    }

    /* Limit max_messages to prevent return value truncation */
    if (max_messages > (size_t)INT32_MAX) {
        max_messages = (size_t)INT32_MAX;
    }

    size_t msg_count = 0;

    for (size_t i = 0; i < len && msg_count < max_messages; i++) {
        int result = devproto_frame_parse_byte(parser, data[i]);

        if (result == 1) {
            /* Frame complete */
            if (devproto_frame_get_message(parser, &out_messages[msg_count]) == 0) {
                /* Copy payload to separate buffer so parser can be reset */
                if (out_messages[msg_count].payload_len > 0) {
                    /* Note: In production, you'd allocate and copy here.
                     * For simplicity, we just point to parser buffer.
                     * Caller must use before next parse. */
                }
                msg_count++;
            }
            devproto_frame_parser_reset(parser);
        } else if (result < 0) {
            /* Error - parser already reset, continue scanning */
        }
    }

    return (int)msg_count;
}

/**
 * Build frame from message
 */
int devproto_frame_build(const devproto_message_t *msg,
                         uint8_t *buffer, size_t buf_size)
{
    if (!msg || !buffer) return -1;

    /* Validate payload length first to prevent integer overflow */
    if (msg->payload_len > DEVPROTO_MAX_PAYLOAD_SIZE) return -1;

    /* Safe calculation: HEADER_SIZE and CRC_SIZE are constants, payload_len is bounded */
    size_t frame_len = DEVPROTO_HEADER_SIZE + msg->payload_len + DEVPROTO_CRC_SIZE;
    if (buf_size < frame_len) return -1;

    /* Prevent return value truncation (frame_len fits in int due to MAX_PAYLOAD_SIZE limit) */
    if (frame_len > (size_t)INT32_MAX) return -1;

    /* Header */
    buffer[0] = DEVPROTO_HEADER_BYTE0;
    buffer[1] = DEVPROTO_HEADER_BYTE1;

    /* Length (big-endian) */
    buffer[2] = (msg->payload_len >> 8) & 0xFF;
    buffer[3] = msg->payload_len & 0xFF;

    /* Type and sequence */
    buffer[4] = msg->msg_type;
    buffer[5] = msg->sequence;

    /* Payload */
    if (msg->payload_len > 0 && msg->payload) {
        memcpy(&buffer[6], msg->payload, msg->payload_len);
    }

    /* CRC (calculated over header + payload) */
    size_t data_len = DEVPROTO_HEADER_SIZE + msg->payload_len;
    uint16_t crc = devproto_crc16(buffer, data_len);
    buffer[data_len] = (crc >> 8) & 0xFF;
    buffer[data_len + 1] = crc & 0xFF;

    return (int)frame_len;
}
