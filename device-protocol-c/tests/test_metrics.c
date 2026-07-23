/**
 * @file test_metrics.c
 * @brief Metric encode/decode tests — 6-byte entry carrying a band dimension
 */

#include <stdio.h>
#include <string.h>
#include <math.h>
#include "devproto/metrics.h"

static int tests_passed = 0;
static int tests_failed = 0;

#define TEST(name) printf("  Testing %s... ", name)
#define PASS() do { printf("PASS\n"); tests_passed++; } while (0)
#define FAIL(msg) do { printf("FAIL: %s\n", msg); tests_failed++; } while (0)

static int floats_equal(float a, float b) {
    return fabsf(a - b) < 1e-3f;
}

static void test_entry_is_six_bytes(void) {
    TEST("metric entry is 6 bytes on the wire");
    if (sizeof(devproto_metric_entry_t) != DEVPROTO_METRIC_ENTRY_SIZE
            || DEVPROTO_METRIC_ENTRY_SIZE != 6) {
        FAIL("entry size changed");
        return;
    }
    PASS();
}

static void test_encode_decode_carries_band(void) {
    TEST("encode/decode preserves type, band and value");

    devproto_metric_entry_t entry;
    if (devproto_metric_encode(&entry, DEVPROTO_METRIC_RSRP, DEVPROTO_BAND_N78, -78.5f) != 0) {
        FAIL("encode failed");
        return;
    }

    /* The band occupies the second byte, per the wire layout. */
    if (entry.type != DEVPROTO_METRIC_RSRP || entry.band != DEVPROTO_BAND_N78) {
        FAIL("wire bytes wrong");
        return;
    }

    devproto_metric_t decoded;
    if (devproto_metric_decode(&entry, &decoded) != 0) {
        FAIL("decode failed");
        return;
    }
    if (decoded.type != DEVPROTO_METRIC_RSRP
            || decoded.band != DEVPROTO_BAND_N78
            || !floats_equal(decoded.value, -78.5f)) {
        FAIL("round trip changed the metric");
        return;
    }
    PASS();
}

static void test_band_neutral_metric_defaults_to_no_band(void) {
    TEST("a non-radio metric carries BAND_NONE");

    devproto_metric_entry_t entry;
    devproto_metric_encode(&entry, DEVPROTO_METRIC_CPU_USAGE, DEVPROTO_BAND_NONE, 42.0f);

    devproto_metric_t decoded;
    devproto_metric_decode(&entry, &decoded);
    if (decoded.band != DEVPROTO_BAND_NONE) {
        FAIL("expected BAND_NONE");
        return;
    }
    PASS();
}

static void test_two_bands_same_type_stay_distinct(void) {
    TEST("same metric on two bands stays distinct through a round trip");

    devproto_metric_t in[2] = {
        { DEVPROTO_METRIC_DL_THROUGHPUT, DEVPROTO_BAND_N28, 65.0f },
        { DEVPROTO_METRIC_DL_THROUGHPUT, DEVPROTO_BAND_N78, 1200.0f },
    };

    uint8_t buffer[64];
    int written = devproto_metrics_build(in, 2, buffer, sizeof(buffer));
    if (written != 2 * DEVPROTO_METRIC_ENTRY_SIZE) {
        FAIL("unexpected encoded length");
        return;
    }

    devproto_metric_t out[2];
    int parsed = devproto_metrics_parse(buffer, (size_t)written, out, 2);
    if (parsed != 2) {
        FAIL("expected 2 metrics");
        return;
    }
    if (out[0].band != DEVPROTO_BAND_N28 || !floats_equal(out[0].value, 65.0f)
            || out[1].band != DEVPROTO_BAND_N78 || !floats_equal(out[1].value, 1200.0f)) {
        FAIL("band or value crossed over");
        return;
    }
    PASS();
}

static void test_names(void) {
    TEST("metric and band names are band-neutral");
    if (strcmp(devproto_metric_name(DEVPROTO_METRIC_RSRP), "RSRP") != 0
            || strcmp(devproto_band_name(DEVPROTO_BAND_N28), "N28") != 0
            || strcmp(devproto_band_name(DEVPROTO_BAND_N78), "N78") != 0
            || strcmp(devproto_band_name(DEVPROTO_BAND_NONE), "NONE") != 0) {
        FAIL("names wrong");
        return;
    }
    PASS();
}

int main(void) {
    printf("=== Metric Encode/Decode Tests ===\n\n");
    test_entry_is_six_bytes();
    test_encode_decode_carries_band();
    test_band_neutral_metric_defaults_to_no_band();
    test_two_bands_same_type_stay_distinct();
    test_names();
    printf("\n=== Results: %d passed, %d failed ===\n", tests_passed, tests_failed);
    return tests_failed == 0 ? 0 : 1;
}
