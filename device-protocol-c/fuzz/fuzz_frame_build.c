/**
 * @file fuzz_frame_build.c
 * @brief Fuzz testing for frame building and round-trip
 *
 * Build with:
 *   clang -fsanitize=fuzzer,address -I../include -o fuzz_build ../src/*.c fuzz_frame_build.c
 *
 * Run:
 *   ./fuzz_build corpus/ -max_len=4096
 */

#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "devproto/frame.h"
#include "devproto/protocol.h"

/* libFuzzer entry point */
int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    if (size < 4) return 0;

    /* Extract parameters from fuzzer input */
    uint8_t msg_type = data[0];
    uint8_t sequence = data[1];
    uint16_t payload_len = ((uint16_t)data[2] << 8) | data[3];

    /* Clamp payload length to available data and max */
    if (payload_len > size - 4) {
        payload_len = size - 4;
    }
    if (payload_len > DEVPROTO_MAX_PAYLOAD_SIZE) {
        payload_len = DEVPROTO_MAX_PAYLOAD_SIZE;
    }

    /* Build a message */
    devproto_message_t msg;
    msg.msg_type = msg_type;
    msg.sequence = sequence;
    msg.payload_len = payload_len;
    msg.payload = (payload_len > 0) ? (uint8_t *)(data + 4) : NULL;

    /* Build frame */
    uint8_t frame_buffer[DEVPROTO_MAX_FRAME_SIZE];
    int frame_len = devproto_frame_build(&msg, frame_buffer, sizeof(frame_buffer));

    if (frame_len > 0) {
        /* Parse it back - should get same message */
        devproto_frame_parser_t parser;
        devproto_message_t out_msg;

        devproto_frame_parser_init(&parser);
        for (int i = 0; i < frame_len; i++) {
            int result = devproto_frame_parse_byte(&parser, frame_buffer[i]);
            if (result == 1) {
                devproto_frame_get_message(&parser, &out_msg);
                /* Verify round-trip */
                if (out_msg.msg_type != msg_type ||
                    out_msg.sequence != sequence ||
                    out_msg.payload_len != payload_len) {
                    /* This would be a bug - abort to report */
                    __builtin_trap();
                }
                if (payload_len > 0 && out_msg.payload) {
                    if (memcmp(out_msg.payload, data + 4, payload_len) != 0) {
                        __builtin_trap();
                    }
                }
                break;
            } else if (result < 0) {
                /* CRC error on our own built frame - bug */
                __builtin_trap();
            }
        }
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
