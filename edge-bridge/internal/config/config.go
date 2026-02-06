// Package config provides configuration loading and management.
package config

import (
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config holds the complete bridge configuration.
type Config struct {
	Bridge   BridgeConfig   `yaml:"bridge"`
	Device   DeviceConfig   `yaml:"device"`
	Cloud    CloudConfig    `yaml:"cloud"`
	Adapters AdaptersConfig `yaml:"adapters"`
}

// AdaptersConfig holds configuration for all protocol adapters.
type AdaptersConfig struct {
	// Manager configuration
	CollectInterval time.Duration `yaml:"collect_interval"`
	RetryOnFailure  bool          `yaml:"retry_on_failure"`
	RetryInterval   time.Duration `yaml:"retry_interval"`

	// Protocol-specific adapters
	SNMP    []SNMPAdapterConfig    `yaml:"snmp"`
	MQTT    []MQTTAdapterConfig    `yaml:"mqtt"`
	NETCONF []NETCONFAdapterConfig `yaml:"netconf"`
	Modbus  []ModbusAdapterConfig  `yaml:"modbus"`
	ORAN    []ORANAdapterConfig    `yaml:"oran"`
}

// SNMPAdapterConfig holds SNMP adapter configuration.
type SNMPAdapterConfig struct {
	// Enabled controls whether this adapter is active.
	Enabled bool `yaml:"enabled"`

	// Name is a unique identifier for this adapter instance.
	Name string `yaml:"name"`

	// Target is the SNMP agent hostname or IP.
	Target string `yaml:"target"`

	// Port is the SNMP port (default 161).
	Port uint16 `yaml:"port"`

	// Version is "v2c" or "v3".
	Version string `yaml:"version"`

	// Community is the SNMPv2c community string.
	Community string `yaml:"community"`

	// V3 authentication settings (for SNMPv3)
	V3 SNMPV3Config `yaml:"v3"`

	// Timeout is the SNMP request timeout.
	Timeout time.Duration `yaml:"timeout"`

	// Retries is the number of SNMP request retries.
	Retries int `yaml:"retries"`

	// OIDProfile selects a predefined OID set ("standard", "vertiv", "schneider", "ericsson", "nokia").
	OIDProfile string `yaml:"oid_profile"`

	// CustomOIDs allows defining additional OID mappings.
	CustomOIDs []OIDMapping `yaml:"custom_oids"`
}

// SNMPV3Config holds SNMPv3 authentication settings.
type SNMPV3Config struct {
	// Username is the SNMPv3 security name.
	Username string `yaml:"username"`

	// AuthProtocol is the authentication protocol ("MD5", "SHA", "SHA256", "SHA512").
	AuthProtocol string `yaml:"auth_protocol"`

	// AuthPassword is the authentication passphrase.
	AuthPassword string `yaml:"auth_password"`

	// PrivProtocol is the privacy protocol ("DES", "AES", "AES192", "AES256").
	PrivProtocol string `yaml:"priv_protocol"`

	// PrivPassword is the privacy passphrase.
	PrivPassword string `yaml:"priv_password"`

	// SecurityLevel is "noAuthNoPriv", "authNoPriv", or "authPriv".
	SecurityLevel string `yaml:"security_level"`
}

// OIDMapping maps an OID to a metric type.
type OIDMapping struct {
	OID        string  `yaml:"oid"`
	MetricType string  `yaml:"metric_type"`
	Scale      float32 `yaml:"scale"`
	Offset     float32 `yaml:"offset"`
}

// MQTTAdapterConfig holds MQTT adapter configuration.
type MQTTAdapterConfig struct {
	// Enabled controls whether this adapter is active.
	Enabled bool `yaml:"enabled"`

	// Name is a unique identifier for this adapter instance.
	Name string `yaml:"name"`

	// Broker is the MQTT broker URL (e.g., "tcp://localhost:1883").
	Broker string `yaml:"broker"`

	// ClientID is the MQTT client ID.
	ClientID string `yaml:"client_id"`

	// Username for MQTT authentication.
	Username string `yaml:"username"`

	// Password for MQTT authentication.
	Password string `yaml:"password"`

	// UseTLS enables TLS connection.
	UseTLS bool `yaml:"use_tls"`

	// QoS level (0, 1, or 2).
	QoS byte `yaml:"qos"`

	// CleanSession starts a new session.
	CleanSession bool `yaml:"clean_session"`

	// Topics to subscribe to with their metric mappings.
	Topics []TopicMapping `yaml:"topics"`
}

// TopicMapping maps an MQTT topic pattern to a metric type.
type TopicMapping struct {
	// Topic is the MQTT topic pattern (supports + and # wildcards).
	Topic string `yaml:"topic"`

	// MetricType is the metric type name.
	MetricType string `yaml:"metric_type"`

	// ValuePath is the JSON path to the value (e.g., "value", "data.temperature").
	ValuePath string `yaml:"value_path"`

	// Scale is the multiplier for the value.
	Scale float32 `yaml:"scale"`

	// Offset is added after scaling.
	Offset float32 `yaml:"offset"`
}

// NETCONFAdapterConfig holds NETCONF adapter configuration.
type NETCONFAdapterConfig struct {
	// Enabled controls whether this adapter is active.
	Enabled bool `yaml:"enabled"`

	// Name is a unique identifier for this adapter instance.
	Name string `yaml:"name"`

	// Host is the NETCONF server hostname or IP.
	Host string `yaml:"host"`

	// Port is the NETCONF port (default 830).
	Port uint16 `yaml:"port"`

	// Username for SSH authentication.
	Username string `yaml:"username"`

	// Password for SSH authentication.
	Password string `yaml:"password"`

	// PrivateKey is the path to SSH private key file (optional).
	PrivateKey string `yaml:"private_key"`

	// Timeout for NETCONF operations.
	Timeout time.Duration `yaml:"timeout"`

	// DeviceType determines YANG path mappings ("ericsson", "nokia", "huawei", "generic").
	DeviceType string `yaml:"device_type"`

	// DatastoreSource specifies the datastore ("running", "candidate", "startup").
	DatastoreSource string `yaml:"datastore_source"`

	// CustomPaths allows defining additional YANG path mappings.
	CustomPaths []YANGPathMapping `yaml:"custom_paths"`
}

// YANGPathMapping maps a YANG XPath to a metric type.
type YANGPathMapping struct {
	// XPath is the YANG path to the data element.
	XPath string `yaml:"xpath"`

	// MetricType is the metric type name.
	MetricType string `yaml:"metric_type"`

	// Scale is the multiplier for the value.
	Scale float32 `yaml:"scale"`

	// Offset is added after scaling.
	Offset float32 `yaml:"offset"`

	// Description is a human-readable description.
	Description string `yaml:"description"`
}

// ModbusAdapterConfig holds Modbus adapter configuration.
type ModbusAdapterConfig struct {
	// Enabled controls whether this adapter is active.
	Enabled bool `yaml:"enabled"`

	// Name is a unique identifier for this adapter instance.
	Name string `yaml:"name"`

	// Mode is "tcp" or "rtu" (serial).
	Mode string `yaml:"mode"`

	// Host is the Modbus TCP server hostname or IP (for TCP mode).
	Host string `yaml:"host"`

	// Port is the Modbus TCP port (default 502).
	Port uint16 `yaml:"port"`

	// SerialPort is the serial device path (for RTU mode).
	SerialPort string `yaml:"serial_port"`

	// BaudRate for serial communication (for RTU mode).
	BaudRate int `yaml:"baud_rate"`

	// DataBits for serial communication (7 or 8).
	DataBits int `yaml:"data_bits"`

	// Parity for serial communication ("N", "E", "O").
	Parity string `yaml:"parity"`

	// StopBits for serial communication (1 or 2).
	StopBits int `yaml:"stop_bits"`

	// SlaveID is the Modbus slave/unit ID.
	SlaveID byte `yaml:"slave_id"`

	// Timeout for Modbus operations.
	Timeout time.Duration `yaml:"timeout"`

	// DeviceType determines register mappings ("ups", "pdu", "rectifier", "generic").
	DeviceType string `yaml:"device_type"`

	// CustomRegisters allows defining additional register mappings.
	CustomRegisters []RegisterMapping `yaml:"custom_registers"`
}

// RegisterMapping maps a Modbus register to a metric type.
type RegisterMapping struct {
	// Address is the register address.
	Address uint16 `yaml:"address"`

	// RegisterType is "holding", "input", "coil", or "discrete".
	RegisterType string `yaml:"register_type"`

	// DataType is "uint16", "int16", "uint32", "int32", "float32".
	DataType string `yaml:"data_type"`

	// MetricType is the metric type name.
	MetricType string `yaml:"metric_type"`

	// Scale is the multiplier for the value.
	Scale float32 `yaml:"scale"`

	// Offset is added after scaling.
	Offset float32 `yaml:"offset"`

	// Description is a human-readable description.
	Description string `yaml:"description"`
}

// ORANAdapterConfig holds O-RAN O1 interface adapter configuration.
type ORANAdapterConfig struct {
	// Enabled controls whether this adapter is active.
	Enabled bool `yaml:"enabled"`

	// Name is a unique identifier for this adapter instance.
	Name string `yaml:"name"`

	// Host is the O-RAN component hostname or IP.
	Host string `yaml:"host"`

	// Port is the NETCONF port (default 830).
	Port uint16 `yaml:"port"`

	// Username for SSH/NETCONF authentication.
	Username string `yaml:"username"`

	// Password for SSH/NETCONF authentication.
	Password string `yaml:"password"`

	// PrivateKey is the path to SSH private key file (optional).
	PrivateKey string `yaml:"private_key"`

	// Timeout for O-RAN operations.
	Timeout time.Duration `yaml:"timeout"`

	// ComponentType determines YANG models ("o-ru", "o-du", "o-cu", "smo").
	ComponentType string `yaml:"component_type"`

	// VESEnabled enables VES event listener for FM/PM.
	VESEnabled bool `yaml:"ves_enabled"`

	// VESPort is the local port for VES event listener.
	VESPort int `yaml:"ves_port"`

	// CustomPaths allows defining additional YANG path mappings.
	CustomPaths []YANGPathMapping `yaml:"custom_paths"`
}

// BridgeConfig holds bridge-specific settings.
type BridgeConfig struct {
	// Bridge identification (for edge-bridge registration)
	BridgeID    string `yaml:"bridge_id"`
	BridgeName  string `yaml:"bridge_name"`
	Version     string `yaml:"version"`
	CallbackURL string `yaml:"callback_url"` // Optional callback endpoint

	// Station information
	StationID        string        `yaml:"station_id"`
	StationName      string        `yaml:"station_name"`
	Location         string        `yaml:"location"`
	Latitude         float64       `yaml:"latitude"`
	Longitude        float64       `yaml:"longitude"`
	StationType      string        `yaml:"station_type"`
	PowerConsumption float64       `yaml:"power_consumption"`
	Description      string        `yaml:"description"`

	// Operational settings
	PollInterval      time.Duration `yaml:"poll_interval"`
	MetricsInterval   time.Duration `yaml:"metrics_interval"`
	HeartbeatInterval time.Duration `yaml:"heartbeat_interval"` // Default 30s
	LogLevel          string        `yaml:"log_level"`
}

// DeviceConfig holds device connection settings.
type DeviceConfig struct {
	Transport string       `yaml:"transport"`
	Serial    SerialConfig `yaml:"serial"`
	TCP       TCPConfig    `yaml:"tcp"`
}

// SerialConfig holds serial port settings.
type SerialConfig struct {
	Port string `yaml:"port"`
	Baud int    `yaml:"baud"`
}

// TCPConfig holds TCP connection settings.
type TCPConfig struct {
	Host string    `yaml:"host"`
	Port int       `yaml:"port"`
	TLS  TLSConfig `yaml:"tls"`
}

// TLSConfig holds TLS settings for secure connections.
type TLSConfig struct {
	// Enabled controls whether TLS is used.
	Enabled bool `yaml:"enabled"`

	// CertFile is the path to the client certificate file (PEM).
	CertFile string `yaml:"cert_file"`

	// KeyFile is the path to the client private key file (PEM).
	KeyFile string `yaml:"key_file"`

	// CAFile is the path to the CA certificate file (PEM) for server verification.
	CAFile string `yaml:"ca_file"`

	// ServerName is the expected server name for verification.
	ServerName string `yaml:"server_name"`

	// InsecureSkipVerify disables server certificate verification (for testing only).
	InsecureSkipVerify bool `yaml:"insecure_skip_verify"`
}

// CloudConfig holds cloud API settings.
type CloudConfig struct {
	BaseURL       string        `yaml:"base_url"`
	Auth          AuthConfig    `yaml:"auth"`
	Timeout       time.Duration `yaml:"timeout"`
	RetryAttempts int           `yaml:"retry_attempts"`
}

// AuthConfig holds authentication settings.
type AuthConfig struct {
	Username string `yaml:"username"`
	Password string `yaml:"password"`
}

// DefaultConfig returns a configuration with default values.
func DefaultConfig() *Config {
	return &Config{
		Bridge: BridgeConfig{
			StationID:        "MIPS-BS-001",
			StationName:      "MIPS Virtual Base Station",
			Location:         "Shenzhen R&D Center",
			Latitude:         22.5431,
			Longitude:        114.0579,
			StationType:      "5G_NR",
			PowerConsumption: 1500.0,
			Description:      "MIPS edge device running virtual base station simulator",
			PollInterval:     10 * time.Second,
			MetricsInterval:  30 * time.Second,
			LogLevel:         "info",
		},
		Device: DeviceConfig{
			Transport: "tcp",
			Serial: SerialConfig{
				Port: "/dev/ttyS0",
				Baud: 115200,
			},
			TCP: TCPConfig{
				Host: "127.0.0.1",
				Port: 9999,
			},
		},
		Cloud: CloudConfig{
			BaseURL:       "http://localhost:8080",
			Timeout:       30 * time.Second,
			RetryAttempts: 3,
			Auth: AuthConfig{
				Username: "",
				Password: "",
			},
		},
		Adapters: AdaptersConfig{
			CollectInterval: 30 * time.Second,
			RetryOnFailure:  true,
			RetryInterval:   30 * time.Second,
			SNMP:            []SNMPAdapterConfig{},
			MQTT:            []MQTTAdapterConfig{},
			NETCONF:         []NETCONFAdapterConfig{},
			Modbus:          []ModbusAdapterConfig{},
			ORAN:            []ORANAdapterConfig{},
		},
	}
}

// Load loads configuration from a YAML file.
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	// Start with defaults
	config := DefaultConfig()

	// Expand environment variables
	expanded := os.ExpandEnv(string(data))

	if err := yaml.Unmarshal([]byte(expanded), config); err != nil {
		return nil, fmt.Errorf("failed to parse config file: %w", err)
	}

	if err := config.Validate(); err != nil {
		return nil, fmt.Errorf("invalid configuration: %w", err)
	}

	return config, nil
}

// Validate validates the configuration.
func (c *Config) Validate() error {
	if c.Bridge.StationID == "" {
		return fmt.Errorf("bridge.station_id is required")
	}

	if c.Device.Transport != "serial" && c.Device.Transport != "tcp" {
		return fmt.Errorf("device.transport must be 'serial' or 'tcp'")
	}

	if c.Device.Transport == "serial" && c.Device.Serial.Port == "" {
		return fmt.Errorf("device.serial.port is required")
	}

	if c.Device.Transport == "tcp" {
		if c.Device.TCP.Host == "" {
			return fmt.Errorf("device.tcp.host is required")
		}
		if c.Device.TCP.Port <= 0 {
			return fmt.Errorf("device.tcp.port must be positive")
		}
	}

	if c.Cloud.BaseURL == "" {
		return fmt.Errorf("cloud.base_url is required")
	}

	if c.Cloud.Auth.Username == "" {
		return fmt.Errorf("cloud.auth.username is required")
	}

	if c.Cloud.Auth.Password == "" {
		return fmt.Errorf("cloud.auth.password is required")
	}

	return nil
}

// Save saves the configuration to a YAML file.
func (c *Config) Save(path string) error {
	data, err := yaml.Marshal(c)
	if err != nil {
		return fmt.Errorf("failed to marshal config: %w", err)
	}

	if err := os.WriteFile(path, data, 0644); err != nil {
		return fmt.Errorf("failed to write config file: %w", err)
	}

	return nil
}
