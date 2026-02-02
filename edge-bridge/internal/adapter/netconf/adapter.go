// Package netconf provides a NETCONF adapter for collecting metrics and configuration
// from network devices using the NETCONF protocol over SSH.
package netconf

import (
	"context"
	"encoding/xml"
	"fmt"
	"io"
	"log"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/knownhosts"

	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// startMessageWriter starts a goroutine that writes messages to the SSH session.
// Logs errors but continues attempting to write subsequent messages.
// Exits when the messages channel is closed or done is signaled.
func startMessageWriter(stdin io.WriteCloser, messages <-chan string, done <-chan struct{}) {
	go func() {
		defer log.Printf("[NETCONF] Writer goroutine exiting")
		for {
			select {
			case <-done:
				return
			case msg, ok := <-messages:
				if !ok {
					return // Channel closed
				}
				if _, err := stdin.Write([]byte(msg)); err != nil {
					log.Printf("[NETCONF] Write error: %v", err)
					continue
				}
				if _, err := stdin.Write([]byte(netconfDelimiter)); err != nil {
					log.Printf("[NETCONF] Delimiter write error: %v", err)
				}
			}
		}
	}()
}

// startMessageReader starts a goroutine that reads and parses NETCONF messages.
// Exits when stdout returns an error (connection closed) or done is signaled.
func startMessageReader(stdout io.Reader, messages chan<- string, done <-chan struct{}, closeOnce *sync.Once) {
	go func() {
		defer log.Printf("[NETCONF] Reader goroutine exiting")
		defer closeOnce.Do(func() { close(messages) })

		buf := make([]byte, 65536)
		var messageBuilder strings.Builder
		for {
			select {
			case <-done:
				return
			default:
				n, err := stdout.Read(buf)
				if err != nil {
					return
				}
				messageBuilder.Write(buf[:n])
				processDelimitedMessages(&messageBuilder, messages)
			}
		}
	}()
}

// processDelimitedMessages extracts complete NETCONF messages from the buffer.
func processDelimitedMessages(builder *strings.Builder, messages chan<- string) {
	content := builder.String()
	if !strings.Contains(content, netconfDelimiter) {
		return
	}

	parts := strings.Split(content, netconfDelimiter)
	for _, part := range parts[:len(parts)-1] {
		if trimmed := strings.TrimSpace(part); trimmed != "" {
			messages <- part
		}
	}
	builder.Reset()
	builder.WriteString(parts[len(parts)-1])
}

// NETCONFAdapter collects metrics via NETCONF over SSH.
type NETCONFAdapter struct {
	config    Config
	client    *ssh.Client
	session   *Session
	pathMap   []types.MetricMapping
	mu        sync.RWMutex
	connected bool
}

// Session represents a NETCONF session.
type Session struct {
	stdin      chan<- string
	stdout     <-chan string
	sessionID  string
	msgID      uint64
	done       chan struct{} // Signal goroutines to stop
	closeOnce  sync.Once     // Ensure channels closed only once
	stdinOwned chan string   // Owned channel for closing
}

// Config holds NETCONF-specific configuration.
type Config struct {
	types.Config `yaml:",inline"`

	// Username for SSH authentication.
	Username string `yaml:"username"`

	// Password for SSH authentication (optional if using key).
	Password string `yaml:"password"`

	// PrivateKey is the path to SSH private key (optional).
	PrivateKey string `yaml:"private_key"`

	// HostKeyCallback determines how to verify host keys.
	// Options: "ignore", "known_hosts", or a specific key fingerprint.
	HostKeyCallback string `yaml:"host_key_callback"`

	// Capabilities to advertise during hello exchange.
	Capabilities []string `yaml:"capabilities"`

	// DeviceType determines which YANG path mappings to use.
	// Options: "ericsson", "nokia", "huawei", "generic"
	DeviceType string `yaml:"device_type"`

	// CustomPaths allows specifying additional YANG path mappings.
	CustomPaths []PathMapping `yaml:"custom_paths"`

	// DatastoreSource specifies which datastore to query.
	// Options: "running", "candidate", "startup"
	DatastoreSource string `yaml:"datastore_source"`
}

// PathMapping maps a YANG path to a metric type.
type PathMapping struct {
	XPath       string  `yaml:"xpath"`
	MetricType  string  `yaml:"metric_type"`
	Scale       float32 `yaml:"scale"`
	Offset      float32 `yaml:"offset"`
	Description string  `yaml:"description"`
}

// DefaultConfig returns default NETCONF configuration.
func DefaultConfig() Config {
	return Config{
		Config:          types.DefaultConfig(),
		HostKeyCallback: "ignore",
		DatastoreSource: "running",
		Capabilities: []string{
			"urn:ietf:params:netconf:base:1.0",
			"urn:ietf:params:netconf:base:1.1",
			"urn:ietf:params:netconf:capability:writable-running:1.0",
		},
	}
}

// New creates a new NETCONF adapter.
func New(cfg Config) (*NETCONFAdapter, error) {
	if cfg.Host == "" {
		return nil, fmt.Errorf("NETCONF host is required")
	}
	if cfg.Port == 0 {
		cfg.Port = 830 // Default NETCONF port
	}
	if cfg.Timeout == 0 {
		cfg.Timeout = 10 * time.Second
	}
	if cfg.Username == "" {
		return nil, fmt.Errorf("NETCONF username is required")
	}
	if cfg.DatastoreSource == "" {
		cfg.DatastoreSource = "running"
	}

	// Build path mappings
	pathMap := buildPathMappings(cfg.DeviceType, cfg.CustomPaths)

	return &NETCONFAdapter{
		config:  cfg,
		pathMap: pathMap,
	}, nil
}

// Name returns the adapter name.
func (n *NETCONFAdapter) Name() string {
	return fmt.Sprintf("netconf-%s:%d", n.config.Host, n.config.Port)
}

// Connect establishes the NETCONF session over SSH.
func (n *NETCONFAdapter) Connect(ctx context.Context) error {
	n.mu.Lock()
	defer n.mu.Unlock()

	if n.connected {
		return nil
	}

	// Configure SSH client
	hostKeyCallback, err := n.buildHostKeyCallback()
	if err != nil {
		return fmt.Errorf("failed to configure host key verification: %w", err)
	}

	sshConfig := &ssh.ClientConfig{
		User:            n.config.Username,
		Timeout:         n.config.Timeout,
		HostKeyCallback: hostKeyCallback,
	}

	// Add authentication methods
	if n.config.Password != "" {
		sshConfig.Auth = append(sshConfig.Auth, ssh.Password(n.config.Password))
	}

	// Add private key authentication if configured
	if n.config.PrivateKey != "" {
		signer, err := n.loadPrivateKey()
		if err != nil {
			return fmt.Errorf("failed to load private key: %w", err)
		}
		sshConfig.Auth = append(sshConfig.Auth, ssh.PublicKeys(signer))
	}

	// Connect to SSH server
	addr := fmt.Sprintf("%s:%d", n.config.Host, n.config.Port)
	client, err := ssh.Dial("tcp", addr, sshConfig)
	if err != nil {
		return fmt.Errorf("SSH connection failed: %w", err)
	}
	n.client = client

	// Open NETCONF subsystem
	session, err := n.openSession()
	if err != nil {
		client.Close()
		return fmt.Errorf("NETCONF session failed: %w", err)
	}
	n.session = session

	n.connected = true
	log.Printf("[NETCONF] Connected to %s:%d (session ID: %s)", n.config.Host, n.config.Port, session.sessionID)
	return nil
}

// Close closes the NETCONF session and SSH connection.
func (n *NETCONFAdapter) Close() error {
	n.mu.Lock()
	defer n.mu.Unlock()

	if !n.connected {
		return nil
	}

	// Send close-session RPC
	if n.session != nil {
		_, _ = n.sendRPC("<close-session/>")

		// Signal goroutines to stop
		close(n.session.done)

		// Close the stdin channel to unblock writer goroutine
		if n.session.stdinOwned != nil {
			close(n.session.stdinOwned)
		}
	}

	// Close SSH connection (will cause reader to exit)
	if n.client != nil {
		if err := n.client.Close(); err != nil {
			return err
		}
	}

	n.connected = false
	n.session = nil
	n.client = nil

	log.Printf("[NETCONF] Disconnected from %s:%d", n.config.Host, n.config.Port)
	return nil
}

// buildHostKeyCallback creates an SSH host key callback based on configuration.
// Supports three modes:
//   - "ignore": Disables host key verification (development only, logs warning)
//   - "known_hosts": Uses system known_hosts file (~/.ssh/known_hosts)
//   - "known_hosts:/path/to/file": Uses specified known_hosts file
//   - Any other value: Logs warning and falls back to ignore mode
func (n *NETCONFAdapter) buildHostKeyCallback() (ssh.HostKeyCallback, error) {
	mode := n.config.HostKeyCallback
	if mode == "" {
		mode = "ignore"
	}

	switch {
	case mode == "ignore":
		log.Printf("[NETCONF] WARNING: SSH host key verification disabled - NOT for production use")
		return ssh.InsecureIgnoreHostKey(), nil

	case mode == "known_hosts":
		// Use default known_hosts file
		knownHostsPath := os.ExpandEnv("$HOME/.ssh/known_hosts")
		callback, err := knownhosts.New(knownHostsPath)
		if err != nil {
			return nil, fmt.Errorf("failed to load known_hosts from %s: %w", knownHostsPath, err)
		}
		log.Printf("[NETCONF] Using known_hosts verification from %s", knownHostsPath)
		return callback, nil

	case strings.HasPrefix(mode, "known_hosts:"):
		// Use specified known_hosts file
		knownHostsPath := strings.TrimPrefix(mode, "known_hosts:")
		callback, err := knownhosts.New(knownHostsPath)
		if err != nil {
			return nil, fmt.Errorf("failed to load known_hosts from %s: %w", knownHostsPath, err)
		}
		log.Printf("[NETCONF] Using known_hosts verification from %s", knownHostsPath)
		return callback, nil

	default:
		// Unknown mode - log warning and fall back to ignore
		log.Printf("[NETCONF] WARNING: Unknown host_key_callback mode '%s', falling back to ignore", mode)
		return ssh.InsecureIgnoreHostKey(), nil
	}
}

// loadPrivateKey loads an SSH private key from the configured path.
func (n *NETCONFAdapter) loadPrivateKey() (ssh.Signer, error) {
	keyData, err := os.ReadFile(n.config.PrivateKey)
	if err != nil {
		return nil, fmt.Errorf("failed to read private key file: %w", err)
	}

	signer, err := ssh.ParsePrivateKey(keyData)
	if err != nil {
		return nil, fmt.Errorf("failed to parse private key: %w", err)
	}

	return signer, nil
}

// IsConnected returns true if connected.
func (n *NETCONFAdapter) IsConnected() bool {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.connected
}

// CollectMetrics collects all mapped metrics using NETCONF get operations.
func (n *NETCONFAdapter) CollectMetrics(ctx context.Context) ([]protocol.Metric, error) {
	n.mu.RLock()
	if !n.connected {
		n.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	n.mu.RUnlock()

	var metrics []protocol.Metric

	// Group paths by subtree for efficient queries
	subtrees := n.groupPathsBySubtree()

	for subtree, mappings := range subtrees {
		// Build and send get request
		response, err := n.get(subtree)
		if err != nil {
			log.Printf("[NETCONF] GET failed for %s: %v", subtree, err)
			continue
		}

		// Parse response and extract metrics
		for _, mapping := range mappings {
			value, err := extractValue(response, mapping.ExternalID)
			if err != nil {
				continue
			}

			transformedValue := mapping.ApplyTransform(value)
			metrics = append(metrics, protocol.Metric{
				Type:  mapping.MetricType,
				Value: transformedValue,
			})
		}
	}

	return metrics, nil
}

// CollectMetric collects a specific metric type.
func (n *NETCONFAdapter) CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error) {
	// Find the path mapping for this metric type
	var mapping *types.MetricMapping
	for i := range n.pathMap {
		if n.pathMap[i].MetricType == metricType {
			mapping = &n.pathMap[i]
			break
		}
	}

	if mapping == nil {
		return nil, fmt.Errorf("no path mapping for metric type %d", metricType)
	}

	n.mu.RLock()
	if !n.connected {
		n.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	n.mu.RUnlock()

	// Get the subtree containing this path
	subtree := getSubtreePath(mapping.ExternalID)
	response, err := n.get(subtree)
	if err != nil {
		return nil, fmt.Errorf("NETCONF GET failed: %w", err)
	}

	value, err := extractValue(response, mapping.ExternalID)
	if err != nil {
		return nil, fmt.Errorf("failed to extract value: %w", err)
	}

	transformedValue := mapping.ApplyTransform(value)

	return &protocol.Metric{
		Type:  metricType,
		Value: transformedValue,
	}, nil
}

// openSession opens a NETCONF session and performs hello exchange.
func (n *NETCONFAdapter) openSession() (*Session, error) {
	// Open SSH session
	sshSession, err := n.client.NewSession()
	if err != nil {
		return nil, fmt.Errorf("failed to create SSH session: %w", err)
	}

	// Get stdin/stdout pipes
	stdin, err := sshSession.StdinPipe()
	if err != nil {
		sshSession.Close()
		return nil, err
	}

	stdout, err := sshSession.StdoutPipe()
	if err != nil {
		sshSession.Close()
		return nil, err
	}

	// Request NETCONF subsystem
	if err := sshSession.RequestSubsystem("netconf"); err != nil {
		sshSession.Close()
		return nil, fmt.Errorf("failed to start NETCONF subsystem: %w", err)
	}

	// Create channels for async I/O
	stdinChan := make(chan string, 10)
	stdoutChan := make(chan string, 10)
	done := make(chan struct{})

	// Create session with done channel for graceful shutdown
	session := &Session{
		stdin:      stdinChan,
		stdout:     stdoutChan,
		msgID:      1,
		done:       done,
		stdinOwned: stdinChan,
	}

	// Start I/O goroutines with done channel
	startMessageWriter(stdin, stdinChan, done)
	startMessageReader(stdout, stdoutChan, done, &session.closeOnce)

	// Send hello message
	hello := n.buildHelloMessage()
	stdinChan <- hello

	// Wait for server hello
	select {
	case serverHello := <-stdoutChan:
		sessionID := extractSessionID(serverHello)
		session.sessionID = sessionID
	case <-time.After(n.config.Timeout):
		return nil, fmt.Errorf("timeout waiting for server hello")
	}

	return session, nil
}

// buildHelloMessage creates the NETCONF hello message.
func (n *NETCONFAdapter) buildHelloMessage() string {
	var caps strings.Builder
	for _, cap := range n.config.Capabilities {
		fmt.Fprintf(&caps, "    <capability>%s</capability>\n", cap)
	}

	return fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
  <capabilities>
%s  </capabilities>
</hello>`, caps.String())
}

// sendRPC sends an RPC and returns the response.
func (n *NETCONFAdapter) sendRPC(operation string) (string, error) {
	if n.session == nil {
		return "", fmt.Errorf("no active session")
	}

	msgID := n.session.msgID
	n.session.msgID++

	rpc := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<rpc message-id="%d" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
  %s
</rpc>`, msgID, operation)

	n.session.stdin <- rpc

	// Wait for reply
	select {
	case reply := <-n.session.stdout:
		// Check for rpc-error
		if strings.Contains(reply, "<rpc-error>") {
			return "", fmt.Errorf("RPC error: %s", extractError(reply))
		}
		return reply, nil
	case <-time.After(n.config.Timeout):
		return "", fmt.Errorf("timeout waiting for RPC reply")
	}
}

// get performs a NETCONF get operation with optional filter.
func (n *NETCONFAdapter) get(filter string) (string, error) {
	var operation string
	if filter != "" {
		operation = fmt.Sprintf(`<get>
    <filter type="subtree">
      %s
    </filter>
  </get>`, filter)
	} else {
		operation = "<get/>"
	}

	return n.sendRPC(operation)
}

// getConfig performs a NETCONF get-config operation.
func (n *NETCONFAdapter) getConfig(source string, filter string) (string, error) {
	var filterXML string
	if filter != "" {
		filterXML = fmt.Sprintf(`<filter type="subtree">%s</filter>`, filter)
	}

	operation := fmt.Sprintf(`<get-config>
    <source><%s/></source>
    %s
  </get-config>`, source, filterXML)

	return n.sendRPC(operation)
}

// groupPathsBySubtree groups metric paths by their common subtree prefix.
func (n *NETCONFAdapter) groupPathsBySubtree() map[string][]types.MetricMapping {
	groups := make(map[string][]types.MetricMapping)

	for _, mapping := range n.pathMap {
		subtree := getSubtreePath(mapping.ExternalID)
		groups[subtree] = append(groups[subtree], mapping)
	}

	return groups
}

// Helper functions

// extractSessionID extracts the session-id from a hello message.
func extractSessionID(hello string) string {
	re := regexp.MustCompile(`<session-id>(\d+)</session-id>`)
	matches := re.FindStringSubmatch(hello)
	if len(matches) > 1 {
		return matches[1]
	}
	return "unknown"
}

// extractError extracts error message from rpc-error.
func extractError(reply string) string {
	re := regexp.MustCompile(`<error-message[^>]*>([^<]+)</error-message>`)
	matches := re.FindStringSubmatch(reply)
	if len(matches) > 1 {
		return matches[1]
	}
	return "unknown error"
}

// extractValue extracts a numeric value from an XML response using the given XPath.
func extractValue(xmlData string, xpath string) (float32, error) {
	// Simple extraction - look for the element and get its text content
	// In production, use a proper XML/XPath library
	elementName := getLastElement(xpath)
	re := regexp.MustCompile(fmt.Sprintf(`<%s[^>]*>([^<]+)</%s>`, elementName, elementName))
	matches := re.FindStringSubmatch(xmlData)
	if len(matches) < 2 {
		return 0, fmt.Errorf("element not found: %s", elementName)
	}

	value, err := strconv.ParseFloat(strings.TrimSpace(matches[1]), 32)
	if err != nil {
		return 0, fmt.Errorf("failed to parse value: %w", err)
	}

	return float32(value), nil
}

// getLastElement gets the last element name from an XPath.
func getLastElement(xpath string) string {
	parts := strings.Split(xpath, "/")
	for i := len(parts) - 1; i >= 0; i-- {
		if parts[i] != "" {
			// Remove namespace prefix if present
			name := parts[i]
			if idx := strings.Index(name, ":"); idx >= 0 {
				name = name[idx+1:]
			}
			// Remove predicates
			if idx := strings.Index(name, "["); idx >= 0 {
				name = name[:idx]
			}
			return name
		}
	}
	return ""
}

// getSubtreePath gets the top-level subtree path for filtering.
func getSubtreePath(xpath string) string {
	parts := strings.Split(xpath, "/")
	var nonEmpty []string
	for _, p := range parts {
		if p != "" {
			nonEmpty = append(nonEmpty, p)
		}
	}

	if len(nonEmpty) >= 2 {
		// Return first two levels as subtree
		return "/" + strings.Join(nonEmpty[:2], "/")
	}
	return xpath
}

// RPCReply represents a NETCONF RPC reply.
type RPCReply struct {
	XMLName xml.Name `xml:"rpc-reply"`
	Data    string   `xml:",innerxml"`
}

// netconfDelimiter is the NETCONF 1.0 message delimiter (RFC 6242).
const netconfDelimiter = "]]>]]>"
