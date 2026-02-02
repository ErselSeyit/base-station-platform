package protocol

// Frame format constants
const (
	HeaderByte0   byte = 0xAA
	HeaderByte1   byte = 0x55
	HeaderSize    int  = 6  // Magic(2) + Length(2) + Type(1) + Seq(1)
	CRCSize       int  = 2
	MaxPayloadLen int  = 4096
	MaxFrameSize  int  = HeaderSize + MaxPayloadLen + CRCSize
)

// MessageType defines the protocol message types.
type MessageType byte

// Request message types (0x01-0x07)
const (
	MsgPing            MessageType = 0x01
	MsgRequestMetrics  MessageType = 0x02
	MsgGetStatus       MessageType = 0x03
	MsgSetConfig       MessageType = 0x04
	MsgExecuteCommand  MessageType = 0x05
	MsgStartStream     MessageType = 0x06
	MsgStopStream      MessageType = 0x07
)

// Response message types (0x81-0x86)
const (
	MsgPong            MessageType = 0x81
	MsgMetricsResponse MessageType = 0x82
	MsgStatusResponse  MessageType = 0x83
	MsgConfigAck       MessageType = 0x84
	MsgCommandResult   MessageType = 0x85
	MsgStreamAck       MessageType = 0x86
)

// Event message types (0xA1-0xA4)
const (
	MsgMetricsEvent       MessageType = 0xA1
	MsgThresholdExceeded  MessageType = 0xA2
	MsgDeviceStateChange  MessageType = 0xA3
	MsgError              MessageType = 0xA4
)

// MetricType defines types of metrics.
type MetricType byte

const (
	// System metrics (0x01-0x0F)
	MetricCPUUsage         MetricType = 0x01
	MetricMemoryUsage      MetricType = 0x02
	MetricTemperature      MetricType = 0x03
	MetricHumidity         MetricType = 0x04
	MetricFanSpeed         MetricType = 0x05
	MetricVoltage          MetricType = 0x06
	MetricCurrent          MetricType = 0x07
	MetricPowerConsumption MetricType = 0x08

	// RF metrics (0x10-0x1F)
	MetricSignalStrength   MetricType = 0x10
	MetricSignalQuality    MetricType = 0x11
	MetricInterference     MetricType = 0x12
	MetricBER              MetricType = 0x13
	MetricVSWR             MetricType = 0x14
	MetricAntennaTilt      MetricType = 0x15

	// Performance metrics (0x20-0x2F)
	MetricDataThroughput   MetricType = 0x20
	MetricLatency          MetricType = 0x21
	MetricPacketLoss       MetricType = 0x22
	MetricJitter           MetricType = 0x23
	MetricConnectionCount  MetricType = 0x24

	// Device metrics (0x30-0x3F)
	MetricBatteryLevel     MetricType = 0x30
	MetricUptime           MetricType = 0x31
	MetricErrorCount       MetricType = 0x32

	// 5G NR700 (n28) metrics (0x40-0x4F)
	MetricDLThroughputNR700 MetricType = 0x40
	MetricULThroughputNR700 MetricType = 0x41
	MetricRSRPNR700         MetricType = 0x42
	MetricSINRNR700         MetricType = 0x43

	// 5G NR3500 (n78) metrics (0x50-0x5F)
	MetricDLThroughputNR3500 MetricType = 0x50
	MetricULThroughputNR3500 MetricType = 0x51
	MetricRSRPNR3500         MetricType = 0x52
	MetricSINRNR3500         MetricType = 0x53

	// 5G Radio metrics (0x60-0x6F)
	MetricPDCPThroughput      MetricType = 0x60
	MetricRLCThroughput       MetricType = 0x61
	MetricInitialBLER         MetricType = 0x62
	MetricAvgMCS              MetricType = 0x63
	MetricRBPerSlot           MetricType = 0x64
	MetricRankIndicator       MetricType = 0x65

	// RF Quality metrics (0x70-0x7F)
	MetricTXImbalance         MetricType = 0x70
	MetricLatencyPing         MetricType = 0x71
	MetricHandoverSuccessRate MetricType = 0x72
	MetricInterferenceLevel   MetricType = 0x73

	// Carrier Aggregation metrics (0x78-0x7F)
	MetricCADLThroughput      MetricType = 0x78
	MetricCAULThroughput      MetricType = 0x79

	// ========================================================================
	// Extended Metrics (Phase 2 Enhancement)
	// ========================================================================

	// Power & Energy metrics (0x80-0x8F)
	MetricUtilityVoltageL1   MetricType = 0x80
	MetricUtilityVoltageL2   MetricType = 0x81
	MetricUtilityVoltageL3   MetricType = 0x82
	MetricPowerFactor        MetricType = 0x83
	MetricGeneratorFuelLevel MetricType = 0x84
	MetricGeneratorRuntime   MetricType = 0x85
	MetricBatterySOC         MetricType = 0x86
	MetricBatteryDOD         MetricType = 0x87
	MetricBatteryCellTempMin MetricType = 0x88
	MetricBatteryCellTempMax MetricType = 0x89
	MetricSolarPanelVoltage  MetricType = 0x8A
	MetricSolarChargeCurrent MetricType = 0x8B
	MetricSitePowerKWH       MetricType = 0x8C

	// Environmental & Safety metrics (0x90-0x9F)
	MetricWindSpeed          MetricType = 0x90
	MetricWindDirection      MetricType = 0x91
	MetricPrecipitation      MetricType = 0x92
	MetricLightningDistance  MetricType = 0x93
	MetricTiltAngle          MetricType = 0x94
	MetricVibrationLevel     MetricType = 0x95
	MetricWaterLevel         MetricType = 0x96
	MetricPM25Level          MetricType = 0x97
	MetricSmokeDetected      MetricType = 0x98
	MetricCOLevel            MetricType = 0x99
	MetricDoorStatus         MetricType = 0x9A
	MetricMotionDetected     MetricType = 0x9B

	// Transport/Backhaul metrics (0xA0-0xAF)
	MetricFiberRxPower       MetricType = 0xA0
	MetricFiberTxPower       MetricType = 0xA1
	MetricFiberBER           MetricType = 0xA2
	MetricFiberOSNR          MetricType = 0xA3
	MetricMWRSL              MetricType = 0xA4
	MetricMWSNR              MetricType = 0xA5
	MetricMWModulation       MetricType = 0xA6
	MetricEthUtilization     MetricType = 0xA7
	MetricEthErrors          MetricType = 0xA8
	MetricEthLatency         MetricType = 0xA9
	MetricPTPOffset          MetricType = 0xAA
	MetricGPSSatellites      MetricType = 0xAB

	// Advanced Radio metrics (0xB0-0xBF)
	MetricBeamWeightMag      MetricType = 0xB0
	MetricBeamWeightPhase    MetricType = 0xB1
	MetricPrecodingRank      MetricType = 0xB2
	MetricPIMLevel           MetricType = 0xB3
	MetricCoChannelInterf    MetricType = 0xB4
	MetricOccupiedBandwidth  MetricType = 0xB5
	MetricACLR               MetricType = 0xB6
	MetricGTPThroughput      MetricType = 0xB7
	MetricPacketDelay        MetricType = 0xB8
	MetricRRCSetupSuccess    MetricType = 0xB9
	MetricPagingSuccess      MetricType = 0xBA

	// Network Slicing metrics (0xC0-0xCF) - 5G specific
	MetricSliceThroughput    MetricType = 0xC0
	MetricSliceLatency       MetricType = 0xC1
	MetricSlicePacketLoss    MetricType = 0xC2
	MetricSlicePRBUtil       MetricType = 0xC3
	MetricSliceSLACompliance MetricType = 0xC4

	// Special values
	MetricAll              MetricType = 0xFF
)

// Metric type aliases for adapter compatibility
const (
	// Generic aliases
	MetricPower           = MetricPowerConsumption
	MetricBatteryTemp     = MetricTemperature      // Used for battery temperature readings
	MetricTxPower         = MetricSignalStrength   // TX power mapping
	MetricRxPower         = MetricSignalQuality    // RX power mapping
	MetricEthUtil         = MetricEthUtilization   // Ethernet utilization alias
	MetricDiskUsage       = MetricMemoryUsage      // Storage usage (placeholder)
	MetricActiveUsers     = MetricConnectionCount  // Active user count
	MetricMaxUsers        = MetricConnectionCount  // Max user count (same underlying type)
	MetricPRBUsage        = MetricSlicePRBUtil     // PRB utilization
	MetricSoftwareVersion = MetricUptime           // Version info (placeholder)

	// RF signal aliases (generic, not band-specific)
	MetricRSSI = MetricSignalStrength
	MetricRSRP = MetricRSRPNR3500
	MetricRSRQ = MetricSignalQuality
	MetricSINR = MetricSINRNR3500

	// Call/Handover metrics
	MetricHandoverSuccess = MetricHandoverSuccessRate
	MetricHandoverFail    = MetricHandoverSuccessRate // Inverse metric (same type)
	MetricCallDrop        = MetricPacketLoss          // Call drop rate
	MetricRRCSuccess      = MetricRRCSetupSuccess     // RRC setup alias
)

// metricTypeNames maps metric types to their string names for the monitoring service.
var metricTypeNames = map[MetricType]string{
	// System metrics
	MetricCPUUsage:         "CPU_USAGE",
	MetricMemoryUsage:      "MEMORY_USAGE",
	MetricTemperature:      "TEMPERATURE",
	MetricHumidity:         "HUMIDITY",
	MetricFanSpeed:         "FAN_SPEED",
	MetricVoltage:          "VOLTAGE",
	MetricCurrent:          "CURRENT",
	MetricPowerConsumption: "POWER_CONSUMPTION",

	// RF metrics
	MetricSignalStrength: "SIGNAL_STRENGTH",
	MetricSignalQuality:  "SIGNAL_QUALITY",
	MetricInterference:   "INTERFERENCE",
	MetricBER:            "BER",
	MetricVSWR:           "VSWR",
	MetricAntennaTilt:    "ANTENNA_TILT",

	// Performance metrics
	MetricDataThroughput:  "DATA_THROUGHPUT",
	MetricLatency:         "LATENCY",
	MetricPacketLoss:      "PACKET_LOSS",
	MetricJitter:          "JITTER",
	MetricConnectionCount: "CONNECTION_COUNT",

	// Device metrics
	MetricBatteryLevel: "BATTERY_LEVEL",
	MetricUptime:       "UPTIME",
	MetricErrorCount:   "ERROR_COUNT",

	// 5G NR700 (n28) metrics
	MetricDLThroughputNR700: "DL_THROUGHPUT_NR700",
	MetricULThroughputNR700: "UL_THROUGHPUT_NR700",
	MetricRSRPNR700:         "RSRP_NR700",
	MetricSINRNR700:         "SINR_NR700",

	// 5G NR3500 (n78) metrics
	MetricDLThroughputNR3500: "DL_THROUGHPUT_NR3500",
	MetricULThroughputNR3500: "UL_THROUGHPUT_NR3500",
	MetricRSRPNR3500:         "RSRP_NR3500",
	MetricSINRNR3500:         "SINR_NR3500",

	// 5G Radio metrics
	MetricPDCPThroughput:  "PDCP_THROUGHPUT",
	MetricRLCThroughput:   "RLC_THROUGHPUT",
	MetricInitialBLER:     "INITIAL_BLER",
	MetricAvgMCS:          "AVG_MCS",
	MetricRBPerSlot:       "RB_PER_SLOT",
	MetricRankIndicator:   "RANK_INDICATOR",

	// RF Quality metrics
	MetricTXImbalance:         "TX_IMBALANCE",
	MetricLatencyPing:         "LATENCY_PING",
	MetricHandoverSuccessRate: "HANDOVER_SUCCESS_RATE",
	MetricInterferenceLevel:   "INTERFERENCE_LEVEL",

	// Carrier Aggregation metrics
	MetricCADLThroughput: "CA_DL_THROUGHPUT",
	MetricCAULThroughput: "CA_UL_THROUGHPUT",

	// Power & Energy metrics (0x80-0x8F)
	MetricUtilityVoltageL1:   "UTILITY_VOLTAGE_L1",
	MetricUtilityVoltageL2:   "UTILITY_VOLTAGE_L2",
	MetricUtilityVoltageL3:   "UTILITY_VOLTAGE_L3",
	MetricPowerFactor:        "POWER_FACTOR",
	MetricGeneratorFuelLevel: "GENERATOR_FUEL_LEVEL",
	MetricGeneratorRuntime:   "GENERATOR_RUNTIME",
	MetricBatterySOC:         "BATTERY_SOC",
	MetricBatteryDOD:         "BATTERY_DOD",
	MetricBatteryCellTempMin: "BATTERY_CELL_TEMP_MIN",
	MetricBatteryCellTempMax: "BATTERY_CELL_TEMP_MAX",
	MetricSolarPanelVoltage:  "SOLAR_PANEL_VOLTAGE",
	MetricSolarChargeCurrent: "SOLAR_CHARGE_CURRENT",
	MetricSitePowerKWH:       "SITE_POWER_KWH",

	// Environmental & Safety metrics (0x90-0x9F)
	MetricWindSpeed:         "WIND_SPEED",
	MetricWindDirection:     "WIND_DIRECTION",
	MetricPrecipitation:     "PRECIPITATION",
	MetricLightningDistance: "LIGHTNING_DISTANCE",
	MetricTiltAngle:         "TILT_ANGLE",
	MetricVibrationLevel:    "VIBRATION_LEVEL",
	MetricWaterLevel:        "WATER_LEVEL",
	MetricPM25Level:         "PM25_LEVEL",
	MetricSmokeDetected:     "SMOKE_DETECTED",
	MetricCOLevel:           "CO_LEVEL",
	MetricDoorStatus:        "DOOR_STATUS",
	MetricMotionDetected:    "MOTION_DETECTED",

	// Transport/Backhaul metrics (0xA0-0xAF)
	MetricFiberRxPower:   "FIBER_RX_POWER",
	MetricFiberTxPower:   "FIBER_TX_POWER",
	MetricFiberBER:       "FIBER_BER",
	MetricFiberOSNR:      "FIBER_OSNR",
	MetricMWRSL:          "MW_RSL",
	MetricMWSNR:          "MW_SNR",
	MetricMWModulation:   "MW_MODULATION",
	MetricEthUtilization: "ETH_UTILIZATION",
	MetricEthErrors:      "ETH_ERRORS",
	MetricEthLatency:     "ETH_LATENCY",
	MetricPTPOffset:      "PTP_OFFSET",
	MetricGPSSatellites:  "GPS_SATELLITES",

	// Advanced Radio metrics (0xB0-0xBF)
	MetricBeamWeightMag:     "BEAM_WEIGHT_MAG",
	MetricBeamWeightPhase:   "BEAM_WEIGHT_PHASE",
	MetricPrecodingRank:     "PRECODING_RANK",
	MetricPIMLevel:          "PIM_LEVEL",
	MetricCoChannelInterf:   "CO_CHANNEL_INTERFERENCE",
	MetricOccupiedBandwidth: "OCCUPIED_BANDWIDTH",
	MetricACLR:              "ACLR",
	MetricGTPThroughput:     "GTP_THROUGHPUT",
	MetricPacketDelay:       "PACKET_DELAY",
	MetricRRCSetupSuccess:   "RRC_SETUP_SUCCESS",
	MetricPagingSuccess:     "PAGING_SUCCESS",

	// Network Slicing metrics (0xC0-0xCF)
	MetricSliceThroughput:    "SLICE_THROUGHPUT",
	MetricSliceLatency:       "SLICE_LATENCY",
	MetricSlicePacketLoss:    "SLICE_PACKET_LOSS",
	MetricSlicePRBUtil:       "SLICE_PRB_UTIL",
	MetricSliceSLACompliance: "SLICE_SLA_COMPLIANCE",
}

// MetricTypeString returns the metric type name for the monitoring service.
// Returns empty string for unsupported types - they will be filtered out.
func MetricTypeString(mt MetricType) string {
	return metricTypeNames[mt]
}

// CommandType defines types of executable commands.
type CommandType byte

const (
	CmdRestart       CommandType = 0x01
	CmdShutdown      CommandType = 0x02
	CmdResetConfig   CommandType = 0x03
	CmdUpdateFirmware CommandType = 0x04
	CmdRunDiagnostic CommandType = 0x05
	CmdSetParameter  CommandType = 0x06
)

// StatusCode defines device status codes.
type StatusCode byte

const (
	StatusOK        StatusCode = 0x00
	StatusWarning   StatusCode = 0x01
	StatusError     StatusCode = 0x02
	StatusCritical  StatusCode = 0x03
	StatusOffline   StatusCode = 0x04
)

// Message represents a protocol message.
type Message struct {
	Type     MessageType
	Sequence byte
	Payload  []byte
}

// Metric represents a single metric value.
type Metric struct {
	Type  MetricType
	Value float32
}

// StatusPayload represents a device status response.
type StatusPayload struct {
	Status   StatusCode
	Uptime   uint32
	Errors   uint16
	Warnings uint16
}

// CommandPayload represents a command to execute.
type CommandPayload struct {
	Type   CommandType
	Params []byte
}

// CommandResultPayload represents a command execution result.
type CommandResultPayload struct {
	Success    bool
	ReturnCode byte
	Output     string
}

// NewPingMessage creates a PING message.
func NewPingMessage(sequence byte) *Message {
	return &Message{
		Type:     MsgPing,
		Sequence: sequence,
	}
}

// NewPongMessage creates a PONG message.
func NewPongMessage(sequence byte) *Message {
	return &Message{
		Type:     MsgPong,
		Sequence: sequence,
	}
}

// NewMetricsRequestMessage creates a metrics request message.
func NewMetricsRequestMessage(sequence byte, metricTypes []MetricType) *Message {
	payload := make([]byte, len(metricTypes))
	for i, mt := range metricTypes {
		payload[i] = byte(mt)
	}
	if len(payload) == 0 {
		payload = []byte{byte(MetricAll)}
	}
	return &Message{
		Type:     MsgRequestMetrics,
		Sequence: sequence,
		Payload:  payload,
	}
}

// NewStatusRequestMessage creates a status request message.
func NewStatusRequestMessage(sequence byte) *Message {
	return &Message{
		Type:     MsgGetStatus,
		Sequence: sequence,
	}
}

// NewCommandMessage creates a command execution message.
func NewCommandMessage(sequence byte, cmdType CommandType, params []byte) *Message {
	payload := make([]byte, 1+len(params))
	payload[0] = byte(cmdType)
	copy(payload[1:], params)
	return &Message{
		Type:     MsgExecuteCommand,
		Sequence: sequence,
		Payload:  payload,
	}
}

// IsRequest returns true if the message is a request type.
func (m *Message) IsRequest() bool {
	return m.Type >= 0x01 && m.Type <= 0x07
}

// IsResponse returns true if the message is a response type.
func (m *Message) IsResponse() bool {
	return m.Type >= 0x81 && m.Type <= 0x86
}

// IsEvent returns true if the message is an event type.
func (m *Message) IsEvent() bool {
	return m.Type >= 0xA1 && m.Type <= 0xA4
}
