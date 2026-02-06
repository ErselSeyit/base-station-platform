// Package netconf provides YANG path mappings for various device types.
package netconf

import (
	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// buildPathMappings creates YANG path mappings based on device type.
func buildPathMappings(deviceType string, customPaths []PathMapping) []types.MetricMapping {
	var mappings []types.MetricMapping

	// Add device-specific mappings
	switch deviceType {
	case "ericsson":
		mappings = append(mappings, ericssonMappings()...)
	case "nokia":
		mappings = append(mappings, nokiaMappings()...)
	case "huawei":
		mappings = append(mappings, huaweiMappings()...)
	case "generic", "":
		mappings = append(mappings, genericMappings()...)
	}

	// Add custom mappings
	for _, custom := range customPaths {
		metricType, ok := parseMetricType(custom.MetricType)
		if !ok {
			continue
		}

		scale := custom.Scale
		if scale == 0 {
			scale = 1.0
		}

		mappings = append(mappings, types.MetricMapping{
			ExternalID:  custom.XPath,
			MetricType:  metricType,
			Scale:       scale,
			Offset:      custom.Offset,
			Description: custom.Description,
		})
	}

	return mappings
}

// genericMappings returns YANG paths based on common/standard models.
// Uses IETF and OpenConfig models where applicable.
func genericMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// System information (ietf-system)
		{
			ExternalID:  "/system-state/platform/os-version",
			MetricType:  protocol.MetricSoftwareVersion,
			Scale:       1.0,
			Description: "System software version",
		},

		// Interface statistics (ietf-interfaces)
		{
			ExternalID:  "/interfaces-state/interface[name='eth0']/statistics/in-octets",
			MetricType:  protocol.MetricEthUtil,
			Scale:       1.0,
			Description: "Interface input bytes",
		},
		{
			ExternalID:  "/interfaces-state/interface[name='eth0']/statistics/out-octets",
			MetricType:  protocol.MetricEthUtil,
			Scale:       1.0,
			Description: "Interface output bytes",
		},
		{
			ExternalID:  "/interfaces-state/interface[name='eth0']/statistics/in-errors",
			MetricType:  protocol.MetricEthErrors,
			Scale:       1.0,
			Description: "Interface input errors",
		},
		{
			ExternalID:  "/interfaces-state/interface[name='eth0']/statistics/out-errors",
			MetricType:  protocol.MetricEthErrors,
			Scale:       1.0,
			Description: "Interface output errors",
		},

		// Hardware state (ietf-hardware)
		{
			ExternalID:  "/hardware/component[name='cpu']/sensor-data/value",
			MetricType:  protocol.MetricCPUUsage,
			Scale:       1.0,
			Description: "CPU usage percentage",
		},
		{
			ExternalID:  "/hardware/component[name='memory']/sensor-data/value",
			MetricType:  protocol.MetricMemoryUsage,
			Scale:       1.0,
			Description: "Memory usage percentage",
		},
		{
			ExternalID:  "/hardware/component[name='temperature-sensor']/sensor-data/value",
			MetricType:  protocol.MetricTemperature,
			Scale:       0.1, // Often reported in tenths of degrees
			Description: "Temperature sensor reading",
		},

		// Power metrics (common patterns)
		{
			ExternalID:  "/hardware/component[class='power-supply']/sensor-data/value",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.001, // millivolts to volts
			Description: "Power supply voltage",
		},
	}
}

// ericssonMappings returns YANG paths for Ericsson equipment.
// Based on Ericsson's YANG models for radio equipment.
func ericssonMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// Radio Unit metrics
		{
			ExternalID:  "/ericsson-rme:radio-equipment/ru/tx-power",
			MetricType:  protocol.MetricTxPower,
			Scale:       0.1, // dBm * 10
			Description: "Ericsson RU transmit power",
		},
		{
			ExternalID:  "/ericsson-rme:radio-equipment/ru/vswr",
			MetricType:  protocol.MetricVSWR,
			Scale:       0.01,
			Description: "Ericsson RU VSWR",
		},
		{
			ExternalID:  "/ericsson-rme:radio-equipment/ru/temperature",
			MetricType:  protocol.MetricTemperature,
			Scale:       1.0,
			Description: "Ericsson RU temperature",
		},
		{
			ExternalID:  "/ericsson-rme:radio-equipment/ru/pa-power-consumption",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "Ericsson PA power consumption",
		},

		// Baseband metrics
		{
			ExternalID:  "/ericsson-rme:baseband/cpu-load",
			MetricType:  protocol.MetricCPUUsage,
			Scale:       1.0,
			Description: "Ericsson BB CPU load",
		},
		{
			ExternalID:  "/ericsson-rme:baseband/memory-utilization",
			MetricType:  protocol.MetricMemoryUsage,
			Scale:       1.0,
			Description: "Ericsson BB memory utilization",
		},

		// Transport metrics
		{
			ExternalID:  "/ericsson-transport:transport/fronthaul/latency",
			MetricType:  protocol.MetricEthLatency,
			Scale:       0.001, // microseconds to milliseconds
			Description: "Ericsson fronthaul latency",
		},

		// Cell metrics (simplified)
		{
			ExternalID:  "/ericsson-cell:nr-cell/prb-utilization-dl",
			MetricType:  protocol.MetricPRBUsage,
			Scale:       1.0,
			Description: "Ericsson NR cell DL PRB utilization",
		},
		{
			ExternalID:  "/ericsson-cell:nr-cell/prb-utilization-ul",
			MetricType:  protocol.MetricPRBUsage,
			Scale:       1.0,
			Description: "Ericsson NR cell UL PRB utilization",
		},
		{
			ExternalID:  "/ericsson-cell:nr-cell/active-ue-count",
			MetricType:  protocol.MetricActiveUsers,
			Scale:       1.0,
			Description: "Ericsson NR cell active UE count",
		},
	}
}

// nokiaMappings returns YANG paths for Nokia equipment.
func nokiaMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// AirScale Radio metrics
		{
			ExternalID:  "/nokia-radio:radio/rf-module/tx-power",
			MetricType:  protocol.MetricTxPower,
			Scale:       0.1,
			Description: "Nokia RF module TX power",
		},
		{
			ExternalID:  "/nokia-radio:radio/rf-module/vswr-ratio",
			MetricType:  protocol.MetricVSWR,
			Scale:       0.01,
			Description: "Nokia RF module VSWR",
		},
		{
			ExternalID:  "/nokia-radio:radio/rf-module/internal-temperature",
			MetricType:  protocol.MetricTemperature,
			Scale:       1.0,
			Description: "Nokia RF module temperature",
		},

		// System metrics
		{
			ExternalID:  "/nokia-system:system/resources/cpu-usage",
			MetricType:  protocol.MetricCPUUsage,
			Scale:       1.0,
			Description: "Nokia system CPU usage",
		},
		{
			ExternalID:  "/nokia-system:system/resources/memory-usage",
			MetricType:  protocol.MetricMemoryUsage,
			Scale:       1.0,
			Description: "Nokia system memory usage",
		},

		// Power metrics
		{
			ExternalID:  "/nokia-power:power/input-voltage",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.001,
			Description: "Nokia input voltage",
		},
		{
			ExternalID:  "/nokia-power:power/power-consumption",
			MetricType:  protocol.MetricPower,
			Scale:       1.0,
			Description: "Nokia power consumption",
		},

		// Cell metrics
		{
			ExternalID:  "/nokia-cell:cell/prb-utilization",
			MetricType:  protocol.MetricPRBUsage,
			Scale:       1.0,
			Description: "Nokia cell PRB utilization",
		},
		{
			ExternalID:  "/nokia-cell:cell/connected-ue-count",
			MetricType:  protocol.MetricActiveUsers,
			Scale:       1.0,
			Description: "Nokia cell connected UEs",
		},
		{
			ExternalID:  "/nokia-cell:cell/throughput-dl",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       0.000001, // bps to Mbps
			Description: "Nokia cell DL throughput",
		},
	}
}

// huaweiMappings returns YANG paths for Huawei equipment.
func huaweiMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// AAU (Active Antenna Unit) metrics
		{
			ExternalID:  "/huawei-aau:aau/rf-channel/output-power",
			MetricType:  protocol.MetricTxPower,
			Scale:       0.1,
			Description: "Huawei AAU output power",
		},
		{
			ExternalID:  "/huawei-aau:aau/rf-channel/vswr",
			MetricType:  protocol.MetricVSWR,
			Scale:       0.01,
			Description: "Huawei AAU VSWR",
		},
		{
			ExternalID:  "/huawei-aau:aau/temperature",
			MetricType:  protocol.MetricTemperature,
			Scale:       1.0,
			Description: "Huawei AAU temperature",
		},

		// BBU (Baseband Unit) metrics
		{
			ExternalID:  "/huawei-bbu:bbu/board/cpu-usage",
			MetricType:  protocol.MetricCPUUsage,
			Scale:       1.0,
			Description: "Huawei BBU CPU usage",
		},
		{
			ExternalID:  "/huawei-bbu:bbu/board/memory-usage",
			MetricType:  protocol.MetricMemoryUsage,
			Scale:       1.0,
			Description: "Huawei BBU memory usage",
		},

		// Power metrics
		{
			ExternalID:  "/huawei-power:power-system/voltage",
			MetricType:  protocol.MetricVoltage,
			Scale:       0.01,
			Description: "Huawei power system voltage",
		},
		{
			ExternalID:  "/huawei-power:power-system/current",
			MetricType:  protocol.MetricCurrent,
			Scale:       0.01,
			Description: "Huawei power system current",
		},

		// Cell metrics
		{
			ExternalID:  "/huawei-cell:nr-cell/prb-usage-rate",
			MetricType:  protocol.MetricPRBUsage,
			Scale:       1.0,
			Description: "Huawei NR cell PRB usage",
		},
		{
			ExternalID:  "/huawei-cell:nr-cell/max-ue-number",
			MetricType:  protocol.MetricMaxUsers,
			Scale:       1.0,
			Description: "Huawei NR cell max UE number",
		},
		{
			ExternalID:  "/huawei-cell:nr-cell/active-ue-number",
			MetricType:  protocol.MetricActiveUsers,
			Scale:       1.0,
			Description: "Huawei NR cell active UE number",
		},

		// Transport metrics
		{
			ExternalID:  "/huawei-transport:transport/fronthaul/delay",
			MetricType:  protocol.MetricEthLatency,
			Scale:       0.001,
			Description: "Huawei fronthaul delay",
		},
	}
}

// parseMetricType converts a string metric type name to protocol.MetricType.
func parseMetricType(name string) (protocol.MetricType, bool) {
	metricMap := map[string]protocol.MetricType{
		"RSSI":              protocol.MetricRSSI,
		"RSRP":              protocol.MetricRSRP,
		"RSRQ":              protocol.MetricRSRQ,
		"SINR":              protocol.MetricSINR,
		"TX_POWER":          protocol.MetricTxPower,
		"RX_POWER":          protocol.MetricRxPower,
		"VSWR":              protocol.MetricVSWR,
		"TEMPERATURE":       protocol.MetricTemperature,
		"HUMIDITY":          protocol.MetricHumidity,
		"VOLTAGE":           protocol.MetricVoltage,
		"CURRENT":           protocol.MetricCurrent,
		"POWER":             protocol.MetricPower,
		"FAN_SPEED":         protocol.MetricFanSpeed,
		"CPU_USAGE":         protocol.MetricCPUUsage,
		"MEMORY_USAGE":      protocol.MetricMemoryUsage,
		"DISK_USAGE":        protocol.MetricDiskUsage,
		"PRB_USAGE":         protocol.MetricPRBUsage,
		"ACTIVE_USERS":      protocol.MetricActiveUsers,
		"MAX_USERS":         protocol.MetricMaxUsers,
		"HANDOVER_SUCCESS":  protocol.MetricHandoverSuccess,
		"HANDOVER_FAIL":     protocol.MetricHandoverFail,
		"CALL_DROP":         protocol.MetricCallDrop,
		"UPTIME":            protocol.MetricUptime,
		"ETH_UTIL":          protocol.MetricEthUtil,
		"ETH_ERRORS":        protocol.MetricEthErrors,
		"ETH_LATENCY":       protocol.MetricEthLatency,
		"GPS_SATELLITES":    protocol.MetricGPSSatellites,
		"FIBER_RX_POWER":    protocol.MetricFiberRxPower,
		"FIBER_TX_POWER":    protocol.MetricFiberTxPower,
		"FIBER_BER":         protocol.MetricFiberBER,
		"FIBER_OSNR":        protocol.MetricFiberOSNR,
		"BATTERY_SOC":       protocol.MetricBatterySOC,
		"GTP_THROUGHPUT":    protocol.MetricGTPThroughput,
		"SOFTWARE_VERSION":  protocol.MetricSoftwareVersion,
	}

	mt, ok := metricMap[name]
	return mt, ok
}
