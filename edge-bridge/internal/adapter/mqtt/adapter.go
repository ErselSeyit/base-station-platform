// Package mqtt provides an MQTT adapter for collecting metrics from IoT sensors.
package mqtt

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"

	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/protocol"
)

// MQTTAdapter collects metrics from MQTT topics.
type MQTTAdapter struct {
	config    Config
	client    mqtt.Client
	metrics   chan protocol.Metric
	topicMap  map[string]types.MetricMapping
	mu        sync.RWMutex
	connected bool
	done      chan struct{}
}

// Config holds MQTT-specific configuration.
type Config struct {
	types.Config `yaml:",inline"`

	// Broker is the MQTT broker URL (e.g., "tcp://localhost:1883")
	Broker string `yaml:"broker"`

	// ClientID is the MQTT client ID.
	ClientID string `yaml:"client_id"`

	// Username for MQTT authentication.
	Username string `yaml:"username"`

	// Password for MQTT authentication.
	Password string `yaml:"password"`

	// UseTLS enables TLS connection.
	UseTLS bool `yaml:"use_tls"`

	// Topics to subscribe to with their metric mappings.
	Topics []TopicMapping `yaml:"topics"`

	// QoS level (0, 1, or 2).
	QoS byte `yaml:"qos"`

	// CleanSession starts a new session.
	CleanSession bool `yaml:"clean_session"`
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

// SensorPayload represents a standard sensor message format.
type SensorPayload struct {
	DeviceID  string    `json:"device_id"`
	Value     float64   `json:"value"`
	Unit      string    `json:"unit"`
	Timestamp time.Time `json:"timestamp"`
}

// DefaultConfig returns default MQTT configuration.
func DefaultConfig() Config {
	return Config{
		Config:       types.DefaultConfig(),
		QoS:          1,
		CleanSession: true,
	}
}

// DefaultTopicMappings returns standard topic patterns for IoT sensors.
var DefaultTopicMappings = []TopicMapping{
	{Topic: "sensors/+/temperature", MetricType: "TEMPERATURE", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/humidity", MetricType: "HUMIDITY", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/door", MetricType: "DOOR_STATUS", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/motion", MetricType: "MOTION_DETECTED", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/water", MetricType: "WATER_LEVEL", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/smoke", MetricType: "SMOKE_DETECTED", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/co", MetricType: "CO_LEVEL", ValuePath: "value", Scale: 1.0},
	{Topic: "sensors/+/pm25", MetricType: "PM25_LEVEL", ValuePath: "value", Scale: 1.0},
	{Topic: "power/+/voltage", MetricType: "VOLTAGE", ValuePath: "value", Scale: 1.0},
	{Topic: "power/+/current", MetricType: "CURRENT", ValuePath: "value", Scale: 1.0},
	{Topic: "power/+/battery_soc", MetricType: "BATTERY_SOC", ValuePath: "value", Scale: 1.0},
	{Topic: "weather/+/wind_speed", MetricType: "WIND_SPEED", ValuePath: "value", Scale: 1.0},
	{Topic: "weather/+/wind_direction", MetricType: "WIND_DIRECTION", ValuePath: "value", Scale: 1.0},
	{Topic: "weather/+/precipitation", MetricType: "PRECIPITATION", ValuePath: "value", Scale: 1.0},
}

// New creates a new MQTT adapter.
func New(cfg Config) (*MQTTAdapter, error) {
	if cfg.Broker == "" {
		return nil, fmt.Errorf("MQTT broker URL is required")
	}

	if cfg.ClientID == "" {
		cfg.ClientID = fmt.Sprintf("edge-bridge-%d", time.Now().UnixNano())
	}

	// Build topic map
	topicMap := make(map[string]types.MetricMapping)
	topics := cfg.Topics
	if len(topics) == 0 {
		topics = DefaultTopicMappings
	}

	for _, t := range topics {
		mt := parseMetricType(t.MetricType)
		if mt != 0 {
			scale := t.Scale
			if scale == 0 {
				scale = 1.0
			}
			topicMap[t.Topic] = types.MetricMapping{
				ExternalID:  t.Topic,
				MetricType:  mt,
				Scale:       scale,
				Offset:      t.Offset,
				Description: t.ValuePath,
			}
		}
	}

	return &MQTTAdapter{
		config:   cfg,
		metrics:  make(chan protocol.Metric, 1000),
		topicMap: topicMap,
		done:     make(chan struct{}),
	}, nil
}

// Name returns the adapter name.
func (m *MQTTAdapter) Name() string {
	return fmt.Sprintf("mqtt-%s", m.config.Broker)
}

// Connect establishes the MQTT connection.
func (m *MQTTAdapter) Connect(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.connected {
		return nil
	}

	opts := mqtt.NewClientOptions()
	opts.AddBroker(m.config.Broker)
	opts.SetClientID(m.config.ClientID)
	opts.SetCleanSession(m.config.CleanSession)

	if m.config.Username != "" {
		opts.SetUsername(m.config.Username)
		opts.SetPassword(m.config.Password)
	}

	if m.config.Timeout > 0 {
		opts.SetConnectTimeout(m.config.Timeout)
	}

	// Set handlers
	opts.SetDefaultPublishHandler(m.messageHandler)
	opts.SetOnConnectHandler(m.onConnectHandler)
	opts.SetConnectionLostHandler(m.onConnectionLostHandler)
	opts.SetAutoReconnect(true)
	opts.SetConnectRetry(true)

	client := mqtt.NewClient(opts)

	token := client.Connect()
	if !token.WaitTimeout(m.config.Timeout) {
		return fmt.Errorf("MQTT connection timeout")
	}
	if token.Error() != nil {
		return fmt.Errorf("MQTT connection failed: %w", token.Error())
	}

	m.client = client
	m.connected = true
	log.Printf("[MQTT] Connected to %s", m.config.Broker)

	return nil
}

// Close closes the MQTT connection.
func (m *MQTTAdapter) Close() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.connected {
		return nil
	}

	close(m.done)
	m.client.Disconnect(1000)
	m.connected = false
	log.Printf("[MQTT] Disconnected from %s", m.config.Broker)
	return nil
}

// IsConnected returns true if connected.
func (m *MQTTAdapter) IsConnected() bool {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.connected && m.client != nil && m.client.IsConnected()
}

// CollectMetrics returns metrics received since last call.
func (m *MQTTAdapter) CollectMetrics(ctx context.Context) ([]protocol.Metric, error) {
	if !m.IsConnected() {
		return nil, fmt.Errorf("not connected")
	}

	var metrics []protocol.Metric

	// Drain the metrics channel
	for {
		select {
		case metric := <-m.metrics:
			metrics = append(metrics, metric)
		default:
			return metrics, nil
		}
	}
}

// CollectMetric is not supported for MQTT (push-based).
func (m *MQTTAdapter) CollectMetric(ctx context.Context, metricType protocol.MetricType) (*protocol.Metric, error) {
	return nil, fmt.Errorf("MQTT adapter does not support on-demand metric collection")
}

// Metrics returns the metrics channel for streaming.
func (m *MQTTAdapter) Metrics() <-chan protocol.Metric {
	return m.metrics
}

// messageHandler processes incoming MQTT messages.
func (m *MQTTAdapter) messageHandler(client mqtt.Client, msg mqtt.Message) {
	topic := msg.Topic()

	// Find matching topic pattern
	var mapping *types.MetricMapping
	for pattern, mm := range m.topicMap {
		if matchTopicPattern(pattern, topic) {
			mmCopy := mm
			mapping = &mmCopy
			break
		}
	}

	if mapping == nil {
		return
	}

	// Parse payload
	value, err := m.parsePayload(msg.Payload(), mapping.Description)
	if err != nil {
		log.Printf("[MQTT] Failed to parse payload from %s: %v", topic, err)
		return
	}

	// Apply transformation
	value = mapping.ApplyTransform(value)

	metric := protocol.Metric{
		Type:  mapping.MetricType,
		Value: value,
	}

	// Non-blocking send to channel - drop if full
	select {
	case m.metrics <- metric:
		// Successfully queued
	default:
		// Channel full - log and drop (avoids race in drain-and-retry pattern)
		log.Printf("[MQTT] Metrics channel full, dropping metric type %d", metric.Type)
	}
}

// parsePayload extracts the value from an MQTT payload.
func (m *MQTTAdapter) parsePayload(payload []byte, valuePath string) (float32, error) {
	// Try JSON parsing first
	var data map[string]interface{}
	if err := json.Unmarshal(payload, &data); err == nil {
		// Navigate JSON path
		return extractJSONValue(data, valuePath)
	}

	// Try direct float parsing
	var value float64
	if _, err := fmt.Sscanf(string(payload), "%f", &value); err == nil {
		return float32(value), nil
	}

	return 0, fmt.Errorf("cannot parse payload")
}

// extractJSONValue extracts a value from nested JSON using dot notation.
func extractJSONValue(data map[string]interface{}, path string) (float32, error) {
	if path == "" {
		path = "value"
	}

	parts := strings.Split(path, ".")
	current := interface{}(data)

	for _, part := range parts {
		switch v := current.(type) {
		case map[string]interface{}:
			var ok bool
			current, ok = v[part]
			if !ok {
				return 0, fmt.Errorf("key %s not found", part)
			}
		default:
			return 0, fmt.Errorf("cannot navigate to %s", part)
		}
	}

	// Convert to float
	switch v := current.(type) {
	case float64:
		return float32(v), nil
	case float32:
		return v, nil
	case int:
		return float32(v), nil
	case int64:
		return float32(v), nil
	case bool:
		if v {
			return 1.0, nil
		}
		return 0.0, nil
	default:
		return 0, fmt.Errorf("cannot convert %T to float", current)
	}
}

// matchTopicPattern checks if a topic matches an MQTT pattern with wildcards.
func matchTopicPattern(pattern, topic string) bool {
	patternParts := strings.Split(pattern, "/")
	topicParts := strings.Split(topic, "/")

	pi := 0
	ti := 0

	for pi < len(patternParts) && ti < len(topicParts) {
		if patternParts[pi] == "#" {
			return true
		}
		if patternParts[pi] == "+" {
			pi++
			ti++
			continue
		}
		if patternParts[pi] != topicParts[ti] {
			return false
		}
		pi++
		ti++
	}

	return pi == len(patternParts) && ti == len(topicParts)
}

// onConnectHandler is called when connection is established.
func (m *MQTTAdapter) onConnectHandler(client mqtt.Client) {
	log.Printf("[MQTT] Connected, subscribing to topics...")

	// Subscribe to all configured topics
	for pattern := range m.topicMap {
		token := client.Subscribe(pattern, m.config.QoS, nil)
		token.Wait()
		if token.Error() != nil {
			log.Printf("[MQTT] Subscribe to %s failed: %v", pattern, token.Error())
		} else {
			log.Printf("[MQTT] Subscribed to %s", pattern)
		}
	}
}

// onConnectionLostHandler is called when connection is lost.
func (m *MQTTAdapter) onConnectionLostHandler(client mqtt.Client, err error) {
	log.Printf("[MQTT] Connection lost: %v", err)
}

// parseMetricType converts a metric type name to protocol.MetricType.
func parseMetricType(name string) protocol.MetricType {
	metricTypeMap := map[string]protocol.MetricType{
		"TEMPERATURE":       protocol.MetricTemperature,
		"HUMIDITY":          protocol.MetricHumidity,
		"DOOR_STATUS":       protocol.MetricDoorStatus,
		"MOTION_DETECTED":   protocol.MetricMotionDetected,
		"WATER_LEVEL":       protocol.MetricWaterLevel,
		"SMOKE_DETECTED":    protocol.MetricSmokeDetected,
		"CO_LEVEL":          protocol.MetricCOLevel,
		"PM25_LEVEL":        protocol.MetricPM25Level,
		"VOLTAGE":           protocol.MetricVoltage,
		"CURRENT":           protocol.MetricCurrent,
		"BATTERY_SOC":       protocol.MetricBatterySOC,
		"WIND_SPEED":        protocol.MetricWindSpeed,
		"WIND_DIRECTION":    protocol.MetricWindDirection,
		"PRECIPITATION":     protocol.MetricPrecipitation,
		"LIGHTNING_DISTANCE": protocol.MetricLightningDistance,
		"TILT_ANGLE":        protocol.MetricTiltAngle,
		"VIBRATION_LEVEL":   protocol.MetricVibrationLevel,
	}

	if mt, ok := metricTypeMap[name]; ok {
		return mt
	}
	return 0
}
