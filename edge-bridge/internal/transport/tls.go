package transport

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"net"
	"os"
	"time"
)

// TLSConfig holds TLS-specific configuration.
type TLSConfig struct {
	// Enabled controls whether TLS is used.
	Enabled bool

	// CertFile is the path to the client certificate file (PEM).
	CertFile string

	// KeyFile is the path to the client private key file (PEM).
	KeyFile string

	// CAFile is the path to the CA certificate file (PEM) for server verification.
	CAFile string

	// ServerName is the expected server name for verification.
	ServerName string

	// InsecureSkipVerify disables server certificate verification (for testing only).
	InsecureSkipVerify bool

	// MinVersion is the minimum TLS version (default: TLS 1.2).
	MinVersion uint16

	// MaxVersion is the maximum TLS version (default: TLS 1.3).
	MaxVersion uint16
}

// DefaultTLSConfig returns a secure default TLS configuration.
func DefaultTLSConfig() *TLSConfig {
	return &TLSConfig{
		Enabled:            false,
		InsecureSkipVerify: false,
		MinVersion:         tls.VersionTLS12,
		MaxVersion:         tls.VersionTLS13,
	}
}

// TLSTransport implements Transport over TLS-encrypted TCP.
type TLSTransport struct {
	host        string
	port        int
	config      *Config
	tlsConfig   *TLSConfig
	conn        net.Conn
	readTimeout time.Duration
}

// NewTLSTransport creates a new TLS transport.
func NewTLSTransport(host string, port int, tlsCfg *TLSConfig) (*TLSTransport, error) {
	if tlsCfg == nil {
		tlsCfg = DefaultTLSConfig()
	}

	return &TLSTransport{
		host:        host,
		port:        port,
		config:      DefaultConfig(),
		tlsConfig:   tlsCfg,
		readTimeout: 100 * time.Millisecond,
	}, nil
}

// buildTLSConfig creates the crypto/tls configuration from our config.
func (tt *TLSTransport) buildTLSConfig() (*tls.Config, error) {
	tlsCfg := &tls.Config{
		MinVersion:         tt.tlsConfig.MinVersion,
		MaxVersion:         tt.tlsConfig.MaxVersion,
		InsecureSkipVerify: tt.tlsConfig.InsecureSkipVerify,
	}

	// Set server name for verification
	if tt.tlsConfig.ServerName != "" {
		tlsCfg.ServerName = tt.tlsConfig.ServerName
	} else {
		tlsCfg.ServerName = tt.host
	}

	// Load client certificate if provided (mutual TLS)
	if tt.tlsConfig.CertFile != "" && tt.tlsConfig.KeyFile != "" {
		cert, err := tls.LoadX509KeyPair(tt.tlsConfig.CertFile, tt.tlsConfig.KeyFile)
		if err != nil {
			return nil, fmt.Errorf("failed to load client certificate: %w", err)
		}
		tlsCfg.Certificates = []tls.Certificate{cert}
	}

	// Load CA certificate for server verification
	if tt.tlsConfig.CAFile != "" {
		caCert, err := os.ReadFile(tt.tlsConfig.CAFile)
		if err != nil {
			return nil, fmt.Errorf("failed to read CA certificate: %w", err)
		}

		caCertPool := x509.NewCertPool()
		if !caCertPool.AppendCertsFromPEM(caCert) {
			return nil, fmt.Errorf("failed to parse CA certificate")
		}
		tlsCfg.RootCAs = caCertPool
	}

	return tlsCfg, nil
}

// Open establishes the TLS connection.
func (tt *TLSTransport) Open() error {
	if tt.conn != nil {
		return nil // Already open
	}

	tlsCfg, err := tt.buildTLSConfig()
	if err != nil {
		return fmt.Errorf("failed to build TLS config: %w", err)
	}

	addr := net.JoinHostPort(tt.host, fmt.Sprintf("%d", tt.port))

	// Create a dialer with timeout
	dialer := &net.Dialer{
		Timeout: tt.config.WriteTimeout,
	}

	conn, err := tls.DialWithDialer(dialer, "tcp", addr, tlsCfg)
	if err != nil {
		return fmt.Errorf("failed to establish TLS connection to %s: %w", addr, err)
	}

	// Verify the connection state
	state := conn.ConnectionState()
	if !state.HandshakeComplete {
		conn.Close()
		return fmt.Errorf("TLS handshake incomplete")
	}

	tt.conn = conn
	return nil
}

// Close terminates the TLS connection.
func (tt *TLSTransport) Close() error {
	if tt.conn == nil {
		return nil
	}

	err := tt.conn.Close()
	tt.conn = nil
	return err
}

// IsOpen returns true if the connection is open.
func (tt *TLSTransport) IsOpen() bool {
	return tt.conn != nil
}

// Send sends data over TLS.
func (tt *TLSTransport) Send(data []byte) (int, error) {
	if tt.conn == nil {
		return 0, ErrNotConnected
	}

	if tt.config.WriteTimeout > 0 {
		tt.conn.SetWriteDeadline(time.Now().Add(tt.config.WriteTimeout))
	}

	return tt.conn.Write(data)
}

// Receive reads data from TLS with timeout.
func (tt *TLSTransport) Receive(buffer []byte, timeout time.Duration) (int, error) {
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
func (tt *TLSTransport) SetReadTimeout(timeout time.Duration) {
	tt.readTimeout = timeout
}

// Type returns the transport type identifier.
func (tt *TLSTransport) Type() string {
	return "tls"
}

// Host returns the TLS host.
func (tt *TLSTransport) Host() string {
	return tt.host
}

// Port returns the TLS port.
func (tt *TLSTransport) Port() int {
	return tt.port
}

// Address returns the full address string.
func (tt *TLSTransport) Address() string {
	return net.JoinHostPort(tt.host, fmt.Sprintf("%d", tt.port))
}

// LocalAddr returns the local address if connected.
func (tt *TLSTransport) LocalAddr() net.Addr {
	if tt.conn == nil {
		return nil
	}
	return tt.conn.LocalAddr()
}

// RemoteAddr returns the remote address if connected.
func (tt *TLSTransport) RemoteAddr() net.Addr {
	if tt.conn == nil {
		return nil
	}
	return tt.conn.RemoteAddr()
}

// ConnectionState returns the TLS connection state if connected.
func (tt *TLSTransport) ConnectionState() *tls.ConnectionState {
	if tt.conn == nil {
		return nil
	}
	if tlsConn, ok := tt.conn.(*tls.Conn); ok {
		state := tlsConn.ConnectionState()
		return &state
	}
	return nil
}

// TLSVersion returns a human-readable TLS version string.
func TLSVersion(version uint16) string {
	switch version {
	case tls.VersionTLS10:
		return "TLS 1.0"
	case tls.VersionTLS11:
		return "TLS 1.1"
	case tls.VersionTLS12:
		return "TLS 1.2"
	case tls.VersionTLS13:
		return "TLS 1.3"
	default:
		return fmt.Sprintf("Unknown (0x%04x)", version)
	}
}
