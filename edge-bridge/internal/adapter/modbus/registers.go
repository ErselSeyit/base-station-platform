// Package modbus provides Modbus register mappings for various power devices.
package modbus

import (
	"fmt"

	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// Common Modbus register address formats.
// Format: "type:address:datatype"
const (
	regInput0U16 = "input:0:uint16"
	regInput1U16 = "input:1:uint16"
	regInput2U16 = "input:2:uint16"
	regInput3U16 = "input:3:uint16"
	regInput4U16 = "input:4:uint16"
	regInput6U16 = "input:6:uint16"
)

// buildRegisterMappings creates register mappings based on device type.
func buildRegisterMappings(deviceType string, customRegisters []RegisterMapping) []types.MetricMapping {
	var mappings []types.MetricMapping

	// Add device-specific mappings
	switch deviceType {
	case "ups":
		mappings = append(mappings, upsMappings()...)
	case "pdu":
		mappings = append(mappings, pduMappings()...)
	case "rectifier":
		mappings = append(mappings, rectifierMappings()...)
	case "generic", "":
		mappings = append(mappings, genericMappings()...)
	}

	// Add custom mappings
	for _, custom := range customRegisters {
		metricType, ok := parseMetricType(custom.MetricType)
		if !ok {
			continue
		}

		scale := custom.Scale
		if scale == 0 {
			scale = 1.0
		}

		// Build external ID from register info
		externalID := fmt.Sprintf("%s:%d:%s",
			custom.RegisterType, custom.Address, custom.DataType)

		mappings = append(mappings, types.MetricMapping{
			ExternalID:  externalID,
			MetricType:  metricType,
			Scale:       scale,
			Offset:      custom.Offset,
			Description: custom.Description,
		})
	}

	return mappings
}

// upsMappings returns Modbus register mappings for UPS devices.
// Based on common UPS Modbus implementations (APC, Vertiv, Eaton).
func upsMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// Input measurements
		{
			ExternalID:  regInput0U16,
			MetricType:  protocol.MetricUtilityVoltageL1,
			Scale:       0.1, // tenths of volts
			Description: "UPS input voltage L1",
		},
		{
			ExternalID:  regInput1U16,
			MetricType:  protocol.MetricUtilityVoltageL2,
			Scale:       0.1,
			Description: "UPS input voltage L2",
		},
		{
			ExternalID:  regInput2U16,
			MetricType:  protocol.MetricUtilityVoltageL3,
			Scale:       0.1,
			Description: "UPS input voltage L3",
		},
		{
			ExternalID:  regInput3U16,
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1,
			Description: "UPS input current",
		},
		{
			ExternalID:  regInput4U16,
			MetricType:  protocol.MetricPowerFactor,
			Scale:       0.01,
			Description: "UPS input power factor",
		},

		// Output measurements
		{
			ExternalID:  "holding:10:uint16",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.1,
			Description: "UPS output voltage",
		},
		{
			ExternalID:  "holding:11:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1,
			Description: "UPS output current",
		},
		{
			ExternalID:  "holding:12:uint16",
			MetricType:  protocol.MetricPower,
			Scale:       1.0, // watts
			Description: "UPS output power",
		},
		{
			ExternalID:  "holding:13:uint16",
			MetricType:  protocol.MetricSitePowerKWH,
			Scale:       0.1, // kWh
			Description: "UPS cumulative energy",
		},

		// Battery measurements
		{
			ExternalID:  "holding:20:uint16",
			MetricType:  protocol.MetricBatterySOC,
			Scale:       1.0, // percentage
			Description: "UPS battery charge level",
		},
		{
			ExternalID:  "holding:21:uint16",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.1, // tenths of volts
			Description: "UPS battery voltage",
		},
		{
			ExternalID:  "holding:22:int16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1, // tenths of amps (signed for charge/discharge)
			Description: "UPS battery current",
		},
		{
			ExternalID:  "holding:23:uint16",
			MetricType:  protocol.MetricBatteryTemp,
			Scale:       0.1,
			Description: "UPS battery temperature",
		},
		{
			ExternalID:  "holding:24:uint16",
			MetricType:  protocol.MetricBatteryDOD,
			Scale:       1.0,
			Description: "UPS battery depth of discharge",
		},
		{
			ExternalID:  "holding:25:uint32",
			MetricType:  protocol.MetricGeneratorRuntime,
			Scale:       1.0, // minutes
			Description: "UPS runtime remaining",
		},

		// Environmental
		{
			ExternalID:  "input:30:int16",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1,
			Description: "UPS ambient temperature",
		},

		// Status (as numeric codes)
		{
			ExternalID:  "holding:40:uint16",
			MetricType:  protocol.MetricUptime,
			Scale:       1.0,
			Description: "UPS status code",
		},
	}
}

// pduMappings returns Modbus register mappings for PDU devices.
// Based on common intelligent PDU implementations.
func pduMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// Main input measurements
		{
			ExternalID:  regInput0U16,
			MetricType:  protocol.MetricVoltage,
			Scale:       0.1,
			Description: "PDU input voltage",
		},
		{
			ExternalID:  regInput1U16,
			MetricType:  protocol.MetricCurrent,
			Scale:       0.01, // hundredths of amps
			Description: "PDU total current",
		},
		{
			ExternalID:  "input:2:uint32",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "PDU total power",
		},
		{
			ExternalID:  "input:4:uint32",
			MetricType:  protocol.MetricSitePowerKWH,
			Scale:       0.001, // Wh to kWh
			Description: "PDU cumulative energy",
		},
		{
			ExternalID:  regInput6U16,
			MetricType:  protocol.MetricPowerFactor,
			Scale:       0.01,
			Description: "PDU power factor",
		},

		// Per-outlet measurements (first 8 outlets)
		{
			ExternalID:  "holding:100:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.01,
			Description: "PDU outlet 1 current",
		},
		{
			ExternalID:  "holding:101:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.01,
			Description: "PDU outlet 2 current",
		},
		{
			ExternalID:  "holding:102:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.01,
			Description: "PDU outlet 3 current",
		},
		{
			ExternalID:  "holding:103:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.01,
			Description: "PDU outlet 4 current",
		},

		// Environmental sensors
		{
			ExternalID:  "input:200:int16",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1,
			Description: "PDU sensor 1 temperature",
		},
		{
			ExternalID:  "input:201:uint16",
			MetricType:  protocol.MetricHumidity,
			Scale:       0.1,
			Description: "PDU sensor 1 humidity",
		},
	}
}

// rectifierMappings returns Modbus register mappings for rectifier/power systems.
// Based on telecom rectifier standards (Eltek, Emerson, Huawei).
func rectifierMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// AC Input
		{
			ExternalID:  regInput0U16,
			MetricType:  protocol.MetricUtilityVoltageL1,
			Scale:       0.1,
			Description: "Rectifier AC input voltage L1",
		},
		{
			ExternalID:  regInput1U16,
			MetricType:  protocol.MetricUtilityVoltageL2,
			Scale:       0.1,
			Description: "Rectifier AC input voltage L2",
		},
		{
			ExternalID:  regInput2U16,
			MetricType:  protocol.MetricUtilityVoltageL3,
			Scale:       0.1,
			Description: "Rectifier AC input voltage L3",
		},
		{
			ExternalID:  regInput3U16,
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1,
			Description: "Rectifier AC input current",
		},

		// DC Output
		{
			ExternalID:  "holding:10:uint16",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.01, // -48V systems often use 0.01V resolution
			Description: "Rectifier DC output voltage",
		},
		{
			ExternalID:  "holding:11:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1,
			Description: "Rectifier DC load current",
		},
		{
			ExternalID:  "holding:12:uint32",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "Rectifier DC output power",
		},

		// Battery
		{
			ExternalID:  "holding:20:int16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1, // Signed: positive=charging, negative=discharging
			Description: "Battery current",
		},
		{
			ExternalID:  "holding:21:uint16",
			MetricType:  protocol.MetricBatterySOC,
			Scale:       1.0,
			Description: "Battery capacity",
		},
		{
			ExternalID:  "holding:22:uint16",
			MetricType:  protocol.MetricBatteryTemp,
			Scale:       0.1,
			Description: "Battery temperature",
		},
		{
			ExternalID:  "holding:23:uint32",
			MetricType:  protocol.MetricGeneratorRuntime,
			Scale:       1.0, // minutes
			Description: "Battery backup time remaining",
		},

		// Module status (per rectifier module)
		{
			ExternalID:  "holding:100:uint16",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "Rectifier module 1 output power",
		},
		{
			ExternalID:  "holding:101:int16",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1,
			Description: "Rectifier module 1 temperature",
		},
		{
			ExternalID:  "holding:102:uint16",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "Rectifier module 2 output power",
		},
		{
			ExternalID:  "holding:103:int16",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1,
			Description: "Rectifier module 2 temperature",
		},

		// System
		{
			ExternalID:  "input:200:int16",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1,
			Description: "Cabinet ambient temperature",
		},
	}
}

// genericMappings returns basic Modbus register mappings.
func genericMappings() []types.MetricMapping {
	return []types.MetricMapping{
		{
			ExternalID:  "holding:0:uint16",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.1,
			Description: "Voltage (register 0)",
		},
		{
			ExternalID:  "holding:1:uint16",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.1,
			Description: "Current (register 1)",
		},
		{
			ExternalID:  "holding:2:uint16",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "Power (register 2)",
		},
		{
			ExternalID:  "holding:3:int16",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1,
			Description: "Temperature (register 3)",
		},
	}
}

// parseMetricType converts a string metric type name to protocol.MetricType.
func parseMetricType(name string) (protocol.MetricType, bool) {
	metricMap := map[string]protocol.MetricType{
		"VOLTAGE":            protocol.MetricVoltage,
		"CURRENT":            protocol.MetricCurrent,
		"POWER":              protocol.MetricPower,
		"TEMPERATURE":        protocol.MetricTemperature,
		"HUMIDITY":           protocol.MetricHumidity,
		"BATTERY_SOC":        protocol.MetricBatterySOC,
		"BATTERY_DOD":        protocol.MetricBatteryDOD,
		"BATTERY_TEMP":       protocol.MetricBatteryTemp,
		"UTILITY_VOLTAGE_L1": protocol.MetricUtilityVoltageL1,
		"UTILITY_VOLTAGE_L2": protocol.MetricUtilityVoltageL2,
		"UTILITY_VOLTAGE_L3": protocol.MetricUtilityVoltageL3,
		"POWER_FACTOR":       protocol.MetricPowerFactor,
		"GENERATOR_RUNTIME":  protocol.MetricGeneratorRuntime,
		"SITE_POWER_KWH":     protocol.MetricSitePowerKWH,
		"FAN_SPEED":          protocol.MetricFanSpeed,
		"UPTIME":             protocol.MetricUptime,
	}

	mt, ok := metricMap[name]
	return mt, ok
}
