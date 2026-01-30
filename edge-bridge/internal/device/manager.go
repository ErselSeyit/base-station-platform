package device

import (
	"context"
	"errors"
	"log"
	"sync"
	"sync/atomic"
	"time"

	"github.com/huawei/edge-bridge/internal/protocol"
	"github.com/huawei/edge-bridge/internal/transport"
)

// Manager errors
var (
	ErrNotConnected    = errors.New("device not connected")
	ErrResponseTimeout = errors.New("response timeout")
)

// ManagerConfig holds device manager configuration.
type ManagerConfig struct {
	ReconnectDelay   time.Duration
	HeartbeatInterval time.Duration
	ResponseTimeout  time.Duration
	MaxReconnectAttempts int
}

// DefaultManagerConfig returns default manager configuration.
func DefaultManagerConfig() *ManagerConfig {
	return &ManagerConfig{
		ReconnectDelay:       5 * time.Second,
		HeartbeatInterval:    30 * time.Second,
		ResponseTimeout:      5 * time.Second,
		MaxReconnectAttempts: 0, // 0 = unlimited
	}
}

// Manager manages the connection to a local device.
type Manager struct {
	transport transport.Transport
	config    *ManagerConfig
	parser    *protocol.FrameParser
	handler   *MessageHandler
	tracker   *RequestTracker

	sequence     byte
	connected    atomic.Bool
	lastActivity time.Time
	mu           sync.RWMutex // protects lastActivity and sequence

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
}

// NewManager creates a new device manager.
func NewManager(t transport.Transport, config *ManagerConfig) *Manager {
	if config == nil {
		config = DefaultManagerConfig()
	}

	return &Manager{
		transport: t,
		config:    config,
		parser:    protocol.NewFrameParser(),
		handler:   NewMessageHandler(),
		tracker:   NewRequestTracker(config.ResponseTimeout),
	}
}

// Handler returns the message handler.
func (m *Manager) Handler() *MessageHandler {
	return m.handler
}

// Start starts the device manager.
func (m *Manager) Start(ctx context.Context) error {
	m.ctx, m.cancel = context.WithCancel(ctx)

	// Connect to device
	if err := m.connect(); err != nil {
		return err
	}

	// Start receive loop
	m.wg.Add(1)
	go m.receiveLoop()

	// Start heartbeat loop
	m.wg.Add(1)
	go m.heartbeatLoop()

	return nil
}

// Stop stops the device manager.
func (m *Manager) Stop() error {
	if m.cancel != nil {
		m.cancel()
	}
	m.wg.Wait()
	return m.transport.Close()
}

// IsConnected returns true if connected to the device.
func (m *Manager) IsConnected() bool {
	return m.connected.Load()
}

func (m *Manager) connect() error {
	if err := m.transport.Open(); err != nil {
		return err
	}

	m.connected.Store(true)
	m.mu.Lock()
	m.lastActivity = time.Now()
	m.mu.Unlock()

	log.Printf("Connected to device via %s", m.transport.Type())
	return nil
}

func (m *Manager) reconnect() {
	m.connected.Store(false)
	m.transport.Close()

	attempts := 0
	for {
		select {
		case <-m.ctx.Done():
			return
		case <-time.After(m.config.ReconnectDelay):
		}

		attempts++
		if m.config.MaxReconnectAttempts > 0 && attempts > m.config.MaxReconnectAttempts {
			log.Printf("Max reconnect attempts reached, giving up")
			return
		}

		log.Printf("Attempting to reconnect (attempt %d)...", attempts)
		if err := m.connect(); err != nil {
			log.Printf("Reconnect failed: %v", err)
			continue
		}

		log.Printf("Reconnected successfully")
		m.parser.Reset()
		return
	}
}

// handleReceiveError processes receive errors, returns true if should continue loop.
func (m *Manager) handleReceiveError(err error) bool {
	if errors.Is(err, transport.ErrTimeout) {
		return true
	}
	log.Printf("Receive error: %v", err)
	m.reconnect()
	return true
}

// processMessages handles parsed protocol messages.
func (m *Manager) processMessages(messages []*protocol.Message) {
	for _, msg := range messages {
		if msg.IsResponse() && m.tracker.Complete(msg) {
			continue
		}
		m.handler.HandleMessage(msg)
	}
}

// handleReceivedData parses and processes received data.
func (m *Manager) handleReceivedData(data []byte) {
	m.mu.Lock()
	m.lastActivity = time.Now()
	m.mu.Unlock()

	messages, err := m.parser.Parse(data)
	if err != nil {
		log.Printf("Parse error: %v", err)
		return
	}

	m.processMessages(messages)
	m.parser.Reset()
}

func (m *Manager) receiveLoop() {
	defer m.wg.Done()

	buffer := make([]byte, 1024)
	for {
		select {
		case <-m.ctx.Done():
			return
		default:
		}

		if !m.IsConnected() {
			time.Sleep(100 * time.Millisecond)
			continue
		}

		n, err := m.transport.Receive(buffer, 100*time.Millisecond)
		if err != nil {
			m.handleReceiveError(err)
			continue
		}

		if n > 0 {
			m.handleReceivedData(buffer[:n])
		}
	}
}

func (m *Manager) heartbeatLoop() {
	defer m.wg.Done()

	ticker := time.NewTicker(m.config.HeartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-m.ctx.Done():
			return
		case <-ticker.C:
			if !m.IsConnected() {
				continue
			}

			if err := m.Ping(); err != nil {
				log.Printf("Heartbeat failed: %v", err)
			}
		}
	}
}

func (m *Manager) nextSequence() byte {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.sequence++
	return m.sequence
}

// Send sends a message to the device.
func (m *Manager) Send(msg *protocol.Message) error {
	if !m.IsConnected() {
		return ErrNotConnected
	}

	frame, err := protocol.BuildFrame(msg)
	if err != nil {
		return err
	}

	_, err = m.transport.Send(frame)
	return err
}

// SendAndWait sends a message and waits for response.
func (m *Manager) SendAndWait(msg *protocol.Message) (*protocol.Message, error) {
	if !m.IsConnected() {
		return nil, ErrNotConnected
	}

	// Track the request
	req := m.tracker.Track(msg.Sequence, msg.Type)

	// Send the message
	if err := m.Send(msg); err != nil {
		return nil, err
	}

	// Wait for response
	select {
	case resp := <-req.ResponseCh:
		if resp == nil {
			return nil, ErrResponseTimeout
		}
		return resp, nil
	case <-time.After(m.config.ResponseTimeout):
		m.tracker.Cleanup()
		return nil, ErrResponseTimeout
	case <-m.ctx.Done():
		return nil, m.ctx.Err()
	}
}

// Ping sends a PING and waits for PONG.
func (m *Manager) Ping() error {
	msg := protocol.NewPingMessage(m.nextSequence())
	resp, err := m.SendAndWait(msg)
	if err != nil {
		return err
	}
	if resp.Type != protocol.MsgPong {
		return errors.New("unexpected response type")
	}
	return nil
}

// RequestMetrics requests metrics from the device.
func (m *Manager) RequestMetrics(types []protocol.MetricType) ([]protocol.Metric, error) {
	msg := protocol.NewMetricsRequestMessage(m.nextSequence(), types)
	resp, err := m.SendAndWait(msg)
	if err != nil {
		return nil, err
	}
	return protocol.ParseMetricsResponse(resp.Payload)
}

// RequestStatus requests status from the device.
func (m *Manager) RequestStatus() (*protocol.StatusPayload, error) {
	msg := protocol.NewStatusRequestMessage(m.nextSequence())
	resp, err := m.SendAndWait(msg)
	if err != nil {
		return nil, err
	}
	return protocol.ParseStatusResponse(resp.Payload)
}

// ExecuteCommand executes a command on the device.
func (m *Manager) ExecuteCommand(cmdType protocol.CommandType, params []byte) (*protocol.CommandResultPayload, error) {
	msg := protocol.NewCommandMessage(m.nextSequence(), cmdType, params)
	resp, err := m.SendAndWait(msg)
	if err != nil {
		return nil, err
	}
	return protocol.ParseCommandResult(resp.Payload)
}

// LastActivity returns the time of last activity.
func (m *Manager) LastActivity() time.Time {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.lastActivity
}
