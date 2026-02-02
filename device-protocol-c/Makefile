# Device Protocol C Library Makefile
# Supports both host (x86_64) and MIPS cross-compilation

# Configuration
CROSS_COMPILE ?=
CC = $(CROSS_COMPILE)gcc
AR = $(CROSS_COMPILE)ar
STRIP = $(CROSS_COMPILE)strip

# C standard and flags
CSTD = -std=c11
CFLAGS = $(CSTD) -Wall -Wextra -Werror -O2 -fPIC
CFLAGS += -I$(CURDIR)/include
CFLAGS += -D_GNU_SOURCE -D_POSIX_C_SOURCE=200809L

# MIPS-specific flags (applied when cross-compiling)
ifneq ($(findstring mips,$(CROSS_COMPILE)),)
    MIPS_FLAGS = -march=mips32r2 -mtune=mips32r2 -mabi=32
    CFLAGS += $(MIPS_FLAGS)
endif

# Debug build
DEBUG ?= 0
ifeq ($(DEBUG),1)
    CFLAGS += -g -O0 -DDEBUG
else
    CFLAGS += -DNDEBUG
endif

# TLS support (requires mbedTLS)
TLS ?= 0
ifeq ($(TLS),1)
    CFLAGS += -DDEVPROTO_TLS_ENABLE
    LDFLAGS += -lmbedtls -lmbedcrypto -lmbedx509
endif

# Directories
SRC_DIR = src
INC_DIR = include
TEST_DIR = tests
EXAMPLE_DIR = examples
BUILD_DIR = build

# Source files
SRCS = $(SRC_DIR)/crc16.c \
       $(SRC_DIR)/frame.c \
       $(SRC_DIR)/protocol.c \
       $(SRC_DIR)/transport.c \
       $(SRC_DIR)/transport_serial.c \
       $(SRC_DIR)/transport_tcp.c \
       $(SRC_DIR)/transport_tls.c \
       $(SRC_DIR)/metrics.c

OBJS = $(SRCS:$(SRC_DIR)/%.c=$(BUILD_DIR)/%.o)

# Output
LIB_STATIC = $(BUILD_DIR)/libdevproto.a
LIB_SHARED = $(BUILD_DIR)/libdevproto.so

# Test sources
TEST_SRCS = $(TEST_DIR)/test_crc16.c \
            $(TEST_DIR)/test_frame.c

TEST_BINS = $(TEST_SRCS:$(TEST_DIR)/%.c=$(BUILD_DIR)/%)

# Example sources
EXAMPLE_SRCS = $(EXAMPLE_DIR)/host_client.c \
               $(EXAMPLE_DIR)/mips_device.c

EXAMPLE_BINS = $(EXAMPLE_SRCS:$(EXAMPLE_DIR)/%.c=$(BUILD_DIR)/%)

# Targets
.PHONY: all clean test host mips examples install fuzz

all: $(BUILD_DIR) $(LIB_STATIC) $(LIB_SHARED)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Static library
$(LIB_STATIC): $(OBJS)
	$(AR) rcs $@ $^

# Shared library
$(LIB_SHARED): $(OBJS)
	$(CC) -shared -o $@ $^ -lpthread $(LDFLAGS)

# Object files
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c -o $@ $<

# Host (native) build
host:
	$(MAKE) CROSS_COMPILE= DEBUG=$(DEBUG)

# MIPS cross-compile (supports both mips-linux-gnu- and mips-linux- prefixes)
mips:
	@if command -v mips-linux-gnu-gcc >/dev/null 2>&1; then \
		$(MAKE) CROSS_COMPILE=mips-linux-gnu- DEBUG=$(DEBUG); \
	elif command -v mips-linux-gcc >/dev/null 2>&1; then \
		$(MAKE) CROSS_COMPILE=mips-linux- DEBUG=$(DEBUG); \
	else \
		echo "Error: No MIPS cross-compiler found."; \
		echo "Install Bootlin toolchain: https://toolchains.bootlin.com/downloads/releases/toolchains/mips32/"; \
		exit 1; \
	fi

# Build and run tests (host only)
test: host $(TEST_BINS)
	@echo "Running tests..."
	@for test in $(TEST_BINS); do \
		echo "=== Running $$test ==="; \
		./$$test || exit 1; \
	done
	@echo "All tests passed!"

# Build tests
$(BUILD_DIR)/test_%: $(TEST_DIR)/test_%.c $(LIB_STATIC)
	$(CC) $(CFLAGS) -o $@ $< -L$(BUILD_DIR) -ldevproto

# Build examples
examples: all $(EXAMPLE_BINS)

$(BUILD_DIR)/host_client: $(EXAMPLE_DIR)/host_client.c $(LIB_STATIC)
	$(CC) $(CFLAGS) -o $@ $< -L$(BUILD_DIR) -ldevproto

$(BUILD_DIR)/mips_device: $(EXAMPLE_DIR)/mips_device.c $(LIB_STATIC)
	$(CC) $(CFLAGS) -o $@ $< -L$(BUILD_DIR) -ldevproto

# Clean
clean:
	rm -rf $(BUILD_DIR)

# Install
PREFIX ?= /usr/local
install: all
	install -d $(PREFIX)/lib $(PREFIX)/include/devproto
	install -m 644 $(LIB_STATIC) $(LIB_SHARED) $(PREFIX)/lib/
	install -m 644 $(INC_DIR)/devproto/*.h $(PREFIX)/include/devproto/

# Fuzzing (requires clang with libFuzzer)
fuzz:
	$(MAKE) -C fuzz all

fuzz-run:
	$(MAKE) -C fuzz run_frame

# Show configuration
info:
	@echo "CC = $(CC)"
	@echo "CFLAGS = $(CFLAGS)"
	@echo "CROSS_COMPILE = $(CROSS_COMPILE)"
	@echo "DEBUG = $(DEBUG)"
