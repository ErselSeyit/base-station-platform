# Edge Bridge - Go-based bridge for MIPS edge devices
# Multi-stage build for minimal image size

# Build stage
FROM golang:1.21-alpine AS builder

RUN apk add --no-cache git

WORKDIR /app

# Copy go mod files first for better caching
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the binary
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o edge-bridge ./cmd/edge-bridge

# Runtime stage
FROM alpine:3.19

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/edge-bridge .
COPY --from=builder /app/configs/bridge-docker.yaml ./configs/bridge.yaml

# Create non-root user
RUN adduser -D -u 1000 bridge
USER bridge

EXPOSE 9999

ENTRYPOINT ["./edge-bridge"]
CMD ["--config", "configs/bridge.yaml"]
