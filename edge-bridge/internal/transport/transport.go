// Package transport provides communication abstractions for device connections.
package transport

import (
	"errors"
	"io"
	"time"
)

// Transport errors
var (
	ErrNotConnected = errors.New("transport not connected")
	ErrTimeout      = errors.New("operation timed out")
	ErrClosed       = errors.New("transport closed")
)

// Transport defines the interface for device communication.
type Transport interface {
	// Open establishes the connection.
	Open() error

	// Close terminates the connection.
	Close() error

	// IsOpen returns true if the connection is open.
	IsOpen() bool

	// Send sends data and returns the number of bytes written.
	Send(data []byte) (int, error)

	// Receive reads data with a timeout. Returns bytes read and any error.
	// A timeout of 0 means no timeout (blocking read).
	Receive(buffer []byte, timeout time.Duration) (int, error)

	// SetReadTimeout sets the default read timeout.
	SetReadTimeout(timeout time.Duration)

	// Type returns the transport type identifier.
	Type() string
}

// Config holds common transport configuration.
type Config struct {
	ReadTimeout  time.Duration
	WriteTimeout time.Duration
	RetryCount   int
	RetryDelay   time.Duration
}

// DefaultConfig returns a default transport configuration.
func DefaultConfig() *Config {
	return &Config{
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 5 * time.Second,
		RetryCount:   3,
		RetryDelay:   100 * time.Millisecond,
	}
}

// BufferedTransport wraps a Transport with buffered reading.
type BufferedTransport struct {
	transport Transport
	buffer    []byte
	bufStart  int
	bufEnd    int
}

// NewBufferedTransport creates a buffered transport wrapper.
func NewBufferedTransport(t Transport, bufSize int) *BufferedTransport {
	return &BufferedTransport{
		transport: t,
		buffer:    make([]byte, bufSize),
	}
}

// ReadByte reads a single byte from the transport.
func (bt *BufferedTransport) ReadByte(timeout time.Duration) (byte, error) {
	if bt.bufStart < bt.bufEnd {
		b := bt.buffer[bt.bufStart]
		bt.bufStart++
		return b, nil
	}

	// Buffer empty, read more
	n, err := bt.transport.Receive(bt.buffer, timeout)
	if err != nil {
		return 0, err
	}
	if n == 0 {
		return 0, io.EOF
	}

	bt.bufStart = 1
	bt.bufEnd = n
	return bt.buffer[0], nil
}

// ReadBytes reads multiple bytes from the transport.
func (bt *BufferedTransport) ReadBytes(count int, timeout time.Duration) ([]byte, error) {
	result := make([]byte, 0, count)

	deadline := time.Now().Add(timeout)
	for len(result) < count {
		remaining := time.Until(deadline)
		if remaining <= 0 {
			if len(result) > 0 {
				return result, ErrTimeout
			}
			return nil, ErrTimeout
		}

		b, err := bt.ReadByte(remaining)
		if err != nil {
			if len(result) > 0 {
				return result, err
			}
			return nil, err
		}
		result = append(result, b)
	}

	return result, nil
}

// Underlying returns the underlying transport.
func (bt *BufferedTransport) Underlying() Transport {
	return bt.transport
}
