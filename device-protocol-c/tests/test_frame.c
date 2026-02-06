/**
 * @file test_frame.c
 * @brief Frame parser unit tests
 */

#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "devproto/frame.h"
#include "devproto/crc16.h"

static int tests_passed = 0;
static int tests_failed = 0;

#define TEST(name) printf("  Testing %s... ", name)
#define PASS() do { printf("PASS\n"); tests_passed++; } while(0)
#define FAIL(msg) do { printf("FAIL: %s\n", msg); tests_failed++; } while(0)

/**
 * Build a valid frame for testing
 */
static int build_test_frame(uint8_t *buffer, size_t buf_size,
                            uint8_t msg_type, uint8_t sequence,
                            const uint8_t *payload, size_t payload_len)
{
    if (buf_size < DEVPROTO_HEADER_SIZE + payload_len + DEVPROTO_CRC_SIZE) {
        return -1;
    }

    /* Header */
    buffer[0] = DEVPROTO_HEADER_BYTE0;
    buffer[1] = DEVPROTO_HEADER_BYTE1;
    buffer[2] = (payload_len >> 8) & 0xFF;
    buffer[3] = payload_len & 0xFF;
    buffer[4] = msg_type;
    buffer[5] = sequence;

    /* Payload */
    if (payload && payload_len > 0) {
        memcpy(&buffer[6], payload, payload_len);
    }

    /* CRC */
    size_t data_len = DEVPROTO_HEADER_SIZE + payload_len;
    uint16_t crc = devproto_crc16(buffer, data_len);
    buffer[data_len] = (crc >> 8) & 0xFF;
    buffer[data_len + 1] = crc & 0xFF;

    return (int)(data_len + DEVPROTO_CRC_SIZE);
}

/**
 * Test parser initialization
 */
void test_parser_init(void)
{
    TEST("parser initialization");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    if (parser.state != DEVPROTO_FRAME_STATE_IDLE) {
        FAIL("initial state should be IDLE");
        return;
    }
    if (parser.buffer_pos != 0) {
        FAIL("buffer_pos should be 0");
        return;
    }

    PASS();
}

/**
 * Test parsing complete PING frame
 */
void test_parse_ping(void)
{
    TEST("parse PING frame");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    uint8_t frame[16];
    int frame_len = build_test_frame(frame, sizeof(frame),
                                     DEVPROTO_MSG_PING, 0x01,
                                     NULL, 0);

    devproto_message_t msg;
    int count = devproto_frame_parse(&parser, frame, frame_len, &msg, 1);

    if (count != 1) {
        FAIL("expected 1 message");
        return;
    }
    if (msg.msg_type != DEVPROTO_MSG_PING) {
        FAIL("wrong message type");
        return;
    }
    if (msg.sequence != 0x01) {
        FAIL("wrong sequence number");
        return;
    }
    if (msg.payload_len != 0) {
        FAIL("PING should have no payload");
        return;
    }

    PASS();
}

/**
 * Test parsing frame with payload
 */
void test_parse_with_payload(void)
{
    TEST("parse frame with payload");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    uint8_t payload[] = {0x01, 0x02, 0x03, 0x04, 0x05};
    uint8_t frame[32];
    int frame_len = build_test_frame(frame, sizeof(frame),
                                     DEVPROTO_MSG_REQUEST_METRICS, 0x42,
                                     payload, sizeof(payload));

    devproto_message_t msg;
    int count = devproto_frame_parse(&parser, frame, frame_len, &msg, 1);

    if (count != 1) {
        FAIL("expected 1 message");
        return;
    }
    if (msg.msg_type != DEVPROTO_MSG_REQUEST_METRICS) {
        FAIL("wrong message type");
        return;
    }
    if (msg.payload_len != sizeof(payload)) {
        FAIL("wrong payload length");
        return;
    }
    if (memcmp(msg.payload, payload, sizeof(payload)) != 0) {
        FAIL("payload mismatch");
        return;
    }

    PASS();
}

/**
 * Test parsing byte by byte
 */
void test_parse_byte_by_byte(void)
{
    TEST("parse byte by byte");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    uint8_t payload[] = "Hello";
    uint8_t frame[32];
    int frame_len = build_test_frame(frame, sizeof(frame),
                                     DEVPROTO_MSG_EXECUTE_COMMAND, 0x99,
                                     payload, 5);

    /* Feed one byte at a time */
    int result = 0;
    for (int i = 0; i < frame_len; i++) {
        result = devproto_frame_parse_byte(&parser, frame[i]);
        if (i < frame_len - 1 && result != 0) {
            FAIL("unexpected completion before last byte");
            return;
        }
    }

    if (result != 1) {
        FAIL("expected frame complete");
        return;
    }

    devproto_message_t msg;
    if (devproto_frame_get_message(&parser, &msg) != 0) {
        FAIL("failed to get message");
        return;
    }

    if (msg.msg_type != DEVPROTO_MSG_EXECUTE_COMMAND) {
        FAIL("wrong message type");
        return;
    }

    PASS();
}

/**
 * Test CRC error detection
 */
void test_crc_error(void)
{
    TEST("CRC error detection");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    uint8_t frame[16];
    int frame_len = build_test_frame(frame, sizeof(frame),
                                     DEVPROTO_MSG_PING, 0x01,
                                     NULL, 0);

    /* Corrupt the CRC */
    frame[frame_len - 1] ^= 0xFF;

    devproto_message_t msg;
    int count = devproto_frame_parse(&parser, frame, frame_len, &msg, 1);

    if (count >= 0) {
        /* Should have CRC error, but might return 0 messages */
        if (parser.crc_errors == 0) {
            FAIL("CRC error not detected");
            return;
        }
    }

    PASS();
}

/**
 * Test sync recovery after garbage
 */
void test_sync_recovery(void)
{
    TEST("sync recovery after garbage");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    /* Build valid frame */
    uint8_t valid_frame[16];
    int valid_len = build_test_frame(valid_frame, sizeof(valid_frame),
                                     DEVPROTO_MSG_PONG, 0x55,
                                     NULL, 0);

    /* Prepend garbage */
    uint8_t data[64];
    data[0] = 0x12;  /* Garbage */
    data[1] = 0x34;
    data[2] = 0x56;
    data[3] = 0xAA;  /* False start */
    data[4] = 0x00;  /* Not 0x55 */
    data[5] = 0xAA;  /* Another false start */
    memcpy(&data[6], valid_frame, valid_len);

    devproto_message_t msg;
    int count = devproto_frame_parse(&parser, data, 6 + valid_len, &msg, 1);

    if (count != 1) {
        FAIL("expected to find 1 valid message");
        return;
    }
    if (msg.msg_type != DEVPROTO_MSG_PONG) {
        FAIL("wrong message type");
        return;
    }

    PASS();
}

/**
 * Test multiple frames in buffer
 */
void test_multiple_frames(void)
{
    TEST("multiple frames in buffer");

    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    /* Build two frames back to back */
    uint8_t frame1[16], frame2[16];
    int len1 = build_test_frame(frame1, sizeof(frame1),
                                DEVPROTO_MSG_PING, 0x01, NULL, 0);
    int len2 = build_test_frame(frame2, sizeof(frame2),
                                DEVPROTO_MSG_PONG, 0x02, NULL, 0);

    uint8_t combined[32];
    memcpy(combined, frame1, len1);
    memcpy(combined + len1, frame2, len2);

    devproto_message_t msgs[4];
    int count = devproto_frame_parse(&parser, combined, len1 + len2, msgs, 4);

    if (count != 2) {
        printf("(got %d) ", count);
        FAIL("expected 2 messages");
        return;
    }
    if (msgs[0].msg_type != DEVPROTO_MSG_PING) {
        FAIL("first message wrong type");
        return;
    }
    if (msgs[1].msg_type != DEVPROTO_MSG_PONG) {
        FAIL("second message wrong type");
        return;
    }

    PASS();
}

/**
 * Test frame builder
 */
void test_frame_build(void)
{
    TEST("frame builder");

    uint8_t payload[] = "Test payload";
    devproto_message_t msg = {
        .msg_type = DEVPROTO_MSG_COMMAND_RESULT,
        .sequence = 0x77,
        .payload = payload,
        .payload_len = sizeof(payload) - 1
    };

    uint8_t buffer[64];
    int len = devproto_frame_build(&msg, buffer, sizeof(buffer));

    if (len < 0) {
        FAIL("build failed");
        return;
    }

    /* Verify header */
    if (buffer[0] != DEVPROTO_HEADER_BYTE0 || buffer[1] != DEVPROTO_HEADER_BYTE1) {
        FAIL("wrong header");
        return;
    }

    /* Parse the built frame to verify it's valid */
    devproto_frame_parser_t parser;
    devproto_frame_parser_init(&parser);

    devproto_message_t parsed;
    int count = devproto_frame_parse(&parser, buffer, len, &parsed, 1);

    if (count != 1) {
        FAIL("couldn't parse built frame");
        return;
    }
    if (parsed.msg_type != msg.msg_type) {
        FAIL("type mismatch");
        return;
    }

    PASS();
}

int main(void)
{
    printf("=== Frame Parser Unit Tests ===\n");
    printf("\n");

    test_parser_init();
    test_parse_ping();
    test_parse_with_payload();
    test_parse_byte_by_byte();
    test_crc_error();
    test_sync_recovery();
    test_multiple_frames();
    test_frame_build();

    printf("\n");
    printf("=== Results: %d passed, %d failed ===\n", tests_passed, tests_failed);

    return tests_failed > 0 ? 1 : 0;
}
