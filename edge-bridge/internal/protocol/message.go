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
	MetricCPUUsage         MetricType = 0x01
	MetricMemoryUsage      MetricType = 0x02
	MetricTemperature      MetricType = 0x03
	MetricHumidity         MetricType = 0x04
	MetricFanSpeed         MetricType = 0x05
	MetricVoltage          MetricType = 0x06
	MetricCurrent          MetricType = 0x07
	MetricPowerConsumption MetricType = 0x08
	MetricSignalStrength   MetricType = 0x10
	MetricSignalQuality    MetricType = 0x11
	MetricInterference     MetricType = 0x12
	MetricBER              MetricType = 0x13
	MetricVSWR             MetricType = 0x14
	MetricAntennaTilt      MetricType = 0x15
	MetricDataThroughput   MetricType = 0x20
	MetricLatency          MetricType = 0x21
	MetricPacketLoss       MetricType = 0x22
	MetricJitter           MetricType = 0x23
	MetricConnectionCount  MetricType = 0x24
	MetricBatteryLevel     MetricType = 0x30
	MetricUptime           MetricType = 0x31
	MetricErrorCount       MetricType = 0x32
	MetricAll              MetricType = 0xFF
)

// MetricTypeString returns the metric type name for the monitoring service.
func MetricTypeString(mt MetricType) string {
	switch mt {
	case MetricCPUUsage:
		return "CPU_USAGE"
	case MetricMemoryUsage:
		return "MEMORY_USAGE"
	case MetricTemperature:
		return "TEMPERATURE"
	case MetricSignalStrength:
		return "SIGNAL_STRENGTH"
	case MetricPowerConsumption:
		return "POWER_CONSUMPTION"
	case MetricFanSpeed:
		return "FAN_SPEED"
	case MetricDataThroughput:
		return "DATA_THROUGHPUT"
	case MetricConnectionCount:
		return "CONNECTION_COUNT"
	case MetricUptime:
		return "UPTIME"
	default:
		// Return empty for unsupported types - they will be filtered out
		return ""
	}
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
