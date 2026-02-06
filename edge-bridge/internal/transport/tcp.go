package transport

import (
	"fmt"
	"net"
	"time"
)

// TCPTransport implements Transport over TCP.
type TCPTransport struct {
	host        string
	port        int
	config      *Config
	conn        net.Conn
	readTimeout time.Duration
}

// NewTCPTransport creates a new TCP transport.
func NewTCPTransport(host string, port int) *TCPTransport {
	return &TCPTransport{
		host:        host,
		port:        port,
		config:      DefaultConfig(),
		readTimeout: 100 * time.Millisecond,
	}
}

// Open establishes the TCP connection.
func (tt *TCPTransport) Open() error {
	if tt.conn != nil {
		return nil // Already open
	}

	addr := net.JoinHostPort(tt.host, fmt.Sprintf("%d", tt.port))
	conn, err := net.DialTimeout("tcp", addr, tt.config.WriteTimeout)
	if err != nil {
		return fmt.Errorf("failed to connect to %s: %w", addr, err)
	}

	tt.conn = conn
	return nil
}

// Close terminates the TCP connection.
func (tt *TCPTransport) Close() error {
	if tt.conn == nil {
		return nil
	}

	err := tt.conn.Close()
	tt.conn = nil
	return err
}

// IsOpen returns true if the connection is open.
func (tt *TCPTransport) IsOpen() bool {
	return tt.conn != nil
}

// Send sends data over TCP.
func (tt *TCPTransport) Send(data []byte) (int, error) {
	if tt.conn == nil {
		return 0, ErrNotConnected
	}

	if tt.config.WriteTimeout > 0 {
		tt.conn.SetWriteDeadline(time.Now().Add(tt.config.WriteTimeout))
	}

	return tt.conn.Write(data)
}

// Receive reads data from TCP with timeout.
func (tt *TCPTransport) Receive(buffer []byte, timeout time.Duration) (int, error) {
	if tt.conn == nil {
		return 0, ErrNotConnected
	}

	if timeout > 0 {
		tt.conn.SetReadDeadline(time.Now().Add(timeout))
	} else {
		tt.conn.SetReadDeadline(time.Now().Add(tt.readTimeout))
	}

	n, err := tt.conn.Read(buffer)
	if err != nil {
		if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
			return n, nil // Timeout is not an error for us
		}
		return n, err
	}
	return n, nil
}

// SetReadTimeout sets the default read timeout.
func (tt *TCPTransport) SetReadTimeout(timeout time.Duration) {
	tt.readTimeout = timeout
}

// Type returns the transport type identifier.
func (tt *TCPTransport) Type() string {
	return "tcp"
}

// Host returns the TCP host.
func (tt *TCPTransport) Host() string {
	return tt.host
}

// Port returns the TCP port.
func (tt *TCPTransport) Port() int {
	return tt.port
}

// Address returns the full address string.
func (tt *TCPTransport) Address() string {
	return net.JoinHostPort(tt.host, fmt.Sprintf("%d", tt.port))
}

// LocalAddr returns the local address if connected.
func (tt *TCPTransport) LocalAddr() net.Addr {
	if tt.conn == nil {
		return nil
	}
	return tt.conn.LocalAddr()
}

// RemoteAddr returns the remote address if connected.
func (tt *TCPTransport) RemoteAddr() net.Addr {
	if tt.conn == nil {
		return nil
	}
	return tt.conn.RemoteAddr()
}
