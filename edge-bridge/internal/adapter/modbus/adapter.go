// Package modbus provides a Modbus adapter for collecting metrics from power systems
// including UPS, PDUs, rectifiers, and other industrial equipment.
package modbus

import (
	"context"
	"encoding/binary"
	"fmt"
	"log"
	"math"
	"sync"
	"time"

	"github.com/goburrow/modbus"

	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// ModbusAdapter collects metrics via Modbus TCP or RTU.
type ModbusAdapter struct {
	config      Config
	client      modbus.Client
	handler     modbus.ClientHandler
	registerMap []types.MetricMapping
	mu          sync.RWMutex
	connected   bool
}

// Config holds Modbus-specific configuration.
type Config struct {
	types.Config `yaml:",inline"`

	// Mode is "tcp" or "rtu" (serial).
	Mode string `yaml:"mode"`

	// TCP settings
	// Host is inherited from types.Config
	// Port is inherited from types.Config

	// RTU/Serial settings
	SerialPort string `yaml:"serial_port"`
	BaudRate   int    `yaml:"baud_rate"`
	DataBits   int    `yaml:"data_bits"`
	Parity     string `yaml:"parity"` // "N", "E", "O"
	StopBits   int    `yaml:"stop_bits"`

	// SlaveID is the Modbus slave/unit ID.
	SlaveID byte `yaml:"slave_id"`

	// DeviceType determines which register mappings to use.
	// Options: "ups", "pdu", "rectifier", "generic"
	DeviceType string `yaml:"device_type"`

	// CustomRegisters allows specifying additional register mappings.
	CustomRegisters []RegisterMapping `yaml:"custom_registers"`
}

// RegisterMapping maps a Modbus register to a metric type.
type RegisterMapping struct {
	Address      uint16  `yaml:"address"`
	RegisterType string  `yaml:"register_type"` // "holding", "input", "coil", "discrete"
	DataType     string  `yaml:"data_type"`     // "uint16", "int16", "uint32", "int32", "float32"
	MetricType   string  `yaml:"metric_type"`
	Scale        float32 `yaml:"scale"`
	Offset       float32 `yaml:"offset"`
	Description  string  `yaml:"description"`
}

// DefaultConfig returns default Modbus configuration.
func DefaultConfig() Config {
	return Config{
		Config:   types.DefaultConfig(),
		Mode:     "tcp",
		SlaveID:  1,
		BaudRate: 9600,
		DataBits: 8,
		Parity:   "N",
		StopBits: 1,
	}
}

// New creates a new Modbus adapter.
func New(cfg Config) (*ModbusAdapter, error) {
	if cfg.Mode == "" {
		cfg.Mode = "tcp"
	}

	switch cfg.Mode {
	case "tcp":
		if cfg.Host == "" {
			return nil, fmt.Errorf("Modbus TCP host is required")
		}
		if cfg.Port == 0 {
			cfg.Port = 502
		}
	case "rtu":
		if cfg.SerialPort == "" {
			return nil, fmt.Errorf("Modbus RTU serial port is required")
		}
	default:
		return nil, fmt.Errorf("unsupported Modbus mode: %s", cfg.Mode)
	}

	if cfg.Timeout == 0 {
		cfg.Timeout = 5 * time.Second
	}

	// Build register mappings
	registerMap := buildRegisterMappings(cfg.DeviceType, cfg.CustomRegisters)

	return &ModbusAdapter{
		config:      cfg,
		registerMap: registerMap,
	}, nil
}

// Name returns the adapter name.
func (m *ModbusAdapter) Name() string {
	if m.config.Mode == "tcp" {
		return fmt.Sprintf("modbus-tcp-%s:%d", m.config.Host, m.config.Port)
	}
	return fmt.Sprintf("modbus-rtu-%s", m.config.SerialPort)
}

// Connect establishes the Modbus connection.
func (m *ModbusAdapter) Connect(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.connected {
		return nil
	}

	var handler modbus.ClientHandler

	if m.config.Mode == "tcp" {
		tcpHandler := modbus.NewTCPClientHandler(
			fmt.Sprintf("%s:%d", m.config.Host, m.config.Port),
		)
		tcpHandler.Timeout = m.config.Timeout
		tcpHandler.SlaveId = m.config.SlaveID

		if err := tcpHandler.Connect(); err != nil {
			return fmt.Errorf("Modbus TCP connect failed: %w", err)
		}
		handler = tcpHandler
	} else {
		rtuHandler := modbus.NewRTUClientHandler(m.config.SerialPort)
		rtuHandler.BaudRate = m.config.BaudRate
		rtuHandler.DataBits = m.config.DataBits
		rtuHandler.Parity = m.config.Parity
		rtuHandler.StopBits = m.config.StopBits
		rtuHandler.SlaveId = m.config.SlaveID
		rtuHandler.Timeout = m.config.Timeout

		if err := rtuHandler.Connect(); err != nil {
			return fmt.Errorf("Modbus RTU connect failed: %w", err)
		}
		handler = rtuHandler
	}

	m.handler = handler
	m.client = modbus.NewClient(handler)
	m.connected = true

	log.Printf("[Modbus] Connected to %s", m.Name())
	return nil
}

// Close closes the Modbus connection.
func (m *ModbusAdapter) Close() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return nil
	}

	if m.handler != nil {
		if tcpHandler, ok := m.handler.(*modbus.TCPClientHandler); ok {
			tcpHandler.Close()
		} else if rtuHandler, ok := m.handler.(*modbus.RTUClientHandler); ok {
			rtuHandler.Close()
		}
	}

	m.connected = false
	m.handler = nil
	m.client = nil

	log.Printf("[Modbus] Disconnected from %s", m.Name())
	return nil
}

// IsConnected returns true if connected.
func (m *ModbusAdapter) IsConnected() bool {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.connected
}

// CollectMetrics collects all mapped metrics.
func (m *ModbusAdapter) CollectMetrics(ctx context.Context) ([]protocol.Metric, error) {
	m.mu.RLock()
	if !m.connected {
		m.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	client := m.client
	m.mu.RUnlock()

	var metrics []protocol.Metric

	for _, mapping := range m.registerMap {
		value, err := m.readRegister(client, mapping)
		if err != nil {
			log.Printf("[Modbus] Read failed for %s at %d: %v",
				mapping.Description, getAddressFromExternalID(mapping.ExternalID), err)
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

// CollectMetric collects a specific metric type.
func (m *ModbusAdapter) CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error) {
	// Find the register mapping for this metric type
	var mapping *types.MetricMapping
	for i := range m.registerMap {
		if m.registerMap[i].MetricType == metricType {
			mapping = &m.registerMap[i]
			break
		}
	}

	if mapping == nil {
		return nil, fmt.Errorf("no register mapping for metric type %d", metricType)
	}

	m.mu.RLock()
	if !m.connected {
		m.mu.RUnlock()
		return nil, fmt.Errorf("not connected")
	}
	client := m.client
	m.mu.RUnlock()

	value, err := m.readRegister(client, *mapping)
	if err != nil {
		return nil, fmt.Errorf("Modbus read failed: %w", err)
	}

	transformedValue := mapping.ApplyTransform(value)

	return &protocol.Metric{
		Type:  metricType,
		Value: transformedValue,
	}, nil
}

// readRegister reads a single register based on the mapping.
func (m *ModbusAdapter) readRegister(client modbus.Client, mapping types.MetricMapping) (float32, error) {
	// Parse the external ID to get register type, address, and data type
	regType, address, dataType := parseExternalID(mapping.ExternalID)

	var data []byte
	var err error

	// Determine number of registers to read based on data type
	quantity := uint16(1)
	if dataType == "uint32" || dataType == "int32" || dataType == "float32" {
		quantity = 2
	}

	// Read based on register type
	switch regType {
	case "holding":
		data, err = client.ReadHoldingRegisters(address, quantity)
	case "input":
		data, err = client.ReadInputRegisters(address, quantity)
	case "coil":
		data, err = client.ReadCoils(address, 1)
	case "discrete":
		data, err = client.ReadDiscreteInputs(address, 1)
	default:
		return 0, fmt.Errorf("unknown register type: %s", regType)
	}

	if err != nil {
		return 0, err
	}

	// Parse the data based on data type
	return parseData(data, dataType)
}

// parseData converts raw bytes to float32 based on data type.
func parseData(data []byte, dataType string) (float32, error) {
	if len(data) < 2 {
		return 0, fmt.Errorf("insufficient data")
	}

	switch dataType {
	case "uint16":
		return float32(binary.BigEndian.Uint16(data)), nil
	case "int16":
		return float32(int16(binary.BigEndian.Uint16(data))), nil
	case "uint32":
		if len(data) < 4 {
			return 0, fmt.Errorf("insufficient data for uint32")
		}
		return float32(binary.BigEndian.Uint32(data)), nil
	case "int32":
		if len(data) < 4 {
			return 0, fmt.Errorf("insufficient data for int32")
		}
		return float32(int32(binary.BigEndian.Uint32(data))), nil
	case "float32":
		if len(data) < 4 {
			return 0, fmt.Errorf("insufficient data for float32")
		}
		bits := binary.BigEndian.Uint32(data)
		return math.Float32frombits(bits), nil
	case "bool", "coil":
		if data[0] != 0 {
			return 1, nil
		}
		return 0, nil
	default:
		return float32(binary.BigEndian.Uint16(data)), nil
	}
}

// parseExternalID extracts register type, address, and data type from external ID.
// Format: "type:address:datatype" e.g., "holding:100:uint16"
func parseExternalID(externalID string) (regType string, address uint16, dataType string) {
	var addr int
	_, _ = fmt.Sscanf(externalID, "%[^:]:%d:%s", &regType, &addr, &dataType)
	if regType == "" {
		regType = "holding"
	}
	if dataType == "" {
		dataType = "uint16"
	}
	return regType, uint16(addr), dataType
}

// getAddressFromExternalID extracts just the address from external ID.
func getAddressFromExternalID(externalID string) uint16 {
	_, address, _ := parseExternalID(externalID)
	return address
}
