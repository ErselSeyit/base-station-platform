/**
 * @file fuzz_frame_parser.c
 * @brief Fuzz testing for frame parser
 *
 * Build with:
 *   clang -fsanitize=fuzzer,address -I../include -o fuzz_frame ../src/*.c fuzz_frame_parser.c
 *
 * Run:
 *   ./fuzz_frame corpus/ -max_len=8192
 */

#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "devproto/frame.h"

/* libFuzzer entry point */
int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    devproto_frame_parser_t parser;
    devproto_message_t messages[16];

    /* Test 1: Parse byte-by-byte */
    devproto_frame_parser_init(&parser);
    for (size_t i = 0; i < size; i++) {
        int result = devproto_frame_parse_byte(&parser, data[i]);
        if (result == 1) {
            /* Frame complete - extract message */
            devproto_message_t msg;
            devproto_frame_get_message(&parser, &msg);
            devproto_frame_parser_reset(&parser);
        }
    }

    /* Test 2: Parse in chunks */
    devproto_frame_parser_reset(&parser);
    int count = devproto_frame_parse(&parser, data, size, messages, 16);
    (void)count;

    /* Test 3: Parse with various chunk sizes */
    devproto_frame_parser_reset(&parser);
    size_t offset = 0;
    while (offset < size) {
        size_t chunk = (size - offset) > 64 ? 64 : (size - offset);
        devproto_frame_parse(&parser, data + offset, chunk, messages, 16);
        offset += chunk;
    }

    return 0;
}

#ifdef STANDALONE_MAIN
/* For testing without fuzzer */
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
