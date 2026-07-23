package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// validConfig returns a configuration that passes Validate, so each test can
// break exactly one thing and assert on that.
func validConfig() *Config {
	c := DefaultConfig()
	c.Bridge.StationID = "MIPS-BS-001"
	c.Device.Transport = "tcp"
	c.Device.TCP.Host = "127.0.0.1"
	c.Device.TCP.Port = 9999
	c.Cloud.BaseURL = "http://localhost:8080"
	c.Cloud.Auth.Username = "edge"
	c.Cloud.Auth.Password = "secret"
	return c
}

func TestValidateAcceptsAWellFormedConfig(t *testing.T) {
	if err := validConfig().Validate(); err != nil {
		t.Fatalf("expected the baseline config to be valid, got %v", err)
	}
}

func TestValidateRejectsIncompleteConfigs(t *testing.T) {
	tests := []struct {
		name    string
		break_  func(*Config)
		wantMsg string
	}{
		{
			name:    "missing station id",
			break_:  func(c *Config) { c.Bridge.StationID = "" },
			wantMsg: "bridge.station_id",
		},
		{
			name:    "unknown transport",
			break_:  func(c *Config) { c.Device.Transport = "carrier-pigeon" },
			wantMsg: "device.transport",
		},
		{
			name: "serial transport without a port",
			break_: func(c *Config) {
				c.Device.Transport = "serial"
				c.Device.Serial.Port = ""
			},
			wantMsg: "device.serial.port",
		},
		{
			name:    "tcp transport without a host",
			break_:  func(c *Config) { c.Device.TCP.Host = "" },
			wantMsg: "device.tcp.host",
		},
		{
			name:    "tcp transport with a zero port",
			break_:  func(c *Config) { c.Device.TCP.Port = 0 },
			wantMsg: "device.tcp.port",
		},
		{
			name:    "tcp transport with a negative port",
			break_:  func(c *Config) { c.Device.TCP.Port = -1 },
			wantMsg: "device.tcp.port",
		},
		{
			name:    "missing cloud base url",
			break_:  func(c *Config) { c.Cloud.BaseURL = "" },
			wantMsg: "cloud.base_url",
		},
		{
			name:    "missing cloud username",
			break_:  func(c *Config) { c.Cloud.Auth.Username = "" },
			wantMsg: "cloud.auth.username",
		},
		{
			name:    "missing cloud password",
			break_:  func(c *Config) { c.Cloud.Auth.Password = "" },
			wantMsg: "cloud.auth.password",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := validConfig()
			tt.break_(c)

			err := c.Validate()
			if err == nil {
				t.Fatalf("expected %s to be rejected", tt.name)
			}
			// The message names the offending field, so an operator can fix
			// the file without reading the source.
			if !strings.Contains(err.Error(), tt.wantMsg) {
				t.Errorf("error %q does not mention %q", err, tt.wantMsg)
			}
		})
	}
}

func TestValidateIgnoresSerialPortWhenUsingTCP(t *testing.T) {
	c := validConfig()
	c.Device.Serial.Port = ""

	if err := c.Validate(); err != nil {
		t.Fatalf("serial settings should not matter for a tcp transport, got %v", err)
	}
}

func TestValidateIgnoresTCPSettingsWhenUsingSerial(t *testing.T) {
	c := validConfig()
	c.Device.Transport = "serial"
	c.Device.Serial.Port = "/dev/ttyS0"
	c.Device.TCP.Host = ""
	c.Device.TCP.Port = 0

	if err := c.Validate(); err != nil {
		t.Fatalf("tcp settings should not matter for a serial transport, got %v", err)
	}
}

func TestSaveAndLoadRoundTrip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "edge-bridge.yaml")
	original := validConfig()
	original.Bridge.StationID = "ROUNDTRIP-1"

	if err := original.Save(path); err != nil {
		t.Fatalf("Save returned an error: %v", err)
	}

	loaded, err := Load(path)
	if err != nil {
		t.Fatalf("Load returned an error: %v", err)
	}

	if loaded.Bridge.StationID != original.Bridge.StationID {
		t.Errorf("StationID = %q, want %q", loaded.Bridge.StationID, original.Bridge.StationID)
	}
	if loaded.Device.TCP.Host != original.Device.TCP.Host {
		t.Errorf("TCP.Host = %q, want %q", loaded.Device.TCP.Host, original.Device.TCP.Host)
	}
	if loaded.Cloud.Auth.Username != original.Cloud.Auth.Username {
		t.Errorf("Auth.Username = %q, want %q", loaded.Cloud.Auth.Username, original.Cloud.Auth.Username)
	}
}

func TestLoadRejectsAnInvalidConfigFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "invalid.yaml")
	invalid := validConfig()
	invalid.Bridge.StationID = "" // fails Validate

	if err := invalid.Save(path); err != nil {
		t.Fatalf("Save returned an error: %v", err)
	}

	// Load validates, so a file that would start a misconfigured bridge is
	// refused up front rather than failing later at connect time.
	if _, err := Load(path); err == nil {
		t.Fatal("expected Load to reject a config that fails validation")
	}
}

func TestLoadReportsAMissingFile(t *testing.T) {
	if _, err := Load(filepath.Join(t.TempDir(), "does-not-exist.yaml")); err == nil {
		t.Fatal("expected an error for a missing config file")
	}
}

func TestLoadReportsMalformedYAML(t *testing.T) {
	path := filepath.Join(t.TempDir(), "broken.yaml")
	if err := os.WriteFile(path, []byte("bridge: [this is not: valid yaml"), 0o600); err != nil {
		t.Fatalf("could not write fixture: %v", err)
	}

	if _, err := Load(path); err == nil {
		t.Fatal("expected an error for malformed YAML")
	}
}

func TestDefaultConfigIsUsableAfterSupplyingRequiredFields(t *testing.T) {
	// DefaultConfig is the starting point for `edge-bridge init`, so it should
	// only be missing the values an operator must supply.
	c := DefaultConfig()
	c.Cloud.BaseURL = "http://localhost:8080"
	c.Cloud.Auth.Username = "edge"
	c.Cloud.Auth.Password = "secret"

	if err := c.Validate(); err != nil {
		t.Fatalf("defaults plus credentials should validate, got %v", err)
	}
}
