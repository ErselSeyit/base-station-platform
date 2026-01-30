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

	// Special values
	MetricAll              MetricType = 0xFF
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
