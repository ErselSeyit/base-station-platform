// Package snmp provides an SNMP adapter for collecting metrics from network devices.
package snmp

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/gosnmp/gosnmp"

	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// SNMPAdapter collects metrics via SNMP v2c or v3.
type SNMPAdapter struct {
	config   Config
	client   *gosnmp.GoSNMP
	oidMap   []types.MetricMapping
	mu       sync.RWMutex
	connected bool
}

// Config holds SNMP-specific configuration.
type Config struct {
	types.Config `yaml:",inline"`

	// Version is the SNMP version: "v2c" or "v3"
	Version string `yaml:"version"`

	// Community is the SNMP v2c community string.
	Community string `yaml:"community"`

	// V3 authentication settings
	Username     string `yaml:"username"`
	AuthProtocol string `yaml:"auth_protocol"` // MD5, SHA, SHA224, SHA256, SHA384, SHA512
	AuthPassword string `yaml:"auth_password"`
	PrivProtocol string `yaml:"priv_protocol"` // DES, AES, AES192, AES256, AES192C, AES256C
	PrivPassword string `yaml:"priv_password"`
	SecurityLevel string `yaml:"security_level"` // noAuthNoPriv, authNoPriv, authPriv

	// DeviceType determines which OID mappings to use.
	DeviceType string `yaml:"device_type"`

	// CustomOIDs allows specifying additional OID mappings.
	CustomOIDs []OIDMapping `yaml:"custom_oids"`
}

// OIDMapping maps an OID to a metric type.
type OIDMapping struct {
	OID         string  `yaml:"oid"`
	MetricType  string  `yaml:"metric_type"`
	Scale       float32 `yaml:"scale"`
	Offset      float32 `yaml:"offset"`
	Description string  `yaml:"description"`
}

// DefaultConfig returns default SNMP configuration.
func DefaultConfig() Config {
	return Config{
		Config:    types.DefaultConfig(),
		Version:   "v2c",
		Community: "public",
		SecurityLevel: "authPriv",
	}
}

// New creates a new SNMP adapter.
func New(cfg Config) (*SNMPAdapter, error) {
	if cfg.Host == "" {
		return nil, fmt.Errorf("SNMP host is required")
	}
	if cfg.Port == 0 {
		cfg.Port = 161
	}
	if cfg.Timeout == 0 {
		cfg.Timeout = 5 * time.Second
	}

	client := &gosnmp.GoSNMP{
		Target:    cfg.Host,
		Port:      uint16(cfg.Port),
		Timeout:   cfg.Timeout,
		Retries:   cfg.RetryAttempts,
	}

	// Configure version-specific settings
	switch cfg.Version {
	case "v2c", "2c", "2":
		client.Version = gosnmp.Version2c
		client.Community = cfg.Community
	case "v3", "3":
		client.Version = gosnmp.Version3
		client.SecurityModel = gosnmp.UserSecurityModel
		client.SecurityParameters = &gosnmp.UsmSecurityParameters{
			UserName: cfg.Username,
		}

		// Set security level
		switch cfg.SecurityLevel {
		case "noAuthNoPriv":
			client.MsgFlags = gosnmp.NoAuthNoPriv
		case "authNoPriv":
			client.MsgFlags = gosnmp.AuthNoPriv
			client.SecurityParameters.(*gosnmp.UsmSecurityParameters).AuthenticationProtocol = parseAuthProtocol(cfg.AuthProtocol)
			client.SecurityParameters.(*gosnmp.UsmSecurityParameters).AuthenticationPassphrase = cfg.AuthPassword
		case "authPriv":
			client.MsgFlags = gosnmp.AuthPriv
			client.SecurityParameters.(*gosnmp.UsmSecurityParameters).AuthenticationProtocol = parseAuthProtocol(cfg.AuthProtocol)
			client.SecurityParameters.(*gosnmp.UsmSecurityParameters).AuthenticationPassphrase = cfg.AuthPassword
			client.SecurityParameters.(*gosnmp.UsmSecurityParameters).PrivacyProtocol = parsePrivProtocol(cfg.PrivProtocol)
			client.SecurityParameters.(*gosnmp.UsmSecurityParameters).PrivacyPassphrase = cfg.PrivPassword
		default:
			client.MsgFlags = gosnmp.AuthPriv
		}
	default:
		return nil, fmt.Errorf("unsupported SNMP version: %s", cfg.Version)
	}

	// Build OID mappings
	oidMap := buildOIDMappings(cfg.DeviceType, cfg.CustomOIDs)

	return &SNMPAdapter{
		config: cfg,
		client: client,
		oidMap: oidMap,
	}, nil
}

// Name returns the adapter name.
func (s *SNMPAdapter) Name() string {
	return fmt.Sprintf("snmp-%s:%d", s.config.Host, s.config.Port)
}

// Connect establishes the SNMP connection.
func (s *SNMPAdapter) Connect(ctx context.Context) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.connected {
		return nil
	}

	if err := s.client.Connect(); err != nil {
		return fmt.Errorf("SNMP connect failed: %w", err)
	}

	s.connected = true
	log.Printf("[SNMP] Connected to %s:%d", s.config.Host, s.config.Port)
	return nil
}

// Close closes the SNMP connection.
func (s *SNMPAdapter) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.connected {
		return nil
	}

	if s.client.Conn != nil {
		if err := s.client.Conn.Close(); err != nil {
			return err
		}
	}

	s.connected = false
	log.Printf("[SNMP] Disconnected from %s:%d", s.config.Host, s.config.Port)
	return nil
}

// IsConnected returns true if connected.
func (s *SNMPAdapter) IsConnected() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.connected
}

// CollectMetrics collects all mapped metrics.
func (s *SNMPAdapter) CollectMetrics(ctx context.Context) ([]protocol.Metric, error) {
	s.mu.RLock()
	if !s.connected {
		s.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	s.mu.RUnlock()

	// Build list of OIDs to query
	oids := make([]string, len(s.oidMap))
	for i, m := range s.oidMap {
		oids[i] = m.ExternalID
	}

	// Query in batches (SNMP has limits on PDU size)
	const batchSize = 10
	var metrics []protocol.Metric

	for i := 0; i < len(oids); i += batchSize {
		end := i + batchSize
		if end > len(oids) {
			end = len(oids)
		}
		batch := oids[i:end]

		result, err := s.client.Get(batch)
		if err != nil {
			log.Printf("[SNMP] GET failed for batch %d: %v", i/batchSize, err)
			continue
		}

		for _, pdu := range result.Variables {
			metric := s.parsePDU(pdu)
			if metric != nil {
				metrics = append(metrics, *metric)
			}
		}
	}

	return metrics, nil
}

// CollectMetric collects a specific metric type.
func (s *SNMPAdapter) CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error) {
	// Find the OID for this metric type
	var oid string
	var mapping *types.MetricMapping
	for i := range s.oidMap {
		if s.oidMap[i].MetricType == metricType {
			oid = s.oidMap[i].ExternalID
			mapping = &s.oidMap[i]
			break
		}
	}

	if oid == "" {
		return nil, fmt.Errorf("no OID mapping for metric type %d", metricType)
	}

	s.mu.RLock()
	if !s.connected {
		s.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	s.mu.RUnlock()

	result, err := s.client.Get([]string{oid})
	if err != nil {
		return nil, fmt.Errorf("SNMP GET failed: %w", err)
	}

	if len(result.Variables) == 0 {
		return nil, fmt.Errorf("no result for OID %s", oid)
	}

	pdu := result.Variables[0]
	value := s.parseValue(pdu)
	if mapping != nil {
		value = mapping.ApplyTransform(value)
	}

	return &protocol.Metric{
		Type:  metricType,
		Value: value,
	}, nil
}

// parsePDU parses an SNMP PDU into a metric.
func (s *SNMPAdapter) parsePDU(pdu gosnmp.SnmpPDU) *protocol.Metric {
	// Find the mapping for this OID
	var mapping *types.MetricMapping
	for i := range s.oidMap {
		if s.oidMap[i].ExternalID == pdu.Name {
			mapping = &s.oidMap[i]
			break
		}
	}

	if mapping == nil {
		return nil
	}

	// Skip error types
	if pdu.Type == gosnmp.NoSuchObject || pdu.Type == gosnmp.NoSuchInstance || pdu.Type == gosnmp.Null {
		return nil
	}

	value := s.parseValue(pdu)
	value = mapping.ApplyTransform(value)

	return &protocol.Metric{
		Type:  mapping.MetricType,
		Value: value,
	}
}

// parseValue extracts a float32 value from an SNMP PDU.
func (s *SNMPAdapter) parseValue(pdu gosnmp.SnmpPDU) float32 {
	switch pdu.Type {
	case gosnmp.Integer:
		return float32(gosnmp.ToBigInt(pdu.Value).Int64())
	case gosnmp.Counter32, gosnmp.Gauge32, gosnmp.TimeTicks, gosnmp.Uinteger32:
		return float32(gosnmp.ToBigInt(pdu.Value).Uint64())
	case gosnmp.Counter64:
		return float32(gosnmp.ToBigInt(pdu.Value).Uint64())
	case gosnmp.OctetString:
		// Try to parse as number string
		str := string(pdu.Value.([]byte))
		var f float64
		if _, err := fmt.Sscanf(str, "%f", &f); err == nil {
			return float32(f)
		}
		return 0
	default:
		return 0
	}
}

// parseAuthProtocol converts auth protocol string to gosnmp constant.
func parseAuthProtocol(proto string) gosnmp.SnmpV3AuthProtocol {
	switch proto {
	case "MD5":
		return gosnmp.MD5
	case "SHA":
		return gosnmp.SHA
	case "SHA224":
		return gosnmp.SHA224
	case "SHA256":
		return gosnmp.SHA256
	case "SHA384":
		return gosnmp.SHA384
	case "SHA512":
		return gosnmp.SHA512
	default:
		return gosnmp.SHA256
	}
}

// parsePrivProtocol converts privacy protocol string to gosnmp constant.
func parsePrivProtocol(proto string) gosnmp.SnmpV3PrivProtocol {
	switch proto {
	case "DES":
		return gosnmp.DES
	case "AES", "AES128":
		return gosnmp.AES
	case "AES192":
		return gosnmp.AES192
	case "AES256":
		return gosnmp.AES256
	case "AES192C":
		return gosnmp.AES192C
	case "AES256C":
		return gosnmp.AES256C
	default:
		return gosnmp.AES256
	}
}
