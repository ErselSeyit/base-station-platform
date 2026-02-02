// Package types provides common types for protocol adapters.
package types

import (
	"context"
	"time"

	"edge-bridge/internal/protocol"
)

// Adapter is the common interface for all protocol adapters.
type Adapter interface {
	// Name returns a unique identifier for this adapter.
	Name() string

	// Connect establishes the connection to the data source.
	Connect(ctx context.Context) error

	// Close closes the connection.
	Close() error

	// IsConnected returns true if the adapter is connected.
	IsConnected() bool

	// CollectMetrics collects all available metrics from the source.
	CollectMetrics(ctx context.Context) ([]protocol.Metric, error)

	// CollectMetric collects a specific metric type.
	CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error)
}

// Config holds common adapter configuration.
type Config struct {
	// Name is a unique identifier for this adapter instance.
	Name string `yaml:"name"`

	// Enabled controls whether this adapter is active.
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

	// Mappings defines how external IDs map to metric types.
	Mappings []MetricMapping `yaml:"mappings"`
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

// MetricMapping maps an external identifier to a protocol metric type.
type MetricMapping struct {
	// ExternalID is the external identifier (OID, register address, topic, etc.)
	ExternalID string

	// MetricType is the internal metric type.
	MetricType protocol.MetricType

	// Scale is a multiplier applied to the raw value.
	Scale float32

	// Offset is added after scaling.
	Offset float32

	// Description is optional human-readable description.
	Description string
}

// ApplyTransform applies scale and offset to a raw value.
func (m *MetricMapping) ApplyTransform(raw float32) float32 {
	return raw*m.Scale + m.Offset
}
