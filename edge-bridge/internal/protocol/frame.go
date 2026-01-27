package protocol

import (
	"encoding/binary"
	"errors"
	"fmt"
)

// Frame parsing errors
var (
	ErrInvalidHeader  = errors.New("invalid frame header")
	ErrPayloadTooLong = errors.New("payload exceeds maximum length")
	ErrCRCMismatch    = errors.New("CRC check failed")
	ErrBufferTooSmall = errors.New("buffer too small for frame")
	ErrIncompleteFrame = errors.New("incomplete frame data")
)

// ParserState represents the state of the frame parser.
type ParserState int

const (
	StateIdle ParserState = iota
	StateHeader1
	StateLength
	StateType
	StateSequence
	StatePayload
	StateCRC
)

// FrameParser parses incoming byte streams into messages.
type FrameParser struct {
	state      ParserState
	buffer     []byte
	bufferPos  int
	payloadLen int
	crcErrors  int
}

// NewFrameParser creates a new frame parser.
func NewFrameParser() *FrameParser {
	return &FrameParser{
		state:  StateIdle,
		buffer: make([]byte, MaxFrameSize),
	}
}

// Reset resets the parser to initial state.
func (p *FrameParser) Reset() {
	p.state = StateIdle
	p.bufferPos = 0
	p.payloadLen = 0
}

// CRCErrors returns the number of CRC errors encountered.
func (p *FrameParser) CRCErrors() int {
	return p.crcErrors
}

// ParseByte processes a single byte and returns true if a complete message is ready.
func (p *FrameParser) ParseByte(b byte) bool {
	switch p.state {
	case StateIdle:
		if b == HeaderByte0 {
			p.buffer[0] = b
			p.bufferPos = 1
			p.state = StateHeader1
		}
		return false

	case StateHeader1:
		if b == HeaderByte1 {
			p.buffer[1] = b
			p.bufferPos = 2
			p.state = StateLength
		} else if b == HeaderByte0 {
			// Stay in this state, might be another start
			p.buffer[0] = b
			p.bufferPos = 1
		} else {
			p.state = StateIdle
		}
		return false

	case StateLength:
		p.buffer[p.bufferPos] = b
		p.bufferPos++
		if p.bufferPos == 4 {
			p.payloadLen = int(binary.BigEndian.Uint16(p.buffer[2:4]))
			if p.payloadLen > MaxPayloadLen {
				p.state = StateIdle
				return false
			}
			p.state = StateType
		}
		return false

	case StateType:
		p.buffer[p.bufferPos] = b
		p.bufferPos++
		p.state = StateSequence
		return false

	case StateSequence:
		p.buffer[p.bufferPos] = b
		p.bufferPos++
		if p.payloadLen > 0 {
			p.state = StatePayload
		} else {
			p.state = StateCRC
		}
		return false

	case StatePayload:
		p.buffer[p.bufferPos] = b
		p.bufferPos++
		if p.bufferPos >= HeaderSize+p.payloadLen {
			p.state = StateCRC
		}
		return false

	case StateCRC:
		p.buffer[p.bufferPos] = b
		p.bufferPos++
		if p.bufferPos >= HeaderSize+p.payloadLen+CRCSize {
			// Frame complete, verify CRC
			frameLen := HeaderSize + p.payloadLen
			expectedCRC := CalculateCRC16(p.buffer[:frameLen])
			actualCRC := binary.BigEndian.Uint16(p.buffer[frameLen : frameLen+CRCSize])

			if expectedCRC != actualCRC {
				p.crcErrors++
				p.state = StateIdle
				return false
			}
			return true
		}
		return false
	}
	return false
}

// Parse processes a buffer of bytes and returns parsed messages.
func (p *FrameParser) Parse(data []byte) ([]*Message, error) {
	var messages []*Message

	for _, b := range data {
		if p.ParseByte(b) {
			msg, err := p.GetMessage()
			if err != nil {
				continue // Skip invalid messages
			}
			messages = append(messages, msg)
			p.Reset()
		}
	}

	return messages, nil
}

// GetMessage extracts the message from a completed frame.
func (p *FrameParser) GetMessage() (*Message, error) {
	if p.bufferPos < HeaderSize+CRCSize {
		return nil, ErrIncompleteFrame
	}

	msg := &Message{
		Type:     MessageType(p.buffer[4]),
		Sequence: p.buffer[5],
	}

	if p.payloadLen > 0 {
		msg.Payload = make([]byte, p.payloadLen)
		copy(msg.Payload, p.buffer[HeaderSize:HeaderSize+p.payloadLen])
	}

	return msg, nil
}

// BuildFrame serializes a message into wire format.
func BuildFrame(msg *Message) ([]byte, error) {
	payloadLen := len(msg.Payload)
	if payloadLen > MaxPayloadLen {
		return nil, ErrPayloadTooLong
	}

	frameLen := HeaderSize + payloadLen + CRCSize
	frame := make([]byte, frameLen)

	// Header magic
	frame[0] = HeaderByte0
	frame[1] = HeaderByte1

	// Length (big-endian)
	binary.BigEndian.PutUint16(frame[2:4], uint16(payloadLen))

	// Type and sequence
	frame[4] = byte(msg.Type)
	frame[5] = msg.Sequence

	// Payload
	if payloadLen > 0 {
		copy(frame[HeaderSize:], msg.Payload)
	}

	// CRC (big-endian)
	crc := CalculateCRC16(frame[:HeaderSize+payloadLen])
	binary.BigEndian.PutUint16(frame[HeaderSize+payloadLen:], crc)

	return frame, nil
}

// String returns a human-readable representation of the message.
func (m *Message) String() string {
	return fmt.Sprintf("Message{Type: 0x%02X, Seq: %d, PayloadLen: %d}",
		m.Type, m.Sequence, len(m.Payload))
}
