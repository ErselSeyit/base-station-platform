// Package oran provides an O-RAN O1 interface adapter for managing O-RAN compliant
// radio units (O-RU), distributed units (O-DU), and central units (O-CU).
//
// The O1 interface is based on NETCONF/YANG for configuration management and
// VES (VNF Event Streaming) for fault, performance, and file management.
package oran

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	"strings"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/knownhosts"

	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// ORANAdapter collects metrics and manages O-RAN compliant equipment via O1 interface.
type ORANAdapter struct {
	config      Config
	sshClient   *ssh.Client
	httpClient  *http.Client
	pathMap     []types.MetricMapping
	mu          sync.RWMutex
	connected   bool
	vesListener *VESListener
}

// Config holds O-RAN O1 adapter configuration.
type Config struct {
	types.Config `yaml:",inline"`

	// NETCONF settings (for CM - Configuration Management)
	Username        string `yaml:"username"`
	Password        string `yaml:"password"`
	PrivateKey      string `yaml:"private_key"`
	HostKeyCallback string `yaml:"host_key_callback"` // "ignore", "known_hosts", or "known_hosts:/path"

	// VES settings (for FM/PM - Fault/Performance Management)
	VESEnabled  bool   `yaml:"ves_enabled"`
	VESEndpoint string `yaml:"ves_endpoint"` // Local endpoint to receive VES events
	VESPort     int    `yaml:"ves_port"`

	// Component type determines YANG models to use
	// Options: "o-ru", "o-du", "o-cu", "smo"
	ComponentType string `yaml:"component_type"`

	// O-RAN WG4 fronthaul plane settings (for O-RU)
	FronthaulEnabled bool   `yaml:"fronthaul_enabled"`
	CUSPlaneIP       string `yaml:"cu_s_plane_ip"`
	CUPlanePort      int    `yaml:"cu_plane_port"`

	// Custom YANG paths
	CustomPaths []PathMapping `yaml:"custom_paths"`
}

// PathMapping maps a YANG path to a metric type.
type PathMapping struct {
	XPath       string  `yaml:"xpath"`
	MetricType  string  `yaml:"metric_type"`
	Scale       float32 `yaml:"scale"`
	Offset      float32 `yaml:"offset"`
	Description string  `yaml:"description"`
}

// VESListener handles incoming VES events.
type VESListener struct {
	server   *http.Server
	events   chan VESEvent
	running  bool
	mu       sync.Mutex
}

// VESEvent represents a VES (VNF Event Streaming) event.
type VESEvent struct {
	EventType string                 `json:"event_type"`
	Timestamp time.Time              `json:"timestamp"`
	SourceID  string                 `json:"source_id"`
	Domain    string                 `json:"domain"` // fault, measurement, heartbeat, etc.
	Data      map[string]interface{} `json:"data"`
}

// DefaultConfig returns default O-RAN O1 configuration.
func DefaultConfig() Config {
	return Config{
		Config:        types.DefaultConfig(),
		VESEnabled:    true,
		VESPort:       30001,
		ComponentType: "o-ru",
	}
}

// New creates a new O-RAN O1 adapter.
func New(cfg Config) (*ORANAdapter, error) {
	if cfg.Host == "" {
		return nil, fmt.Errorf("O-RAN host is required")
	}
	if cfg.Port == 0 {
		cfg.Port = 830 // NETCONF default
	}
	if cfg.Timeout == 0 {
		cfg.Timeout = 10 * time.Second
	}
	if cfg.Username == "" {
		return nil, fmt.Errorf("O-RAN username is required")
	}

	// Build YANG path mappings based on component type
	pathMap := buildPathMappings(cfg.ComponentType, cfg.CustomPaths)

	adapter := &ORANAdapter{
		config:  cfg,
		pathMap: pathMap,
		httpClient: &http.Client{
			Timeout: cfg.Timeout,
		},
	}

	return adapter, nil
}

// Name returns the adapter name.
func (o *ORANAdapter) Name() string {
	return fmt.Sprintf("oran-%s-%s:%d", o.config.ComponentType, o.config.Host, o.config.Port)
}

// Connect establishes connections for O1 interface.
func (o *ORANAdapter) Connect(ctx context.Context) error {
	o.mu.Lock()
	defer o.mu.Unlock()

	if o.connected {
		return nil
	}

	// Build host key callback based on configuration
	hostKeyCallback, err := o.buildHostKeyCallback()
	if err != nil {
		return fmt.Errorf("failed to configure host key verification: %w", err)
	}

	// Connect NETCONF (for configuration management)
	sshConfig := &ssh.ClientConfig{
		User:            o.config.Username,
		Timeout:         o.config.Timeout,
		HostKeyCallback: hostKeyCallback,
	}

	if o.config.Password != "" {
		sshConfig.Auth = append(sshConfig.Auth, ssh.Password(o.config.Password))
	}

	// Add private key authentication if configured
	if o.config.PrivateKey != "" {
		signer, err := o.loadPrivateKey()
		if err != nil {
			return fmt.Errorf("failed to load private key: %w", err)
		}
		sshConfig.Auth = append(sshConfig.Auth, ssh.PublicKeys(signer))
	}

	addr := fmt.Sprintf("%s:%d", o.config.Host, o.config.Port)
	client, err := ssh.Dial("tcp", addr, sshConfig)
	if err != nil {
		return fmt.Errorf("O-RAN NETCONF connection failed: %w", err)
	}
	o.sshClient = client

	// Start VES listener if enabled
	if o.config.VESEnabled {
		listener, err := o.startVESListener()
		if err != nil {
			log.Printf("[O-RAN] Warning: VES listener failed to start: %v", err)
		} else {
			o.vesListener = listener
		}
	}

	o.connected = true
	log.Printf("[O-RAN] Connected to %s (%s)", o.config.Host, o.config.ComponentType)
	return nil
}

// Close closes all O-RAN connections.
func (o *ORANAdapter) Close() error {
	o.mu.Lock()
	defer o.mu.Unlock()

	if !o.connected {
		return nil
	}

	// Stop VES listener
	if o.vesListener != nil {
		o.vesListener.Stop()
	}

	// Close SSH/NETCONF connection
	if o.sshClient != nil {
		o.sshClient.Close()
	}

	o.connected = false
	log.Printf("[O-RAN] Disconnected from %s", o.config.Host)
	return nil
}

// buildHostKeyCallback creates an SSH host key callback based on configuration.
// Supports three modes:
//   - "ignore": Disables host key verification (development only, logs warning)
//   - "known_hosts": Uses system known_hosts file (~/.ssh/known_hosts)
//   - "known_hosts:/path/to/file": Uses specified known_hosts file
func (o *ORANAdapter) buildHostKeyCallback() (ssh.HostKeyCallback, error) {
	mode := o.config.HostKeyCallback
	if mode == "" {
		mode = "ignore"
	}

	switch {
	case mode == "ignore":
		log.Printf("[O-RAN] WARNING: SSH host key verification disabled - NOT for production use")
		return ssh.InsecureIgnoreHostKey(), nil

	case mode == "known_hosts":
		knownHostsPath := os.ExpandEnv("$HOME/.ssh/known_hosts")
		callback, err := knownhosts.New(knownHostsPath)
		if err != nil {
			return nil, fmt.Errorf("failed to load known_hosts from %s: %w", knownHostsPath, err)
		}
		log.Printf("[O-RAN] Using known_hosts verification from %s", knownHostsPath)
		return callback, nil

	case strings.HasPrefix(mode, "known_hosts:"):
		knownHostsPath := strings.TrimPrefix(mode, "known_hosts:")
		callback, err := knownhosts.New(knownHostsPath)
		if err != nil {
			return nil, fmt.Errorf("failed to load known_hosts from %s: %w", knownHostsPath, err)
		}
		log.Printf("[O-RAN] Using known_hosts verification from %s", knownHostsPath)
		return callback, nil

	default:
		log.Printf("[O-RAN] WARNING: Unknown host_key_callback mode '%s', falling back to ignore", mode)
		return ssh.InsecureIgnoreHostKey(), nil
	}
}

// loadPrivateKey loads an SSH private key from the configured path.
func (o *ORANAdapter) loadPrivateKey() (ssh.Signer, error) {
	keyData, err := os.ReadFile(o.config.PrivateKey)
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
func (o *ORANAdapter) IsConnected() bool {
	o.mu.RLock()
	defer o.mu.RUnlock()
	return o.connected
}

// CollectMetrics collects metrics via NETCONF and VES.
func (o *ORANAdapter) CollectMetrics(ctx context.Context) ([]protocol.Metric, error) {
	o.mu.RLock()
	if !o.connected {
		o.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	o.mu.RUnlock()

	var metrics []protocol.Metric

	// Collect from NETCONF (configuration/state data)
	netconfMetrics, err := o.collectNETCONFMetrics(ctx)
	if err != nil {
		log.Printf("[O-RAN] NETCONF collection error: %v", err)
	} else {
		metrics = append(metrics, netconfMetrics...)
	}

	// Collect from VES events (if listener is running)
	if o.vesListener != nil {
		vesMetrics := o.collectVESMetrics()
		metrics = append(metrics, vesMetrics...)
	}

	return metrics, nil
}

// CollectMetric collects a specific metric type.
func (o *ORANAdapter) CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error) {
	// Find the path mapping for this metric type
	var mapping *types.MetricMapping
	for i := range o.pathMap {
		if o.pathMap[i].MetricType == metricType {
			mapping = &o.pathMap[i]
			break
		}
	}

	if mapping == nil {
		return nil, fmt.Errorf("no path mapping for metric type %d", metricType)
	}

	o.mu.RLock()
	if !o.connected {
		o.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	o.mu.RUnlock()

	// Query via NETCONF
	value, err := o.queryNETCONF(mapping.ExternalID)
	if err != nil {
		return nil, err
	}

	transformedValue := mapping.ApplyTransform(value)

	return &protocol.Metric{
		Type:  metricType,
		Value: transformedValue,
	}, nil
}

// collectNETCONFMetrics queries NETCONF for all mapped metrics.
func (o *ORANAdapter) collectNETCONFMetrics(_ context.Context) ([]protocol.Metric, error) {
	var metrics []protocol.Metric

	for _, mapping := range o.pathMap {
		value, err := o.queryNETCONF(mapping.ExternalID)
		if err != nil {
			continue
		}

		transformedValue := mapping.ApplyTransform(value)
		metrics = append(metrics, protocol.Metric{
			Type:  mapping.MetricType,
			Value: transformedValue,
		})
	}

	return metrics, nil
}

// queryNETCONF sends a NETCONF get request for the given path.
func (o *ORANAdapter) queryNETCONF(xpath string) (float32, error) {
	// Simplified NETCONF query implementation
	// In production, this would use proper NETCONF RPC over the SSH session

	// For now, return a placeholder indicating the query was attempted
	// Real implementation would parse XML response
	return 0, fmt.Errorf("NETCONF query not fully implemented for: %s", xpath)
}

// collectVESMetrics extracts metrics from received VES events.
func (o *ORANAdapter) collectVESMetrics() []protocol.Metric {
	var metrics []protocol.Metric

	// Drain available events from the channel (non-blocking)
	for {
		select {
		case event := <-o.vesListener.events:
			extracted := o.extractMetricsFromVES(event)
			metrics = append(metrics, extracted...)
		default:
			return metrics
		}
	}
}

// extractMetricsFromVES converts a VES event to metrics.
func (o *ORANAdapter) extractMetricsFromVES(event VESEvent) []protocol.Metric {
	var metrics []protocol.Metric

	switch event.Domain {
	case "measurement":
		// Extract measurement values
		if measurements, ok := event.Data["measurementFields"].(map[string]interface{}); ok {
			for key, value := range measurements {
				metric := o.mapVESMeasurement(key, value)
				if metric != nil {
					metrics = append(metrics, *metric)
				}
			}
		}
	case "fault":
		// Could convert alarm severity to a metric
	case "heartbeat":
		// Heartbeat indicates component is alive
	}

	return metrics
}

// mapVESMeasurement maps a VES measurement to a protocol metric.
func (o *ORANAdapter) mapVESMeasurement(key string, value interface{}) *protocol.Metric {
	// Map common O-RAN measurements to metric types
	metricMap := map[string]protocol.MetricType{
		"cpuUsage":         protocol.MetricCPUUsage,
		"memoryUsage":      protocol.MetricMemoryUsage,
		"temperature":      protocol.MetricTemperature,
		"txPower":          protocol.MetricTxPower,
		"rxPower":          protocol.MetricRxPower,
		"vswr":             protocol.MetricVSWR,
		"prbUtilization":   protocol.MetricPRBUsage,
		"activeUeCount":    protocol.MetricActiveUsers,
	}

	metricType, ok := metricMap[key]
	if !ok {
		return nil
	}

	var floatValue float32
	switch v := value.(type) {
	case float64:
		floatValue = float32(v)
	case float32:
		floatValue = v
	case int:
		floatValue = float32(v)
	case int64:
		floatValue = float32(v)
	default:
		return nil
	}

	return &protocol.Metric{
		Type:  metricType,
		Value: floatValue,
	}
}

// startVESListener starts the HTTP server to receive VES events.
func (o *ORANAdapter) startVESListener() (*VESListener, error) {
	listener := &VESListener{
		events: make(chan VESEvent, 1000),
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/eventListener/v7", listener.handleVESEvent)
	mux.HandleFunc("/eventListener/v7/events", listener.handleVESEvent)

	listener.server = &http.Server{
		Addr:    fmt.Sprintf(":%d", o.config.VESPort),
		Handler: mux,
	}

	go func() {
		listener.mu.Lock()
		listener.running = true
		listener.mu.Unlock()

		log.Printf("[O-RAN] VES listener starting on port %d", o.config.VESPort)
		if err := listener.server.ListenAndServe(); err != http.ErrServerClosed {
			log.Printf("[O-RAN] VES listener error: %v", err)
		}
	}()

	return listener, nil
}

// handleVESEvent processes incoming VES events.
func (l *VESListener) handleVESEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read body", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	// Parse VES event batch
	var eventBatch struct {
		EventList []struct {
			CommonEventHeader struct {
				Domain    string `json:"domain"`
				SourceID  string `json:"sourceId"`
				EventType string `json:"eventType"`
			} `json:"commonEventHeader"`
			MeasurementFields map[string]interface{} `json:"measurementFields,omitempty"`
			FaultFields       map[string]interface{} `json:"faultFields,omitempty"`
		} `json:"eventList"`
	}

	if err := json.Unmarshal(body, &eventBatch); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	// Convert to internal events and queue
	for _, e := range eventBatch.EventList {
		event := VESEvent{
			EventType: e.CommonEventHeader.EventType,
			Timestamp: time.Now(),
			SourceID:  e.CommonEventHeader.SourceID,
			Domain:    e.CommonEventHeader.Domain,
			Data:      make(map[string]interface{}),
		}

		if e.MeasurementFields != nil {
			event.Data["measurementFields"] = e.MeasurementFields
		}
		if e.FaultFields != nil {
			event.Data["faultFields"] = e.FaultFields
		}

		select {
		case l.events <- event:
		default:
			// Channel full, drop oldest
			<-l.events
			l.events <- event
		}
	}

	w.WriteHeader(http.StatusAccepted)
}

// Stop stops the VES listener.
func (l *VESListener) Stop() {
	l.mu.Lock()
	defer l.mu.Unlock()

	if !l.running {
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	l.server.Shutdown(ctx)
	l.running = false
	close(l.events)
}
