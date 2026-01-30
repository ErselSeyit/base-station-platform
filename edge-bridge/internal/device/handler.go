// Package device provides device connection management and message handling.
package device

import (
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/huawei/edge-bridge/internal/protocol"
)

// MessageHandler processes incoming protocol messages.
type MessageHandler struct {
	onMetrics func([]protocol.Metric)
	onStatus  func(*protocol.StatusPayload)
	onAlert   func(protocol.MessageType, []byte)
	onError   func(error)
}

// NewMessageHandler creates a new message handler.
func NewMessageHandler() *MessageHandler {
	return &MessageHandler{}
}

// OnMetrics sets the callback for metrics responses.
func (h *MessageHandler) OnMetrics(fn func([]protocol.Metric)) {
	h.onMetrics = fn
}

// OnStatus sets the callback for status responses.
func (h *MessageHandler) OnStatus(fn func(*protocol.StatusPayload)) {
	h.onStatus = fn
}

// OnAlert sets the callback for alert events.
func (h *MessageHandler) OnAlert(fn func(protocol.MessageType, []byte)) {
	h.onAlert = fn
}

// OnError sets the callback for errors.
func (h *MessageHandler) OnError(fn func(error)) {
	h.onError = fn
}

// HandleMessage processes an incoming message.
func (h *MessageHandler) HandleMessage(msg *protocol.Message) {
	switch msg.Type {
	case protocol.MsgPong:
		log.Printf("Received PONG (seq=%d)", msg.Sequence)

	case protocol.MsgMetricsResponse:
		h.handleMetricsResponse(msg)

	case protocol.MsgStatusResponse:
		h.handleStatusResponse(msg)

	case protocol.MsgCommandResult:
		h.handleCommandResult(msg)

	case protocol.MsgMetricsEvent:
		h.handleMetricsEvent(msg)

	case protocol.MsgThresholdExceeded:
		h.handleThresholdExceeded(msg)

	case protocol.MsgDeviceStateChange:
		h.handleDeviceStateChange(msg)

	case protocol.MsgError:
		h.handleErrorEvent(msg)

	default:
		log.Printf("Unknown message type: 0x%02X", msg.Type)
	}
}

func (h *MessageHandler) handleMetricsResponse(msg *protocol.Message) {
	metrics, err := protocol.ParseMetricsResponse(msg.Payload)
	if err != nil {
		log.Printf("Failed to parse metrics response: %v", err)
		if h.onError != nil {
			h.onError(err)
		}
		return
	}

	log.Printf("Received %d metrics", len(metrics))
	for _, m := range metrics {
		log.Printf("  %s: %.4f", protocol.MetricTypeString(m.Type), m.Value)
	}

	if h.onMetrics != nil {
		h.onMetrics(metrics)
	}
}

func (h *MessageHandler) handleStatusResponse(msg *protocol.Message) {
	status, err := protocol.ParseStatusResponse(msg.Payload)
	if err != nil {
		log.Printf("Failed to parse status response: %v", err)
		if h.onError != nil {
			h.onError(err)
		}
		return
	}

	log.Printf("Status: code=%d, uptime=%d, errors=%d, warnings=%d",
		status.Status, status.Uptime, status.Errors, status.Warnings)

	if h.onStatus != nil {
		h.onStatus(status)
	}
}

func (h *MessageHandler) handleCommandResult(msg *protocol.Message) {
	result, err := protocol.ParseCommandResult(msg.Payload)
	if err != nil {
		log.Printf("Failed to parse command result: %v", err)
		return
	}

	if result.Success {
		log.Printf("Command succeeded (code=%d): %s", result.ReturnCode, result.Output)
	} else {
		log.Printf("Command failed (code=%d): %s", result.ReturnCode, result.Output)
	}
}

func (h *MessageHandler) handleMetricsEvent(msg *protocol.Message) {
	log.Printf("Received metrics event")
	if h.onAlert != nil {
		h.onAlert(msg.Type, msg.Payload)
	}
}

func (h *MessageHandler) handleThresholdExceeded(msg *protocol.Message) {
	log.Printf("ALERT: Threshold exceeded!")
	if h.onAlert != nil {
		h.onAlert(msg.Type, msg.Payload)
	}
}

func (h *MessageHandler) handleDeviceStateChange(msg *protocol.Message) {
	log.Printf("Device state changed")
	if h.onAlert != nil {
		h.onAlert(msg.Type, msg.Payload)
	}
}

func (h *MessageHandler) handleErrorEvent(msg *protocol.Message) {
	log.Printf("Device error event received")
	if h.onError != nil {
		h.onError(fmt.Errorf("device error: %v", msg.Payload))
	}
}

// PendingRequest tracks a request waiting for response.
type PendingRequest struct {
	Sequence   byte
	Type       protocol.MessageType
	SentAt     time.Time
	ResponseCh chan *protocol.Message
}

// RequestTracker tracks pending requests and matches responses.
type RequestTracker struct {
	mu      sync.Mutex
	pending map[byte]*PendingRequest
	timeout time.Duration
}

// NewRequestTracker creates a new request tracker.
func NewRequestTracker(timeout time.Duration) *RequestTracker {
	return &RequestTracker{
		pending: make(map[byte]*PendingRequest),
		timeout: timeout,
	}
}

// Track adds a request to track.
func (rt *RequestTracker) Track(seq byte, msgType protocol.MessageType) *PendingRequest {
	rt.mu.Lock()
	defer rt.mu.Unlock()

	req := &PendingRequest{
		Sequence:   seq,
		Type:       msgType,
		SentAt:     time.Now(),
		ResponseCh: make(chan *protocol.Message, 1),
	}
	rt.pending[seq] = req
	return req
}

// Complete marks a request as complete with its response.
// Returns true if the request was found and completed.
func (rt *RequestTracker) Complete(msg *protocol.Message) bool {
	rt.mu.Lock()
	req, ok := rt.pending[msg.Sequence]
	if !ok {
		rt.mu.Unlock()
		return false
	}
	delete(rt.pending, msg.Sequence)
	rt.mu.Unlock()

	// Send outside lock to avoid deadlock; channel is buffered
	select {
	case req.ResponseCh <- msg:
	default:
		// Channel full - shouldn't happen with buffered channel of 1
		log.Printf("Warning: response channel full for seq %d", msg.Sequence)
	}
	return true
}

// Cleanup removes expired requests and signals timeout on their channels.
func (rt *RequestTracker) Cleanup() {
	rt.mu.Lock()
	defer rt.mu.Unlock()

	now := time.Now()
	for seq, req := range rt.pending {
		if now.Sub(req.SentAt) > rt.timeout {
			delete(rt.pending, seq)
			// Send nil to signal timeout instead of closing
			select {
			case req.ResponseCh <- nil:
			default:
			}
		}
	}
}

// Remove removes a specific request from tracking without sending a response.
func (rt *RequestTracker) Remove(seq byte) {
	rt.mu.Lock()
	defer rt.mu.Unlock()
	delete(rt.pending, seq)
}
