package transport

import (
	"fmt"
	"time"

	"github.com/tarm/serial"
)

// SerialTransport implements Transport over a serial port.
type SerialTransport struct {
	port        string
	baudRate    int
	config      *Config
	conn        *serial.Port
	readTimeout time.Duration
}

// SerialConfig holds serial port configuration.
type SerialConfig struct {
	Port     string
	BaudRate int
	DataBits int
	StopBits int
	Parity   string
}

// DefaultSerialConfig returns default serial configuration.
func DefaultSerialConfig() *SerialConfig {
	return &SerialConfig{
		Port:     "/dev/ttyS0",
		BaudRate: 115200,
		DataBits: 8,
		StopBits: 1,
		Parity:   "N",
	}
}

// NewSerialTransport creates a new serial transport.
func NewSerialTransport(port string, baudRate int) *SerialTransport {
	return &SerialTransport{
		port:        port,
		baudRate:    baudRate,
		config:      DefaultConfig(),
		readTimeout: 100 * time.Millisecond,
	}
}

// Open establishes the serial connection.
func (st *SerialTransport) Open() error {
	if st.conn != nil {
		return nil // Already open
	}

	c := &serial.Config{
		Name:        st.port,
		Baud:        st.baudRate,
		ReadTimeout: st.readTimeout,
		Size:        8,
		Parity:      serial.ParityNone,
		StopBits:    serial.Stop1,
	}

	port, err := serial.OpenPort(c)
	if err != nil {
		return fmt.Errorf("failed to open serial port %s: %w", st.port, err)
	}

	st.conn = port
	return nil
}

// Close terminates the serial connection.
func (st *SerialTransport) Close() error {
	if st.conn == nil {
		return nil
	}

	err := st.conn.Close()
	st.conn = nil
	return err
}

// IsOpen returns true if the connection is open.
func (st *SerialTransport) IsOpen() bool {
	return st.conn != nil
}

// Send sends data over the serial port.
func (st *SerialTransport) Send(data []byte) (int, error) {
	if st.conn == nil {
		return 0, ErrNotConnected
	}
	return st.conn.Write(data)
}

// Receive reads data from the serial port with timeout.
func (st *SerialTransport) Receive(buffer []byte, timeout time.Duration) (int, error) {
	if st.conn == nil {
		return 0, ErrNotConnected
	}

	// The serial library handles timeout via ReadTimeout config
	// For different timeouts, we would need to recreate the port
	// For simplicity, we use blocking read with the configured timeout
	n, err := st.conn.Read(buffer)
	if err != nil {
		return n, err
	}
	return n, nil
}

// SetReadTimeout sets the default read timeout.
func (st *SerialTransport) SetReadTimeout(timeout time.Duration) {
	st.readTimeout = timeout
}

// Type returns the transport type identifier.
func (st *SerialTransport) Type() string {
	return "serial"
}

// Port returns the serial port path.
func (st *SerialTransport) Port() string {
	return st.port
}

// BaudRate returns the baud rate.
func (st *SerialTransport) BaudRate() int {
	return st.baudRate
}

// Flush clears any buffered data.
func (st *SerialTransport) Flush() error {
	if st.conn == nil {
		return ErrNotConnected
	}
	return st.conn.Flush()
}
