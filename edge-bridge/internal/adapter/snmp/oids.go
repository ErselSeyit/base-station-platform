package snmp

import (
	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// Standard MIB OIDs
var standardOIDs = map[string]types.MetricMapping{
	// UPS-MIB (RFC 1628)
	".1.3.6.1.2.1.33.1.2.1.0": {
		ExternalID:  ".1.3.6.1.2.1.33.1.2.1.0",
		MetricType:  protocol.MetricBatteryLevel, // upsBatteryStatus
		Scale:       1.0,
		Description: "UPS Battery Status",
	},
	".1.3.6.1.2.1.33.1.2.3.0": {
		ExternalID:  ".1.3.6.1.2.1.33.1.2.3.0",
		MetricType:  protocol.MetricBatterySOC, // upsEstimatedChargeRemaining
		Scale:       1.0,
		Description: "UPS Estimated Charge Remaining (%)",
	},
	".1.3.6.1.2.1.33.1.2.5.0": {
		ExternalID:  ".1.3.6.1.2.1.33.1.2.5.0",
		MetricType:  protocol.MetricVoltage, // upsBatteryVoltage (0.1V units)
		Scale:       0.1,
		Description: "UPS Battery Voltage",
	},
	".1.3.6.1.2.1.33.1.2.7.0": {
		ExternalID:  ".1.3.6.1.2.1.33.1.2.7.0",
		MetricType:  protocol.MetricTemperature, // upsBatteryTemperature
		Scale:       1.0,
		Description: "UPS Battery Temperature (°C)",
	},
	".1.3.6.1.2.1.33.1.4.4.1.2.1": {
		ExternalID:  ".1.3.6.1.2.1.33.1.4.4.1.2.1",
		MetricType:  protocol.MetricUtilityVoltageL1, // upsOutputVoltage (RMS volts)
		Scale:       1.0,
		Description: "UPS Output Voltage L1",
	},
	".1.3.6.1.2.1.33.1.4.4.1.3.1": {
		ExternalID:  ".1.3.6.1.2.1.33.1.4.4.1.3.1",
		MetricType:  protocol.MetricCurrent, // upsOutputCurrent (0.1A)
		Scale:       0.1,
		Description: "UPS Output Current",
	},
	".1.3.6.1.2.1.33.1.4.4.1.4.1": {
		ExternalID:  ".1.3.6.1.2.1.33.1.4.4.1.4.1",
		MetricType:  protocol.MetricPowerConsumption, // upsOutputPower (watts)
		Scale:       1.0,
		Description: "UPS Output Power (W)",
	},

	// HOST-RESOURCES-MIB
	".1.3.6.1.2.1.25.3.3.1.2.1": {
		ExternalID:  ".1.3.6.1.2.1.25.3.3.1.2.1",
		MetricType:  protocol.MetricCPUUsage, // hrProcessorLoad
		Scale:       1.0,
		Description: "CPU Load (%)",
	},

	// IF-MIB
	".1.3.6.1.2.1.2.2.1.10.1": {
		ExternalID:  ".1.3.6.1.2.1.2.2.1.10.1",
		MetricType:  protocol.MetricDataThroughput, // ifInOctets
		Scale:       8.0 / 1000000.0,               // Convert bytes to Mbps
		Description: "Interface In Throughput (Mbps)",
	},
	".1.3.6.1.2.1.2.2.1.14.1": {
		ExternalID:  ".1.3.6.1.2.1.2.2.1.14.1",
		MetricType:  protocol.MetricEthErrors, // ifInErrors
		Scale:       1.0,
		Description: "Interface In Errors",
	},
}

// Vertiv/Liebert UPS OIDs
var vertivOIDs = map[string]types.MetricMapping{
	".1.3.6.1.4.1.476.1.42.3.5.2.1.0": {
		ExternalID:  ".1.3.6.1.4.1.476.1.42.3.5.2.1.0",
		MetricType:  protocol.MetricUtilityVoltageL1,
		Scale:       0.1,
		Description: "Vertiv Input Voltage L1",
	},
	".1.3.6.1.4.1.476.1.42.3.5.2.2.0": {
		ExternalID:  ".1.3.6.1.4.1.476.1.42.3.5.2.2.0",
		MetricType:  protocol.MetricUtilityVoltageL2,
		Scale:       0.1,
		Description: "Vertiv Input Voltage L2",
	},
	".1.3.6.1.4.1.476.1.42.3.5.2.3.0": {
		ExternalID:  ".1.3.6.1.4.1.476.1.42.3.5.2.3.0",
		MetricType:  protocol.MetricUtilityVoltageL3,
		Scale:       0.1,
		Description: "Vertiv Input Voltage L3",
	},
	".1.3.6.1.4.1.476.1.42.3.5.1.1.0": {
		ExternalID:  ".1.3.6.1.4.1.476.1.42.3.5.1.1.0",
		MetricType:  protocol.MetricBatterySOC,
		Scale:       1.0,
		Description: "Vertiv Battery Capacity (%)",
	},
	".1.3.6.1.4.1.476.1.42.3.5.1.2.0": {
		ExternalID:  ".1.3.6.1.4.1.476.1.42.3.5.1.2.0",
		MetricType:  protocol.MetricBatteryCellTempMax,
		Scale:       0.1,
		Description: "Vertiv Battery Temperature",
	},
}

// Schneider/APC UPS OIDs
var schneiderOIDs = map[string]types.MetricMapping{
	".1.3.6.1.4.1.318.1.1.1.3.2.1.0": {
		ExternalID:  ".1.3.6.1.4.1.318.1.1.1.3.2.1.0",
		MetricType:  protocol.MetricUtilityVoltageL1,
		Scale:       1.0,
		Description: "APC Input Voltage",
	},
	".1.3.6.1.4.1.318.1.1.1.4.2.1.0": {
		ExternalID:  ".1.3.6.1.4.1.318.1.1.1.4.2.1.0",
		MetricType:  protocol.MetricVoltage,
		Scale:       1.0,
		Description: "APC Output Voltage",
	},
	".1.3.6.1.4.1.318.1.1.1.4.2.3.0": {
		ExternalID:  ".1.3.6.1.4.1.318.1.1.1.4.2.3.0",
		MetricType:  protocol.MetricPowerConsumption,
		Scale:       1.0,
		Description: "APC Output Load (%)",
	},
	".1.3.6.1.4.1.318.1.1.1.2.2.1.0": {
		ExternalID:  ".1.3.6.1.4.1.318.1.1.1.2.2.1.0",
		MetricType:  protocol.MetricBatterySOC,
		Scale:       1.0,
		Description: "APC Battery Capacity (%)",
	},
	".1.3.6.1.4.1.318.1.1.1.2.2.3.0": {
		ExternalID:  ".1.3.6.1.4.1.318.1.1.1.2.2.3.0",
		MetricType:  protocol.MetricGeneratorRuntime, // Runtime remaining in minutes
		Scale:       1.0,
		Description: "APC Battery Runtime Remaining (min)",
	},
	".1.3.6.1.4.1.318.1.1.1.2.2.2.0": {
		ExternalID:  ".1.3.6.1.4.1.318.1.1.1.2.2.2.0",
		MetricType:  protocol.MetricBatteryCellTempMax,
		Scale:       1.0,
		Description: "APC Battery Temperature (°C)",
	},
}

// Ericsson RBS OIDs (example - actual OIDs may vary)
var ericssonOIDs = map[string]types.MetricMapping{
	".1.3.6.1.4.1.193.177.2.2.1.3.1.1.5.1": {
		ExternalID:  ".1.3.6.1.4.1.193.177.2.2.1.3.1.1.5.1",
		MetricType:  protocol.MetricTemperature,
		Scale:       0.1,
		Description: "Ericsson RRU Temperature",
	},
	".1.3.6.1.4.1.193.177.2.2.1.3.1.1.6.1": {
		ExternalID:  ".1.3.6.1.4.1.193.177.2.2.1.3.1.1.6.1",
		MetricType:  protocol.MetricVSWR,
		Scale:       0.01,
		Description: "Ericsson VSWR",
	},
	".1.3.6.1.4.1.193.177.2.2.1.3.1.1.7.1": {
		ExternalID:  ".1.3.6.1.4.1.193.177.2.2.1.3.1.1.7.1",
		MetricType:  protocol.MetricPowerConsumption,
		Scale:       0.1,
		Description: "Ericsson TX Power (dBm)",
	},
}

// Nokia BTS OIDs (example - actual OIDs may vary)
var nokiaOIDs = map[string]types.MetricMapping{
	".1.3.6.1.4.1.94.1.21.1.7.1.3.1": {
		ExternalID:  ".1.3.6.1.4.1.94.1.21.1.7.1.3.1",
		MetricType:  protocol.MetricTemperature,
		Scale:       1.0,
		Description: "Nokia BTS Temperature",
	},
	".1.3.6.1.4.1.94.1.21.1.7.1.4.1": {
		ExternalID:  ".1.3.6.1.4.1.94.1.21.1.7.1.4.1",
		MetricType:  protocol.MetricPowerConsumption,
		Scale:       0.1,
		Description: "Nokia TX Power",
	},
}

// Environment sensor OIDs (generic)
var environmentOIDs = map[string]types.MetricMapping{
	// Temperature/Humidity sensors (common enterprise OIDs)
	".1.3.6.1.4.1.3854.1.2.2.1.16.1.3.0": {
		ExternalID:  ".1.3.6.1.4.1.3854.1.2.2.1.16.1.3.0",
		MetricType:  protocol.MetricTemperature,
		Scale:       0.01,
		Description: "Environment Temperature",
	},
	".1.3.6.1.4.1.3854.1.2.2.1.17.1.3.0": {
		ExternalID:  ".1.3.6.1.4.1.3854.1.2.2.1.17.1.3.0",
		MetricType:  protocol.MetricHumidity,
		Scale:       0.01,
		Description: "Environment Humidity",
	},
}

// SFP/Fiber OIDs (common transceiver monitoring)
var fiberOIDs = map[string]types.MetricMapping{
	// IF-MIB extensions for SFP
	".1.3.6.1.4.1.2636.3.60.1.1.1.1.5": {
		ExternalID:  ".1.3.6.1.4.1.2636.3.60.1.1.1.1.5",
		MetricType:  protocol.MetricFiberRxPower,
		Scale:       0.01,
		Description: "Fiber Rx Power (dBm)",
	},
	".1.3.6.1.4.1.2636.3.60.1.1.1.1.6": {
		ExternalID:  ".1.3.6.1.4.1.2636.3.60.1.1.1.1.6",
		MetricType:  protocol.MetricFiberTxPower,
		Scale:       0.01,
		Description: "Fiber Tx Power (dBm)",
	},
}

// buildOIDMappings creates the OID mapping list based on device type.
func buildOIDMappings(deviceType string, customOIDs []OIDMapping) []types.MetricMapping {
	var mappings []types.MetricMapping

	// Always include standard OIDs
	for _, m := range standardOIDs {
		mappings = append(mappings, m)
	}

	// Add device-specific OIDs
	switch deviceType {
	case "vertiv", "liebert":
		for _, m := range vertivOIDs {
			mappings = append(mappings, m)
		}
	case "schneider", "apc":
		for _, m := range schneiderOIDs {
			mappings = append(mappings, m)
		}
	case "ericsson":
		for _, m := range ericssonOIDs {
			mappings = append(mappings, m)
		}
	case "nokia":
		for _, m := range nokiaOIDs {
			mappings = append(mappings, m)
		}
	case "environment", "sensor":
		for _, m := range environmentOIDs {
			mappings = append(mappings, m)
		}
	case "fiber", "sfp":
		for _, m := range fiberOIDs {
			mappings = append(mappings, m)
		}
	case "all":
		// Include all vendor OIDs
		for _, m := range vertivOIDs {
			mappings = append(mappings, m)
		}
		for _, m := range schneiderOIDs {
			mappings = append(mappings, m)
		}
		for _, m := range ericssonOIDs {
			mappings = append(mappings, m)
		}
		for _, m := range nokiaOIDs {
			mappings = append(mappings, m)
		}
		for _, m := range environmentOIDs {
			mappings = append(mappings, m)
		}
		for _, m := range fiberOIDs {
			mappings = append(mappings, m)
		}
	}

	// Add custom OID mappings
	for _, custom := range customOIDs {
		mt := parseMetricType(custom.MetricType)
		if mt != 0 {
			scale := custom.Scale
			if scale == 0 {
				scale = 1.0
			}
			mappings = append(mappings, types.MetricMapping{
				ExternalID:  custom.OID,
				MetricType:  mt,
				Scale:       scale,
				Offset:      custom.Offset,
				Description: custom.Description,
			})
		}
	}

	return mappings
}

// parseMetricType converts a metric type string to protocol.MetricType.
func parseMetricType(name string) protocol.MetricType {
	// Map common metric type names to protocol constants
	metricTypeMap := map[string]protocol.MetricType{
		"CPU_USAGE":            protocol.MetricCPUUsage,
		"MEMORY_USAGE":         protocol.MetricMemoryUsage,
		"TEMPERATURE":          protocol.MetricTemperature,
		"HUMIDITY":             protocol.MetricHumidity,
		"FAN_SPEED":            protocol.MetricFanSpeed,
		"VOLTAGE":              protocol.MetricVoltage,
		"CURRENT":              protocol.MetricCurrent,
		"POWER_CONSUMPTION":    protocol.MetricPowerConsumption,
		"BATTERY_LEVEL":        protocol.MetricBatteryLevel,
		"BATTERY_SOC":          protocol.MetricBatterySOC,
		"BATTERY_DOD":          protocol.MetricBatteryDOD,
		"UTILITY_VOLTAGE_L1":   protocol.MetricUtilityVoltageL1,
		"UTILITY_VOLTAGE_L2":   protocol.MetricUtilityVoltageL2,
		"UTILITY_VOLTAGE_L3":   protocol.MetricUtilityVoltageL3,
		"POWER_FACTOR":         protocol.MetricPowerFactor,
		"GENERATOR_FUEL_LEVEL": protocol.MetricGeneratorFuelLevel,
		"GENERATOR_RUNTIME":    protocol.MetricGeneratorRuntime,
		"SOLAR_PANEL_VOLTAGE":  protocol.MetricSolarPanelVoltage,
		"SOLAR_CHARGE_CURRENT": protocol.MetricSolarChargeCurrent,
		"SITE_POWER_KWH":       protocol.MetricSitePowerKWH,
		"FIBER_RX_POWER":       protocol.MetricFiberRxPower,
		"FIBER_TX_POWER":       protocol.MetricFiberTxPower,
		"ETH_UTILIZATION":      protocol.MetricEthUtilization,
		"ETH_ERRORS":           protocol.MetricEthErrors,
		"ETH_LATENCY":          protocol.MetricEthLatency,
		"VSWR":                 protocol.MetricVSWR,
		"SIGNAL_STRENGTH":      protocol.MetricSignalStrength,
		"DATA_THROUGHPUT":      protocol.MetricDataThroughput,
	}

	if mt, ok := metricTypeMap[name]; ok {
		return mt
	}
	return 0
}
