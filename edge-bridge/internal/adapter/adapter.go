// Package adapter provides protocol adapters for collecting metrics from various sources.
package adapter

import (
	"context"
	"time"

	"edge-bridge/internal/protocol"
)

// Adapter defines the interface for protocol adapters.
type Adapter interface {
	// Name returns the adapter name for logging/identification.
	Name() string

	// Connect establishes connection to the target device.
	Connect(ctx context.Context) error

	// Close closes the connection.
	Close() error

	// IsConnected returns true if the adapter is connected.
	IsConnected() bool

	// CollectMetrics collects all available metrics from the device.
	CollectMetrics(ctx context.Context) ([]protocol.Metric, error)

	// CollectMetric collects a specific metric type.
	CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error)
}

// Config holds common configuration for all adapters.
type Config struct {
	// Enabled indicates if the adapter is enabled.
	Enabled bool `yaml:"enabled"`

	// Host is the target device hostname or IP.
	Host string `yaml:"host"`

	// Port is the target device port.
	Port int `yaml:"port"`

	// Timeout is the connection/request timeout.
	Timeout time.Duration `yaml:"timeout"`

	// PollInterval is how often to collect metrics.
	PollInterval time.Duration `yaml:"poll_interval"`

	// RetryAttempts is the number of retry attempts on failure.
	RetryAttempts int `yaml:"retry_attempts"`

	// RetryDelay is the delay between retries.
	RetryDelay time.Duration `yaml:"retry_delay"`
}

// DefaultConfig returns default adapter configuration.
func DefaultConfig() Config {
	return Config{
		Enabled:       false,
		Timeout:       5 * time.Second,
		PollInterval:  30 * time.Second,
		RetryAttempts: 3,
		RetryDelay:    1 * time.Second,
	}
}

// MetricMapping maps an external identifier to a metric type.
type MetricMapping struct {
	// ExternalID is the external identifier (OID, topic, register address, etc.)
	ExternalID string

	// MetricType is the internal metric type.
	MetricType protocol.MetricType

	// Scale is the multiplier to apply to the raw value.
	Scale float32

	// Offset is added after scaling.
	Offset float32

	// Description is a human-readable description.
	Description string
}

// ApplyTransform applies scale and offset to a raw value.
func (m *MetricMapping) ApplyTransform(rawValue float32) float32 {
	return rawValue*m.Scale + m.Offset
}
