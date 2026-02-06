#!/usr/bin/env python3
"""
Device Protocol Implementation for MIPS Base Station Simulator

Binary protocol compatible with:
- C library (device-protocol-c)
- Go edge-bridge (edge-bridge/internal/protocol)

Frame Format:
+--------+--------+--------+--------+-------------+--------+
| 0xAA55 | LENGTH |  TYPE  |  SEQ   | PAYLOAD     |  CRC   |
+--------+--------+--------+--------+-------------+--------+
| 2 bytes| 2 bytes| 1 byte | 1 byte | 0-4096 bytes| 2 bytes|
         (big-endian)                              (CRC-16-CCITT)
"""

import struct
import socket
import threading
import logging
from enum import IntEnum
from dataclasses import dataclass
from typing import Optional, List, Callable, Dict, Any

logger = logging.getLogger(__name__)

# Protocol Constants
HEADER_MAGIC = 0xAA55
HEADER_BYTE0 = 0xAA
HEADER_BYTE1 = 0x55
MAX_PAYLOAD_SIZE = 4096
HEADER_SIZE = 6  # magic(2) + length(2) + type(1) + seq(1)
CRC_SIZE = 2
MIN_FRAME_SIZE = HEADER_SIZE + CRC_SIZE


class MessageType(IntEnum):
    """Protocol message types - compatible with C/Go implementations"""
    # Requests (PC -> Device)
    PING = 0x01
    REQUEST_METRICS = 0x02
    GET_STATUS = 0x03
    SET_CONFIG = 0x04
    EXECUTE_COMMAND = 0x05
    START_STREAM = 0x06
    STOP_STREAM = 0x07

    # Responses (Device -> PC)
    PONG = 0x81
    METRICS_RESPONSE = 0x82
    STATUS_RESPONSE = 0x83
    CONFIG_ACK = 0x84
    COMMAND_RESULT = 0x85
    STREAM_ACK = 0x86

    # Async Events (Device -> PC)
    METRICS_EVENT = 0xA1
    THRESHOLD_EXCEEDED = 0xA2
    DEVICE_STATE_CHANGE = 0xA3
    ERROR = 0xA4


class MetricType(IntEnum):
    """Metric types - compatible with C/Go implementations"""
    # System metrics (0x01-0x0F)
    CPU_USAGE = 0x01
    MEMORY_USAGE = 0x02
    TEMPERATURE = 0x03
    HUMIDITY = 0x04
    FAN_SPEED = 0x05
    VOLTAGE = 0x06
    CURRENT = 0x07
    POWER_CONSUMPTION = 0x08

    # RF metrics (0x10-0x1F)
    SIGNAL_STRENGTH = 0x10
    SIGNAL_QUALITY = 0x11
    INTERFERENCE = 0x12
    BER = 0x13
    VSWR = 0x14
    ANTENNA_TILT = 0x15

    # Performance metrics (0x20-0x2F)
    DATA_THROUGHPUT = 0x20
    LATENCY = 0x21
    PACKET_LOSS = 0x22
    JITTER = 0x23
    CONNECTION_COUNT = 0x24

    # Device metrics (0x30-0x3F)
    BATTERY_LEVEL = 0x30
    UPTIME = 0x31
    ERROR_COUNT = 0x32

    # 5G NR700 (n28) metrics (0x40-0x4F)
    DL_THROUGHPUT_NR700 = 0x40
    UL_THROUGHPUT_NR700 = 0x41
    RSRP_NR700 = 0x42
    SINR_NR700 = 0x43

    # 5G NR3500 (n78) metrics (0x50-0x5F)
    DL_THROUGHPUT_NR3500 = 0x50
    UL_THROUGHPUT_NR3500 = 0x51
    RSRP_NR3500 = 0x52
    SINR_NR3500 = 0x53

    # 5G Radio metrics (0x60-0x6F)
    PDCP_THROUGHPUT = 0x60
    RLC_THROUGHPUT = 0x61
    INITIAL_BLER = 0x62
    AVG_MCS = 0x63
    RB_PER_SLOT = 0x64
    RANK_INDICATOR = 0x65

    # RF Quality metrics (0x70-0x7F)
    TX_IMBALANCE = 0x70
    LATENCY_PING = 0x71
    HANDOVER_SUCCESS_RATE = 0x72
    INTERFERENCE_LEVEL = 0x73

    # Carrier Aggregation (0x78-0x7F)
    CA_DL_THROUGHPUT = 0x78
    CA_UL_THROUGHPUT = 0x79

    # Power & Energy (0x80-0x8F)
    UTILITY_VOLTAGE_L1 = 0x80
    BATTERY_SOC = 0x86
    SITE_POWER_KWH = 0x8C

    # Environmental (0x90-0x9F)
    WIND_SPEED = 0x90
    DOOR_STATUS = 0x9A

    # Special
    ALL = 0xFF


class StatusCode(IntEnum):
    """Device status codes"""
    OK = 0x00
    WARNING = 0x01
    ERROR = 0x02
    CRITICAL = 0x03
    OFFLINE = 0x04


class CommandType(IntEnum):
    """Command types"""
    RESTART = 0x01
    SHUTDOWN = 0x02
    RESET_CONFIG = 0x03
    UPDATE_FIRMWARE = 0x04
    RUN_DIAGNOSTIC = 0x05
    SET_PARAMETER = 0x06


# CRC-16-CCITT lookup table (polynomial 0x1021, initial 0xFFFF)
CRC_TABLE = [
    0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50A5, 0x60C6, 0x70E7,
    0x8108, 0x9129, 0xA14A, 0xB16B, 0xC18C, 0xD1AD, 0xE1CE, 0xF1EF,
    0x1231, 0x0210, 0x3273, 0x2252, 0x52B5, 0x4294, 0x72F7, 0x62D6,
    0x9339, 0x8318, 0xB37B, 0xA35A, 0xD3BD, 0xC39C, 0xF3FF, 0xE3DE,
    0x2462, 0x3443, 0x0420, 0x1401, 0x64E6, 0x74C7, 0x44A4, 0x5485,
    0xA56A, 0xB54B, 0x8528, 0x9509, 0xE5EE, 0xF5CF, 0xC5AC, 0xD58D,
    0x3653, 0x2672, 0x1611, 0x0630, 0x76D7, 0x66F6, 0x5695, 0x46B4,
    0xB75B, 0xA77A, 0x9719, 0x8738, 0xF7DF, 0xE7FE, 0xD79D, 0xC7BC,
    0x48C4, 0x58E5, 0x6886, 0x78A7, 0x0840, 0x1861, 0x2802, 0x3823,
    0xC9CC, 0xD9ED, 0xE98E, 0xF9AF, 0x8948, 0x9969, 0xA90A, 0xB92B,
    0x5AF5, 0x4AD4, 0x7AB7, 0x6A96, 0x1A71, 0x0A50, 0x3A33, 0x2A12,
    0xDBFD, 0xCBDC, 0xFBBF, 0xEB9E, 0x9B79, 0x8B58, 0xBB3B, 0xAB1A,
    0x6CA6, 0x7C87, 0x4CE4, 0x5CC5, 0x2C22, 0x3C03, 0x0C60, 0x1C41,
    0xEDAE, 0xFD8F, 0xCDEC, 0xDDCD, 0xAD2A, 0xBD0B, 0x8D68, 0x9D49,
    0x7E97, 0x6EB6, 0x5ED5, 0x4EF4, 0x3E13, 0x2E32, 0x1E51, 0x0E70,
    0xFF9F, 0xEFBE, 0xDFDD, 0xCFFC, 0xBF1B, 0xAF3A, 0x9F59, 0x8F78,
    0x9188, 0x81A9, 0xB1CA, 0xA1EB, 0xD10C, 0xC12D, 0xF14E, 0xE16F,
    0x1080, 0x00A1, 0x30C2, 0x20E3, 0x5004, 0x4025, 0x7046, 0x6067,
    0x83B9, 0x9398, 0xA3FB, 0xB3DA, 0xC33D, 0xD31C, 0xE37F, 0xF35E,
    0x02B1, 0x1290, 0x22F3, 0x32D2, 0x4235, 0x5214, 0x6277, 0x7256,
    0xB5EA, 0xA5CB, 0x95A8, 0x8589, 0xF56E, 0xE54F, 0xD52C, 0xC50D,
    0x34E2, 0x24C3, 0x14A0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
    0xA7DB, 0xB7FA, 0x8799, 0x97B8, 0xE75F, 0xF77E, 0xC71D, 0xD73C,
    0x26D3, 0x36F2, 0x0691, 0x16B0, 0x6657, 0x7676, 0x4615, 0x5634,
    0xD94C, 0xC96D, 0xF90E, 0xE92F, 0x99C8, 0x89E9, 0xB98A, 0xA9AB,
    0x5844, 0x4865, 0x7806, 0x6827, 0x18C0, 0x08E1, 0x3882, 0x28A3,
    0xCB7D, 0xDB5C, 0xEB3F, 0xFB1E, 0x8BF9, 0x9BD8, 0xABBB, 0xBB9A,
    0x4A75, 0x5A54, 0x6A37, 0x7A16, 0x0AF1, 0x1AD0, 0x2AB3, 0x3A92,
    0xFD2E, 0xED0F, 0xDD6C, 0xCD4D, 0xBDAA, 0xAD8B, 0x9DE8, 0x8DC9,
    0x7C26, 0x6C07, 0x5C64, 0x4C45, 0x3CA2, 0x2C83, 0x1CE0, 0x0CC1,
    0xEF1F, 0xFF3E, 0xCF5D, 0xDF7C, 0xAF9B, 0xBFBA, 0x8FD9, 0x9FF8,
    0x6E17, 0x7E36, 0x4E55, 0x5E74, 0x2E93, 0x3EB2, 0x0ED1, 0x1EF0,
]


def calculate_crc16(data: bytes) -> int:
    """Calculate CRC-16-CCITT checksum"""
    crc = 0xFFFF
    for byte in data:
        crc = ((crc << 8) & 0xFFFF) ^ CRC_TABLE[(crc >> 8) ^ byte]
    return crc


@dataclass
class Message:
    """Protocol message"""
    msg_type: MessageType
    sequence: int
    payload: bytes = b''

    def is_request(self) -> bool:
        return 0x01 <= self.msg_type <= 0x07

    def is_response(self) -> bool:
        return 0x81 <= self.msg_type <= 0x86

    def is_event(self) -> bool:
        return self.msg_type >= 0xA0


@dataclass
class Metric:
    """Single metric value"""
    metric_type: MetricType
    value: float


@dataclass
class StatusPayload:
    """Device status payload"""
    status: StatusCode
    uptime: int  # seconds
    errors: int
    warnings: int


@dataclass
class CommandResult:
    """Command execution result"""
    success: bool
    return_code: int
    output: str


def encode_metrics(metrics: List[Metric]) -> bytes:
    """Encode metrics to wire format (5 bytes per metric)"""
    result = bytearray()
    for m in metrics:
        result.append(m.metric_type)
        result.extend(struct.pack('>f', m.value))  # big-endian float32
    return bytes(result)


def decode_metrics(data: bytes) -> List[Metric]:
    """Decode metrics from wire format"""
    metrics = []
    offset = 0
    while offset + 5 <= len(data):
        metric_type = MetricType(data[offset])
        value = struct.unpack('>f', data[offset + 1:offset + 5])[0]
        metrics.append(Metric(metric_type, value))
        offset += 5
    return metrics


def encode_status(status: StatusPayload) -> bytes:
    """Encode status payload (9 bytes)"""
    return struct.pack('>BIHH',
                       status.status,
                       status.uptime,
                       status.errors,
                       status.warnings)


def encode_command_result(result: CommandResult) -> bytes:
    """Encode command result"""
    output_bytes = result.output.encode('utf-8')
    return struct.pack('>BB', 0x00 if result.success else 0x01, result.return_code) + output_bytes


def build_frame(msg: Message) -> bytes:
    """Build wire frame from message"""
    payload = msg.payload if msg.payload else b''
    payload_len = len(payload)

    if payload_len > MAX_PAYLOAD_SIZE:
        raise ValueError(f"Payload too large: {payload_len} > {MAX_PAYLOAD_SIZE}")

    # Build frame without CRC
    frame = bytearray()
    frame.append(HEADER_BYTE0)
    frame.append(HEADER_BYTE1)
    frame.extend(struct.pack('>H', payload_len))  # length big-endian
    frame.append(msg.msg_type)
    frame.append(msg.sequence)
    frame.extend(payload)

    # Calculate and append CRC
    crc = calculate_crc16(bytes(frame))
    frame.extend(struct.pack('>H', crc))

    return bytes(frame)


class FrameParser:
    """State machine frame parser"""

    def __init__(self):
        self.reset()

    def reset(self):
        self.buffer = bytearray()
        self.state = 'IDLE'
        self.payload_len = 0
        self.crc_errors = 0

    def parse(self, data: bytes) -> List[Message]:
        """Parse incoming data and return complete messages"""
        messages = []
        for byte in data:
            msg = self._parse_byte(byte)
            if msg:
                messages.append(msg)
        return messages

    def _parse_byte(self, byte: int) -> Optional[Message]:
        if self.state == 'IDLE':
            if byte == HEADER_BYTE0:
                self.buffer = bytearray([byte])
                self.state = 'HEADER1'
        elif self.state == 'HEADER1':
            if byte == HEADER_BYTE1:
                self.buffer.append(byte)
                self.state = 'LENGTH'
            elif byte == HEADER_BYTE0:
                self.buffer = bytearray([byte])
            else:
                self.state = 'IDLE'
        elif self.state == 'LENGTH':
            self.buffer.append(byte)
            if len(self.buffer) == 4:
                self.payload_len = struct.unpack('>H', self.buffer[2:4])[0]
                if self.payload_len > MAX_PAYLOAD_SIZE:
                    self.state = 'IDLE'
                else:
                    self.state = 'TYPE'
        elif self.state == 'TYPE':
            self.buffer.append(byte)
            self.state = 'SEQUENCE'
        elif self.state == 'SEQUENCE':
            self.buffer.append(byte)
            if self.payload_len > 0:
                self.state = 'PAYLOAD'
            else:
                self.state = 'CRC'
        elif self.state == 'PAYLOAD':
            self.buffer.append(byte)
            if len(self.buffer) >= HEADER_SIZE + self.payload_len:
                self.state = 'CRC'
        elif self.state == 'CRC':
            self.buffer.append(byte)
            if len(self.buffer) >= HEADER_SIZE + self.payload_len + CRC_SIZE:
                return self._verify_and_extract()
        return None

    def _verify_and_extract(self) -> Optional[Message]:
        frame_len = HEADER_SIZE + self.payload_len
        expected_crc = calculate_crc16(bytes(self.buffer[:frame_len]))
        actual_crc = struct.unpack('>H', self.buffer[frame_len:frame_len + CRC_SIZE])[0]

        self.state = 'IDLE'

        if expected_crc != actual_crc:
            self.crc_errors += 1
            logger.warning(f"CRC mismatch: expected 0x{expected_crc:04X}, got 0x{actual_crc:04X}")
            return None

        msg_type = MessageType(self.buffer[4])
        sequence = self.buffer[5]
        payload = bytes(self.buffer[HEADER_SIZE:HEADER_SIZE + self.payload_len]) if self.payload_len > 0 else b''

        return Message(msg_type, sequence, payload)


class DeviceProtocolServer:
    """
    TCP server implementing the device protocol.
    Handles requests from edge-bridge and responds with device state.
    """

    def __init__(self, host: str = '0.0.0.0', port: int = 8888):
        self.host = host
        self.port = port
        self.running = False
        self.server_socket: Optional[socket.socket] = None
        self.clients: List[socket.socket] = []
        self.lock = threading.Lock()

        # Callbacks for getting device state
        self.get_metrics_callback: Optional[Callable[[List[MetricType]], List[Metric]]] = None
        self.get_status_callback: Optional[Callable[[], StatusPayload]] = None
        self.execute_command_callback: Optional[Callable[[CommandType, bytes], CommandResult]] = None

    def set_metrics_callback(self, callback: Callable[[List[MetricType]], List[Metric]]):
        """Set callback to get current metrics"""
        self.get_metrics_callback = callback

    def set_status_callback(self, callback: Callable[[], StatusPayload]):
        """Set callback to get device status"""
        self.get_status_callback = callback

    def set_command_callback(self, callback: Callable[[CommandType, bytes], CommandResult]):
        """Set callback to execute commands"""
        self.execute_command_callback = callback

    def start(self):
        """Start the protocol server"""
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((self.host, self.port))
        self.server_socket.listen(5)
        self.server_socket.settimeout(1.0)
        self.running = True

        logger.info(f"Device protocol server listening on {self.host}:{self.port}")

        thread = threading.Thread(target=self._accept_loop, daemon=True)
        thread.start()

    def stop(self):
        """Stop the protocol server"""
        self.running = False
        with self.lock:
            for client in self.clients:
                try:
                    client.close()
                except Exception:
                    pass
            self.clients.clear()
        if self.server_socket:
            self.server_socket.close()
        logger.info("Device protocol server stopped")

    def _accept_loop(self):
        """Accept incoming connections"""
        while self.running:
            try:
                client_socket, addr = self.server_socket.accept()
                logger.info(f"Edge bridge connected from {addr}")
                with self.lock:
                    self.clients.append(client_socket)
                thread = threading.Thread(
                    target=self._handle_client,
                    args=(client_socket, addr),
                    daemon=True
                )
                thread.start()
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    logger.error(f"Accept error: {e}")

    def _handle_client(self, client_socket: socket.socket, addr):
        """Handle a single client connection"""
        parser = FrameParser()
        client_socket.settimeout(1.0)

        try:
            while self.running:
                try:
                    data = client_socket.recv(4096)
                    if not data:
                        break

                    messages = parser.parse(data)
                    for msg in messages:
                        response = self._handle_message(msg)
                        if response:
                            frame = build_frame(response)
                            client_socket.sendall(frame)

                except socket.timeout:
                    continue
                except Exception as e:
                    logger.error(f"Error handling client {addr}: {e}")
                    break
        finally:
            logger.info(f"Edge bridge disconnected: {addr}")
            with self.lock:
                if client_socket in self.clients:
                    self.clients.remove(client_socket)
            client_socket.close()

    def _handle_message(self, msg: Message) -> Optional[Message]:
        """Process incoming message and generate response"""
        logger.debug(f"Received: type=0x{msg.msg_type:02X}, seq={msg.sequence}, payload_len={len(msg.payload)}")

        if msg.msg_type == MessageType.PING:
            return Message(MessageType.PONG, msg.sequence)

        elif msg.msg_type == MessageType.REQUEST_METRICS:
            return self._handle_metrics_request(msg)

        elif msg.msg_type == MessageType.GET_STATUS:
            return self._handle_status_request(msg)

        elif msg.msg_type == MessageType.EXECUTE_COMMAND:
            return self._handle_command_request(msg)

        else:
            logger.warning(f"Unknown message type: 0x{msg.msg_type:02X}")
            return None

    def _handle_metrics_request(self, msg: Message) -> Message:
        """Handle metrics request"""
        # Parse requested metric types from payload
        requested_types = []
        if msg.payload:
            for byte in msg.payload:
                if byte == MetricType.ALL:
                    requested_types = []  # Empty means all
                    break
                try:
                    requested_types.append(MetricType(byte))
                except ValueError:
                    pass

        # Get metrics from callback
        if self.get_metrics_callback:
            metrics = self.get_metrics_callback(requested_types)
        else:
            metrics = []

        payload = encode_metrics(metrics)
        logger.debug(f"Responding with {len(metrics)} metrics")
        return Message(MessageType.METRICS_RESPONSE, msg.sequence, payload)

    def _handle_status_request(self, msg: Message) -> Message:
        """Handle status request"""
        if self.get_status_callback:
            status = self.get_status_callback()
        else:
            status = StatusPayload(StatusCode.OK, 0, 0, 0)

        payload = encode_status(status)
        return Message(MessageType.STATUS_RESPONSE, msg.sequence, payload)

    def _handle_command_request(self, msg: Message) -> Message:
        """Handle command execution request"""
        if len(msg.payload) < 1:
            result = CommandResult(False, 1, "No command type specified")
        else:
            try:
                cmd_type = CommandType(msg.payload[0])
                params = msg.payload[1:] if len(msg.payload) > 1 else b''

                if self.execute_command_callback:
                    result = self.execute_command_callback(cmd_type, params)
                else:
                    result = CommandResult(False, 1, "Command execution not supported")
            except ValueError:
                result = CommandResult(False, 1, f"Unknown command type: {msg.payload[0]}")

        payload = encode_command_result(result)
        return Message(MessageType.COMMAND_RESULT, msg.sequence, payload)

    def send_event(self, event_type: MessageType, payload: bytes = b''):
        """Send an async event to all connected clients"""
        if not self.running:
            return

        msg = Message(event_type, 0, payload)
        frame = build_frame(msg)

        with self.lock:
            for client in self.clients[:]:
                try:
                    client.sendall(frame)
                except Exception as e:
                    logger.error(f"Error sending event: {e}")
