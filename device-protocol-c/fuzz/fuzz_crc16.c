/**
 * @file fuzz_crc16.c
 * @brief Fuzz testing for CRC-16 implementation
 *
 * Build with:
 *   clang -fsanitize=fuzzer,address -I../include -o fuzz_crc ../src/crc16.c fuzz_crc16.c
 *
 * Run:
 *   ./fuzz_crc corpus/ -max_len=65536
 */

#include <stdint.h>
#include <stddef.h>
#include "devproto/crc16.h"

/* libFuzzer entry point */
int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    /* Test CRC calculation with various input sizes */
    uint16_t crc = devproto_crc16(data, size);
    (void)crc;

    /* Test incremental CRC calculation if supported */
    if (size >= 2) {
        /* Calculate CRC on first half, then continue with second half */
        size_t half = size / 2;
        uint16_t crc1 = devproto_crc16(data, half);
        uint16_t crc2 = devproto_crc16(data + half, size - half);
        (void)crc1;
        (void)crc2;
    }

    /* Test with aligned and unaligned addresses */
    if (size > 1) {
        uint16_t crc_unaligned = devproto_crc16(data + 1, size - 1);
        (void)crc_unaligned;
    }

    return 0;
}

#ifdef STANDALONE_MAIN
#include <stdio.h>
#include <stdlib.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <input_file>\n", argv[0]);
        return 1;
    }

    FILE *f = fopen(argv[1], "rb");
    if (!f) {
        perror("fopen");
        return 1;
    }

    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    fseek(f, 0, SEEK_SET);

    uint8_t *data = malloc(fsize);
    if (!data) {
        fclose(f);
        return 1;
    }

    fread(data, 1, fsize, f);
    fclose(f);

    int result = LLVMFuzzerTestOneInput(data, fsize);
    free(data);
    return result;
}
#endif
