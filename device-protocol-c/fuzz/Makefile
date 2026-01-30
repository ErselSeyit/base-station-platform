# Fuzzing Makefile for Device Protocol Library
#
# Requires: clang with libFuzzer support OR AFL++
#
# Usage:
#   make fuzz_frame    # Build and run frame parser fuzzer
#   make fuzz_crc      # Build and run CRC fuzzer
#   make fuzz_build    # Build and run frame build fuzzer
#   make all           # Build all fuzzers
#   make clean         # Clean build artifacts

# Compiler selection (clang required for libFuzzer)
CC = clang
CFLAGS = -std=c11 -Wall -Wextra -I../include -g

# Fuzzer flags
FUZZ_FLAGS = -fsanitize=fuzzer,address,undefined
FUZZ_FLAGS += -fno-omit-frame-pointer

# Source files from library
LIB_SRCS = ../src/crc16.c ../src/frame.c ../src/protocol.c ../src/metrics.c

# Corpus directories
CORPUS_DIR = corpus
CRASH_DIR = crashes

# Build directory
BUILD_DIR = build

# Targets
FUZZERS = $(BUILD_DIR)/fuzz_frame_parser \
          $(BUILD_DIR)/fuzz_crc16 \
          $(BUILD_DIR)/fuzz_frame_build

.PHONY: all clean run_frame run_crc run_build corpus

all: $(BUILD_DIR) $(FUZZERS)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR) $(CORPUS_DIR) $(CRASH_DIR)

# Frame parser fuzzer
$(BUILD_DIR)/fuzz_frame_parser: fuzz_frame_parser.c $(LIB_SRCS) | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(FUZZ_FLAGS) -o $@ $^

# CRC fuzzer (only needs crc16.c)
$(BUILD_DIR)/fuzz_crc16: fuzz_crc16.c ../src/crc16.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(FUZZ_FLAGS) -o $@ $^

# Frame build fuzzer
$(BUILD_DIR)/fuzz_frame_build: fuzz_frame_build.c $(LIB_SRCS) | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(FUZZ_FLAGS) -o $@ $^

# Run fuzzers
run_frame: $(BUILD_DIR)/fuzz_frame_parser corpus
	./$(BUILD_DIR)/fuzz_frame_parser $(CORPUS_DIR) -max_len=8192 \
		-artifact_prefix=$(CRASH_DIR)/

run_crc: $(BUILD_DIR)/fuzz_crc16 corpus
	./$(BUILD_DIR)/fuzz_crc16 $(CORPUS_DIR) -max_len=65536 \
		-artifact_prefix=$(CRASH_DIR)/

run_build: $(BUILD_DIR)/fuzz_frame_build corpus
	./$(BUILD_DIR)/fuzz_frame_build $(CORPUS_DIR) -max_len=4096 \
		-artifact_prefix=$(CRASH_DIR)/

# Create initial corpus with valid frames
corpus: $(BUILD_DIR)
	mkdir -p $(CORPUS_DIR)
	@echo "Creating seed corpus..."
	@# Valid PING frame: AA 55 00 00 01 00 CRC16
	@printf '\xAA\x55\x00\x00\x01\x00\xD1\x1C' > $(CORPUS_DIR)/ping_frame
	@# Valid PONG frame
	@printf '\xAA\x55\x00\x00\x02\x00\xE0\x2C' > $(CORPUS_DIR)/pong_frame
	@# Frame with small payload
	@printf '\xAA\x55\x00\x04\x03\x00TESTCRC' > $(CORPUS_DIR)/small_payload
	@# Empty file
	@touch $(CORPUS_DIR)/empty
	@# Random bytes
	@head -c 128 /dev/urandom > $(CORPUS_DIR)/random || true
	@echo "Seed corpus created in $(CORPUS_DIR)/"

# Standalone builds (for debugging without fuzzer)
standalone: $(BUILD_DIR)
	$(CC) $(CFLAGS) -DSTANDALONE_MAIN -fsanitize=address,undefined \
		-o $(BUILD_DIR)/fuzz_frame_standalone fuzz_frame_parser.c $(LIB_SRCS)
	$(CC) $(CFLAGS) -DSTANDALONE_MAIN -fsanitize=address,undefined \
		-o $(BUILD_DIR)/fuzz_crc_standalone fuzz_crc16.c ../src/crc16.c
	$(CC) $(CFLAGS) -DSTANDALONE_MAIN -fsanitize=address,undefined \
		-o $(BUILD_DIR)/fuzz_build_standalone fuzz_frame_build.c $(LIB_SRCS)

# AFL++ builds
afl: CC=afl-clang-fast
afl: FUZZ_FLAGS=-fsanitize=address,undefined
afl: all
	@echo "AFL++ fuzzers built. Run with:"
	@echo "  afl-fuzz -i $(CORPUS_DIR) -o findings -- ./$(BUILD_DIR)/fuzz_frame_parser"

clean:
	rm -rf $(BUILD_DIR) $(CRASH_DIR)

# Print help
help:
	@echo "Device Protocol Fuzzing Targets:"
	@echo "  make all        - Build all fuzzers"
	@echo "  make run_frame  - Run frame parser fuzzer"
	@echo "  make run_crc    - Run CRC fuzzer"
	@echo "  make run_build  - Run frame build fuzzer"
	@echo "  make corpus     - Create seed corpus"
	@echo "  make standalone - Build standalone (non-fuzzer) versions"
	@echo "  make afl        - Build for AFL++"
	@echo "  make clean      - Remove build artifacts"
