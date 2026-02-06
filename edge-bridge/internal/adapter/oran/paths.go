// Package oran provides YANG path mappings for O-RAN components.
// Based on O-RAN Alliance specifications for O1 interface.
package oran

import (
	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// buildPathMappings creates YANG path mappings based on O-RAN component type.
func buildPathMappings(componentType string, customPaths []PathMapping) []types.MetricMapping {
	var mappings []types.MetricMapping

	// Add component-specific mappings
	switch componentType {
	case "o-ru":
		mappings = append(mappings, oruMappings()...)
	case "o-du":
		mappings = append(mappings, oduMappings()...)
	case "o-cu":
		mappings = append(mappings, ocuMappings()...)
	case "smo":
		mappings = append(mappings, smoMappings()...)
	default:
		mappings = append(mappings, oruMappings()...) // Default to O-RU
	}

	// Add common O-RAN mappings
	mappings = append(mappings, commonORANMappings()...)

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

// commonORANMappings returns YANG paths common to all O-RAN components.
// Based on O-RAN.WG4.MP.0 (Management Plane) specifications.
func commonORANMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// o-ran-hardware (hardware inventory and monitoring)
		{
			ExternalID:  "/o-ran-hardware:hardware/component[name='chassis']/state/temperature",
			MetricType:  protocol.MetricTemperature,
			Scale:       1.0,
			Description: "O-RAN component temperature",
		},

		// o-ran-software-management
		{
			ExternalID:  "/o-ran-software-management:software-inventory/software-slot/running",
			MetricType:  protocol.MetricSoftwareVersion,
			Scale:       1.0,
			Description: "O-RAN running software version",
		},

		// ietf-system (common system state)
		{
			ExternalID:  "/ietf-system:system-state/platform/os-version",
			MetricType:  protocol.MetricSoftwareVersion,
			Scale:       1.0,
			Description: "System OS version",
		},

		// o-ran-operations (operational state)
		{
			ExternalID:  "/o-ran-operations:operational-state",
			MetricType:  protocol.MetricUptime,
			Scale:       1.0,
			Description: "O-RAN operational state",
		},

		// o-ran-sync (synchronization status)
		{
			ExternalID:  "/o-ran-sync:sync/ptp-status/lock-state",
			MetricType:  protocol.MetricPTPOffset,
			Scale:       1.0,
			Description: "PTP synchronization state",
		},
		{
			ExternalID:  "/o-ran-sync:sync/sync-status/sync-state",
			MetricType:  protocol.MetricGPSSatellites,
			Scale:       1.0,
			Description: "Synchronization state",
		},
	}
}

// oruMappings returns YANG paths for O-RAN Radio Unit (O-RU).
// Based on O-RAN.WG4.CUS.0 (Control, User and Synchronization Plane).
func oruMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// o-ran-uplane-conf (user plane configuration)
		{
			ExternalID:  "/o-ran-uplane-conf:user-plane-configuration/tx-array-carriers/tx-array-carrier/gain",
			MetricType:  protocol.MetricTxPower,
			Scale:       0.1, // dB * 10
			Description: "O-RU TX array carrier gain",
		},
		{
			ExternalID:  "/o-ran-uplane-conf:user-plane-configuration/rx-array-carriers/rx-array-carrier/gain-correction",
			MetricType:  protocol.MetricRxPower,
			Scale:       0.1,
			Description: "O-RU RX array carrier gain correction",
		},

		// o-ran-module-cap (module capabilities and measurements)
		{
			ExternalID:  "/o-ran-module-cap:module-capability/ru-capabilities/number-of-ru-ports",
			MetricType:  protocol.MetricActiveUsers, // Repurposed for port count
			Scale:       1.0,
			Description: "O-RU number of ports",
		},

		// o-ran-transceiver (optical transceiver monitoring)
		{
			ExternalID:  "/o-ran-transceiver:port-transceivers/port-transceiver/rx-power",
			MetricType:  protocol.MetricFiberRxPower,
			Scale:       0.01, // dBm * 100
			Description: "O-RU fronthaul RX optical power",
		},
		{
			ExternalID:  "/o-ran-transceiver:port-transceivers/port-transceiver/tx-power",
			MetricType:  protocol.MetricFiberTxPower,
			Scale:       0.01,
			Description: "O-RU fronthaul TX optical power",
		},
		{
			ExternalID:  "/o-ran-transceiver:port-transceivers/port-transceiver/temperature",
			MetricType:  protocol.MetricTemperature,
			Scale:       1.0,
			Description: "O-RU transceiver temperature",
		},

		// o-ran-fan (fan monitoring)
		{
			ExternalID:  "/o-ran-fan:fan-tray/fan/fan-speed",
			MetricType:  protocol.MetricFanSpeed,
			Scale:       1.0, // RPM
			Description: "O-RU fan speed",
		},

		// o-ran-ald (antenna line devices)
		{
			ExternalID:  "/o-ran-ald:ald-ports-io/ald-port/vswr",
			MetricType:  protocol.MetricVSWR,
			Scale:       0.001, // VSWR * 1000
			Description: "O-RU antenna VSWR",
		},

		// o-ran-performance-management
		{
			ExternalID:  "/o-ran-performance-management:performance-measurement-objects/rx-window-measurement/rx-on-time",
			MetricType:  protocol.MetricEthUtil,
			Scale:       1.0,
			Description: "O-RU fronthaul RX on-time",
		},
		{
			ExternalID:  "/o-ran-performance-management:performance-measurement-objects/tx-measurement/tx-total",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       1.0,
			Description: "O-RU TX total packets",
		},

		// o-ran-delay-management
		{
			ExternalID:  "/o-ran-delay-management:delay-management/bandwidth-scs-delay-state/ru-delay-profile",
			MetricType:  protocol.MetricEthLatency,
			Scale:       0.001, // nanoseconds to microseconds
			Description: "O-RU processing delay",
		},
	}
}

// oduMappings returns YANG paths for O-RAN Distributed Unit (O-DU).
func oduMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// Resource utilization
		{
			ExternalID:  "/o-ran-du:distributed-unit/resources/cpu-utilization",
			MetricType:  protocol.MetricCPUUsage,
			Scale:       1.0,
			Description: "O-DU CPU utilization",
		},
		{
			ExternalID:  "/o-ran-du:distributed-unit/resources/memory-utilization",
			MetricType:  protocol.MetricMemoryUsage,
			Scale:       1.0,
			Description: "O-DU memory utilization",
		},

		// Cell metrics
		{
			ExternalID:  "/o-ran-du:distributed-unit/cells/nr-cell/prb-utilization-dl",
			MetricType:  protocol.MetricPRBUsage,
			Scale:       1.0,
			Description: "O-DU NR cell DL PRB utilization",
		},
		{
			ExternalID:  "/o-ran-du:distributed-unit/cells/nr-cell/prb-utilization-ul",
			MetricType:  protocol.MetricPRBUsage,
			Scale:       1.0,
			Description: "O-DU NR cell UL PRB utilization",
		},
		{
			ExternalID:  "/o-ran-du:distributed-unit/cells/nr-cell/active-ue-count",
			MetricType:  protocol.MetricActiveUsers,
			Scale:       1.0,
			Description: "O-DU active UE count",
		},

		// Fronthaul interface
		{
			ExternalID:  "/o-ran-du:distributed-unit/fronthaul/interface/rx-bytes",
			MetricType:  protocol.MetricEthUtil,
			Scale:       1.0,
			Description: "O-DU fronthaul RX bytes",
		},
		{
			ExternalID:  "/o-ran-du:distributed-unit/fronthaul/interface/tx-bytes",
			MetricType:  protocol.MetricEthUtil,
			Scale:       1.0,
			Description: "O-DU fronthaul TX bytes",
		},

		// RLC/MAC statistics
		{
			ExternalID:  "/o-ran-du:distributed-unit/mac/throughput-dl",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       0.000001, // bps to Mbps
			Description: "O-DU MAC DL throughput",
		},
		{
			ExternalID:  "/o-ran-du:distributed-unit/mac/throughput-ul",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       0.000001,
			Description: "O-DU MAC UL throughput",
		},
	}
}

// ocuMappings returns YANG paths for O-RAN Central Unit (O-CU).
func ocuMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// Resource utilization
		{
			ExternalID:  "/o-ran-cu:central-unit/resources/cpu-utilization",
			MetricType:  protocol.MetricCPUUsage,
			Scale:       1.0,
			Description: "O-CU CPU utilization",
		},
		{
			ExternalID:  "/o-ran-cu:central-unit/resources/memory-utilization",
			MetricType:  protocol.MetricMemoryUsage,
			Scale:       1.0,
			Description: "O-CU memory utilization",
		},

		// PDCP statistics
		{
			ExternalID:  "/o-ran-cu:central-unit/pdcp/throughput-dl",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       0.000001,
			Description: "O-CU PDCP DL throughput",
		},
		{
			ExternalID:  "/o-ran-cu:central-unit/pdcp/throughput-ul",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       0.000001,
			Description: "O-CU PDCP UL throughput",
		},
		{
			ExternalID:  "/o-ran-cu:central-unit/pdcp/packet-loss-rate",
			MetricType:  protocol.MetricCallDrop, // Repurposed for packet loss
			Scale:       100.0,                   // Convert to percentage
			Description: "O-CU PDCP packet loss rate",
		},

		// RRC statistics
		{
			ExternalID:  "/o-ran-cu:central-unit/rrc/connected-ue-count",
			MetricType:  protocol.MetricActiveUsers,
			Scale:       1.0,
			Description: "O-CU RRC connected UEs",
		},
		{
			ExternalID:  "/o-ran-cu:central-unit/rrc/setup-success-rate",
			MetricType:  protocol.MetricRRCSuccess,
			Scale:       100.0,
			Description: "O-CU RRC setup success rate",
		},

		// F1 interface (to O-DU)
		{
			ExternalID:  "/o-ran-cu:central-unit/f1/latency",
			MetricType:  protocol.MetricPacketDelay,
			Scale:       0.001, // microseconds to milliseconds
			Description: "O-CU F1 interface latency",
		},

		// E1 interface (CU-CP to CU-UP)
		{
			ExternalID:  "/o-ran-cu:central-unit/e1/throughput",
			MetricType:  protocol.MetricGTPThroughput,
			Scale:       0.000001,
			Description: "O-CU E1 interface throughput",
		},
	}
}

// smoMappings returns YANG paths for Service Management and Orchestration (SMO).
func smoMappings() []types.MetricMapping {
	return []types.MetricMapping{
		// Overall network metrics
		{
			ExternalID:  "/o-ran-smo:smo/network/total-ru-count",
			MetricType:  protocol.MetricActiveUsers, // Repurposed for RU count
			Scale:       1.0,
			Description: "SMO total O-RU count",
		},
		{
			ExternalID:  "/o-ran-smo:smo/network/total-du-count",
			MetricType:  protocol.MetricMaxUsers, // Repurposed for DU count
			Scale:       1.0,
			Description: "SMO total O-DU count",
		},

		// Alarm statistics
		{
			ExternalID:  "/o-ran-smo:smo/alarms/critical-count",
			MetricType:  protocol.MetricCallDrop, // Repurposed for alarm count
			Scale:       1.0,
			Description: "SMO critical alarm count",
		},
		{
			ExternalID:  "/o-ran-smo:smo/alarms/major-count",
			MetricType:  protocol.MetricHandoverFail, // Repurposed
			Scale:       1.0,
			Description: "SMO major alarm count",
		},
	}
}

// parseMetricType converts a string metric type name to protocol.MetricType.
func parseMetricType(name string) (protocol.MetricType, bool) {
	metricMap := map[string]protocol.MetricType{
		"TX_POWER":          protocol.MetricTxPower,
		"RX_POWER":          protocol.MetricRxPower,
		"VSWR":              protocol.MetricVSWR,
		"TEMPERATURE":       protocol.MetricTemperature,
		"FAN_SPEED":         protocol.MetricFanSpeed,
		"CPU_USAGE":         protocol.MetricCPUUsage,
		"MEMORY_USAGE":      protocol.MetricMemoryUsage,
		"PRB_USAGE":         protocol.MetricPRBUsage,
		"ACTIVE_USERS":      protocol.MetricActiveUsers,
		"GTP_THROUGHPUT":    protocol.MetricGTPThroughput,
		"PACKET_DELAY":      protocol.MetricPacketDelay,
		"RRC_SUCCESS":       protocol.MetricRRCSuccess,
		"FIBER_RX_POWER":    protocol.MetricFiberRxPower,
		"FIBER_TX_POWER":    protocol.MetricFiberTxPower,
		"ETH_UTIL":          protocol.MetricEthUtil,
		"ETH_LATENCY":       protocol.MetricEthLatency,
		"PTP_OFFSET":        protocol.MetricPTPOffset,
		"GPS_SATELLITES":    protocol.MetricGPSSatellites,
		"UPTIME":            protocol.MetricUptime,
		"SOFTWARE_VERSION":  protocol.MetricSoftwareVersion,
	}

	mt, ok := metricMap[name]
	return mt, ok
}
