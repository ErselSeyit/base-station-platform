# Edge Bridge Makefile

# Build variables
VERSION := 0.1.0
BUILD_TIME := $(shell date -u '+%Y-%m-%d_%H:%M:%S')
LDFLAGS := -ldflags "-X main.version=$(VERSION) -X main.buildTime=$(BUILD_TIME)"

# Go commands
GO := go
GOFMT := gofmt
GOTEST := $(GO) test
GOBUILD := $(GO) build

# Directories
BUILD_DIR := build
CMD_DIR := cmd/edge-bridge

# Binary names
BINARY := edge-bridge
BINARY_MIPS := $(BINARY)-mips

# Default target
.PHONY: all
all: build

# Build for current platform
.PHONY: build
build: deps
	@echo "Building for current platform..."
	@mkdir -p $(BUILD_DIR)
	$(GOBUILD) $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY) ./$(CMD_DIR)
	@echo "Built: $(BUILD_DIR)/$(BINARY)"

# Build for MIPS
.PHONY: mips
mips: deps
	@echo "Building for MIPS32..."
	@mkdir -p $(BUILD_DIR)
	GOOS=linux GOARCH=mips GOMIPS=softfloat $(GOBUILD) $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY_MIPS) ./$(CMD_DIR)
	@echo "Built: $(BUILD_DIR)/$(BINARY_MIPS)"

# Build for MIPS64
.PHONY: mips64
mips64: deps
	@echo "Building for MIPS64..."
	@mkdir -p $(BUILD_DIR)
	GOOS=linux GOARCH=mips64 $(GOBUILD) $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY)-mips64 ./$(CMD_DIR)
	@echo "Built: $(BUILD_DIR)/$(BINARY)-mips64"

# Build all platforms
.PHONY: build-all
build-all: build mips mips64
	@echo "Built all platforms"

# Install dependencies
.PHONY: deps
deps:
	@echo "Downloading dependencies..."
	$(GO) mod download
	$(GO) mod tidy

# Run tests
.PHONY: test
test:
	@echo "Running tests..."
	$(GOTEST) -v ./...

# Run tests with coverage
.PHONY: test-cover
test-cover:
	@echo "Running tests with coverage..."
	$(GOTEST) -v -coverprofile=$(BUILD_DIR)/coverage.out ./...
	$(GO) tool cover -html=$(BUILD_DIR)/coverage.out -o $(BUILD_DIR)/coverage.html
	@echo "Coverage report: $(BUILD_DIR)/coverage.html"

# Format code
.PHONY: fmt
fmt:
	@echo "Formatting code..."
	$(GOFMT) -s -w .

# Lint code
.PHONY: lint
lint:
	@echo "Linting code..."
	$(GO) vet ./...

# Clean build artifacts
.PHONY: clean
clean:
	@echo "Cleaning..."
	rm -rf $(BUILD_DIR)
	$(GO) clean

# Run the bridge (for testing)
.PHONY: run
run: build
	@echo "Running edge bridge..."
	$(BUILD_DIR)/$(BINARY) --transport tcp --tcp-host 127.0.0.1 --tcp-port 9999

# Run with config file
.PHONY: run-config
run-config: build
	@echo "Running edge bridge with config..."
	$(BUILD_DIR)/$(BINARY) --config configs/bridge.yaml

# Check binary info
.PHONY: info
info:
	@echo "Binary information:"
	@if [ -f $(BUILD_DIR)/$(BINARY) ]; then \
		file $(BUILD_DIR)/$(BINARY); \
	fi
	@if [ -f $(BUILD_DIR)/$(BINARY_MIPS) ]; then \
		file $(BUILD_DIR)/$(BINARY_MIPS); \
	fi

# Install binary to system
.PHONY: install
install: build
	@echo "Installing edge-bridge..."
	install -m 755 $(BUILD_DIR)/$(BINARY) /usr/local/bin/

# Uninstall binary
.PHONY: uninstall
uninstall:
	@echo "Uninstalling edge-bridge..."
	rm -f /usr/local/bin/$(BINARY)

# Help
.PHONY: help
help:
	@echo "Edge Bridge Build System"
	@echo ""
	@echo "Targets:"
	@echo "  build       Build for current platform"
	@echo "  mips        Build for MIPS32"
	@echo "  mips64      Build for MIPS64"
	@echo "  build-all   Build all platforms"
	@echo "  test        Run tests"
	@echo "  test-cover  Run tests with coverage"
	@echo "  fmt         Format code"
	@echo "  lint        Lint code"
	@echo "  clean       Clean build artifacts"
	@echo "  run         Build and run (TCP mode)"
	@echo "  run-config  Build and run with config file"
	@echo "  info        Show binary information"
	@echo "  install     Install to /usr/local/bin"
	@echo "  uninstall   Remove from /usr/local/bin"
	@echo "  help        Show this help"
