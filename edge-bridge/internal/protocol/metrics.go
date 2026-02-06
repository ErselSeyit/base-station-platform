package protocol

import (
	"encoding/binary"
	"errors"
	"math"
)

// Metric encoding errors
var (
	ErrInvalidMetricPayload = errors.New("invalid metric payload")
	ErrMetricBufferTooSmall = errors.New("buffer too small for metrics")
)

// MetricEntrySize is the size of a single metric entry (1 byte type + 4 bytes float32).
const MetricEntrySize = 5

// EncodeMetrics encodes a slice of metrics into wire format.
// Format: [type(1)][value(4 float32 big-endian)]...
func EncodeMetrics(metrics []Metric) []byte {
	if len(metrics) == 0 {
		return nil
	}

	buf := make([]byte, len(metrics)*MetricEntrySize)
	offset := 0

	for _, m := range metrics {
		buf[offset] = byte(m.Type)
		binary.BigEndian.PutUint32(buf[offset+1:], math.Float32bits(m.Value))
		offset += MetricEntrySize
	}

	return buf
}

// DecodeMetrics decodes wire format data into a slice of metrics.
func DecodeMetrics(data []byte) ([]Metric, error) {
	if len(data) == 0 {
		return nil, nil
	}

	if len(data)%MetricEntrySize != 0 {
		return nil, ErrInvalidMetricPayload
	}

	count := len(data) / MetricEntrySize
	metrics := make([]Metric, count)

	for i := 0; i < count; i++ {
		offset := i * MetricEntrySize
		metrics[i].Type = MetricType(data[offset])
		metrics[i].Value = math.Float32frombits(binary.BigEndian.Uint32(data[offset+1:]))
	}

	return metrics, nil
}

// EncodeStatus encodes a status payload into wire format.
// Format: [status(1)][uptime(4)][errors(2)][warnings(2)]
func EncodeStatus(status *StatusPayload) []byte {
	buf := make([]byte, 9)
	buf[0] = byte(status.Status)
	binary.BigEndian.PutUint32(buf[1:5], status.Uptime)
	binary.BigEndian.PutUint16(buf[5:7], status.Errors)
	binary.BigEndian.PutUint16(buf[7:9], status.Warnings)
	return buf
}

// DecodeStatus decodes wire format data into a status payload.
func DecodeStatus(data []byte) (*StatusPayload, error) {
	if len(data) < 9 {
		return nil, errors.New("status payload too short")
	}

	return &StatusPayload{
		Status:   StatusCode(data[0]),
		Uptime:   binary.BigEndian.Uint32(data[1:5]),
		Errors:   binary.BigEndian.Uint16(data[5:7]),
		Warnings: binary.BigEndian.Uint16(data[7:9]),
	}, nil
}

// EncodeCommandResult encodes a command result into wire format.
// Format: [success(1)][return_code(1)][output_len(2)][output...]
func EncodeCommandResult(result *CommandResultPayload) []byte {
	outputLen := len(result.Output)
	buf := make([]byte, 4+outputLen)

	if result.Success {
		buf[0] = 0x00
	} else {
		buf[0] = 0x01
	}
	buf[1] = result.ReturnCode
	binary.BigEndian.PutUint16(buf[2:4], uint16(outputLen))
	copy(buf[4:], result.Output)

	return buf
}

// DecodeCommandResult decodes wire format data into a command result.
func DecodeCommandResult(data []byte) (*CommandResultPayload, error) {
	if len(data) < 2 {
		return nil, errors.New("command result payload too short")
	}

	result := &CommandResultPayload{
		Success:    data[0] == 0x00,
		ReturnCode: data[1],
	}

	if len(data) > 2 {
		result.Output = string(data[2:])
	}

	return result, nil
}

// ParseMetricsResponse parses a metrics response message payload.
func ParseMetricsResponse(payload []byte) ([]Metric, error) {
	return DecodeMetrics(payload)
}

// ParseStatusResponse parses a status response message payload.
func ParseStatusResponse(payload []byte) (*StatusPayload, error) {
	return DecodeStatus(payload)
}

// ParseCommandResult parses a command result message payload.
func ParseCommandResult(payload []byte) (*CommandResultPayload, error) {
	return DecodeCommandResult(payload)
}
