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
	Bridge BridgeConfig `yaml:"bridge"`
	Device DeviceConfig `yaml:"device"`
	Cloud  CloudConfig  `yaml:"cloud"`
}

// BridgeConfig holds bridge-specific settings.
type BridgeConfig struct {
	StationID       string        `yaml:"station_id"`
	PollInterval    time.Duration `yaml:"poll_interval"`
	MetricsInterval time.Duration `yaml:"metrics_interval"`
	LogLevel        string        `yaml:"log_level"`
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
	Host string `yaml:"host"`
	Port int    `yaml:"port"`
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
			StationID:       "BS-001",
			PollInterval:    10 * time.Second,
			MetricsInterval: 30 * time.Second,
			LogLevel:        "info",
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
				Username: "bridge-user",
				Password: "",
			},
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
