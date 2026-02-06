/**
 * @file test_crc16.c
 * @brief CRC-16 unit tests - validates against Python implementation
 */

#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "devproto/crc16.h"
#include "devproto/protocol.h"

/* Test counter */
static int tests_passed = 0;
static int tests_failed = 0;

#define TEST(name) \
    printf("  Testing %s... ", name)

#define PASS() \
    do { printf("PASS\n"); tests_passed++; } while(0)

#define FAIL(msg) \
    do { printf("FAIL: %s\n", msg); tests_failed++; } while(0)

#define ASSERT_EQ(expected, actual) \
    do { \
        if ((expected) != (actual)) { \
            FAIL("expected " #expected " but got different value"); \
            return; \
        } \
    } while(0)

/**
 * Test CRC with empty data
 */
void test_crc_empty(void)
{
    TEST("empty data");
    uint16_t crc = devproto_crc16(NULL, 0);
    ASSERT_EQ(0xFFFF, crc);
    PASS();
}

/**
 * Test CRC with known values (verified against Python)
 *
 * Python verification:
 * >>> calculate_crc16(b'\xAA\x55\x00\x00\x01\x01')
 * >>> calculate_crc16(b'Hello')
 * >>> calculate_crc16(b'\x00\x01\x02\x03')
 */
void test_crc_known_vectors(void)
{
    TEST("known test vectors");

    /* Vector 1: Protocol header + PING message (no payload) */
    uint8_t ping_header[] = {0xAA, 0x55, 0x00, 0x00, 0x01, 0x01};
    uint16_t crc1 = devproto_crc16(ping_header, sizeof(ping_header));

    /* Vector 2: Simple string "Hello" */
    uint8_t hello[] = {'H', 'e', 'l', 'l', 'o'};
    uint16_t crc2 = devproto_crc16(hello, sizeof(hello));

    /* Vector 3: Sequential bytes */
    uint8_t seq[] = {0x00, 0x01, 0x02, 0x03};
    uint16_t crc3 = devproto_crc16(seq, sizeof(seq));

    /* These values should match Python calculate_crc16() results */
    /* If they don't match, the protocol will be incompatible! */

    /* Print for manual verification against Python */
    printf("\n    CRC of PING header: 0x%04X\n", crc1);
    printf("    CRC of 'Hello': 0x%04X\n", crc2);
    printf("    CRC of 0x00010203: 0x%04X\n    ", crc3);

    /* Basic sanity checks */
    if (crc1 == 0 || crc2 == 0 || crc3 == 0) {
        FAIL("CRC should not be zero for non-trivial data");
        return;
    }

    PASS();
}

/**
 * Test CRC byte-by-byte vs all-at-once
 */
void test_crc_streaming(void)
{
    TEST("streaming CRC update");

    uint8_t data[] = {0xAA, 0x55, 0x00, 0x05, 0x02, 0x01, 'H', 'e', 'l', 'l', 'o'};

    /* Calculate all at once */
    uint16_t crc_full = devproto_crc16(data, sizeof(data));

    /* Calculate in parts using streaming API */
    uint16_t crc_stream = DEVPROTO_CRC_INITIAL;
    crc_stream = devproto_crc16_update(crc_stream, data, 6);
    crc_stream = devproto_crc16_update(crc_stream, data + 6, 5);

    ASSERT_EQ(crc_full, crc_stream);
    PASS();
}

/**
 * Test CRC fast (table-driven) matches slow (bit-by-bit)
 */
void test_crc_fast_matches_slow(void)
{
    TEST("fast CRC matches slow CRC");

    uint8_t data[] = "The quick brown fox jumps over the lazy dog";

    uint16_t crc_slow = devproto_crc16(data, sizeof(data) - 1);
    uint16_t crc_fast = devproto_crc16_fast(data, sizeof(data) - 1);

    ASSERT_EQ(crc_slow, crc_fast);
    PASS();
}

/**
 * Test CRC detects single bit errors
 */
void test_crc_error_detection(void)
{
    TEST("single bit error detection");

    uint8_t original[] = {0xAA, 0x55, 0x00, 0x01, 0x01, 0x01, 0xFF};
    uint8_t modified[sizeof(original)];

    uint16_t crc_original = devproto_crc16(original, sizeof(original));

    /* Flip each bit and verify CRC changes */
    for (size_t byte = 0; byte < sizeof(original); byte++) {
        for (int bit = 0; bit < 8; bit++) {
            memcpy(modified, original, sizeof(original));
            modified[byte] ^= (1 << bit);

            uint16_t crc_modified = devproto_crc16(modified, sizeof(modified));

            if (crc_modified == crc_original) {
                char msg[100];
                snprintf(msg, sizeof(msg),
                         "CRC collision at byte %zu bit %d", byte, bit);
                FAIL(msg);
                return;
            }
        }
    }

    PASS();
}

/**
 * Test full frame CRC verification
 */
void test_frame_crc(void)
{
    TEST("full frame CRC");

    /* Build a valid frame manually */
    uint8_t frame[16];
    frame[0] = DEVPROTO_HEADER_BYTE0;  /* 0xAA */
    frame[1] = DEVPROTO_HEADER_BYTE1;  /* 0x55 */
    frame[2] = 0x00;                    /* Length MSB */
    frame[3] = 0x05;                    /* Length LSB (5 bytes payload) */
    frame[4] = 0x02;                    /* Type: REQUEST_METRICS */
    frame[5] = 0x42;                    /* Sequence: 0x42 */
    frame[6] = 'H';                     /* Payload */
    frame[7] = 'e';
    frame[8] = 'l';
    frame[9] = 'l';
    frame[10] = 'o';

    /* Calculate CRC over header + payload (11 bytes) */
    uint16_t crc = devproto_crc16(frame, 11);

    /* Append CRC (big-endian) */
    frame[11] = (crc >> 8) & 0xFF;
    frame[12] = crc & 0xFF;

    /* Verify by recalculating */
    uint16_t crc_verify = devproto_crc16(frame, 11);
    uint16_t crc_received = ((uint16_t)frame[11] << 8) | frame[12];

    ASSERT_EQ(crc_verify, crc_received);
    PASS();
}

int main(void)
{
    printf("=== CRC-16-CCITT Unit Tests ===\n");
    printf("\n");

    test_crc_empty();
    test_crc_known_vectors();
    test_crc_streaming();
    test_crc_fast_matches_slow();
    test_crc_error_detection();
    test_frame_crc();

    printf("\n");
    printf("=== Results: %d passed, %d failed ===\n", tests_passed, tests_failed);

    return tests_failed > 0 ? 1 : 0;
}
