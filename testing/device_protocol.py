#!/usr/bin/env python3
"""
Device Communication Protocol for Base Station Platform
=========================================================
Defines the protocol for PC ↔ MIPS device communication.

This module provides:
- Protocol message definitions
- Serial/UART communication support
- Device simulator for testing without hardware
- Command/Response handling

Communication Flow:
  PC (Control System)                    Base Station (MIPS Device)
       │                                         │
       │──── REQUEST_METRICS ──────────────────>│
       │<─── METRICS_RESPONSE ──────────────────│
       │                                         │
       │──── EXECUTE_COMMAND ──────────────────>│
       │<─── COMMAND_RESULT ────────────────────│
       │                                         │
       │<─── ALERT_EVENT (async) ───────────────│
       │                                         │

Message Format (Binary):
  +--------+--------+--------+--------+--------+--------+
  | HEADER | LENGTH |  TYPE  |  SEQ   | PAYLOAD... | CRC |
  +--------+--------+--------+--------+--------+--------+
  | 2 bytes| 2 bytes| 1 byte | 1 byte | N bytes   | 2 b |
  +--------+--------+--------+--------+--------+--------+

Usage:
    # As a module
    from device_protocol import DeviceProtocol, SerialDeviceConnection, DeviceSimulator

    # Run simulator standalone
    python3 device_protocol.py --simulate --port /dev/pts/2
"""

import struct
import json
import time
import threading
import random
import argparse
from enum import IntEnum
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Callable, Any, Tuple
from datetime import datetime
from collections import deque
import socket

# Optional serial support
try:
    import serial
    SERIAL_AVAILABLE = True
except ImportError:
    SERIAL_AVAILABLE = False

# Optional PTY support for simulation
try:
    import pty
    import os
    PTY_AVAILABLE = True
except ImportError:
    PTY_AVAILABLE = False


# =============================================================================
# PROTOCOL DEFINITIONS
# =============================================================================

class MessageType(IntEnum):
    """Protocol message types"""
    # Requests (PC → Device)
    PING = 0x01
    REQUEST_METRICS = 0x02
    EXECUTE_COMMAND = 0x03
    SET_CONFIG = 0x04
    GET_STATUS = 0x05
    REBOOT = 0x06
    UPDATE_FIRMWARE = 0x07

    # Responses (Device → PC)
    PONG = 0x81
    METRICS_RESPONSE = 0x82
    COMMAND_RESULT = 0x83
    CONFIG_ACK = 0x84
    STATUS_RESPONSE = 0x85
    REBOOT_ACK = 0x86

    # Async Events (Device → PC)
    ALERT_EVENT = 0xA1
    THRESHOLD_EXCEEDED = 0xA2
    HARDWARE_FAULT = 0xA3
    CONNECTION_LOST = 0xA4


class MetricType(IntEnum):
    """Metric types that can be requested from device"""
    CPU_USAGE = 0x01
    MEMORY_USAGE = 0x02
    TEMPERATURE = 0x03
    HUMIDITY = 0x04
    FAN_SPEED = 0x05
    VOLTAGE = 0x06
    CURRENT = 0x07
    POWER = 0x08
    SIGNAL_STRENGTH = 0x10
    SIGNAL_QUALITY = 0x11
    INTERFERENCE = 0x12
    BER = 0x13
    VSWR = 0x14
    ANTENNA_TILT = 0x15
    THROUGHPUT = 0x20
    LATENCY = 0x21
    PACKET_LOSS = 0x22
    JITTER = 0x23
    CONNECTION_COUNT = 0x24
    BATTERY_LEVEL = 0x30
    UPTIME = 0x31
    ERROR_COUNT = 0x32
    ALL_METRICS = 0xFF


class CommandType(IntEnum):
    """Command types that can be executed on device"""
    RESTART_SERVICE = 0x01
    CLEAR_CACHE = 0x02
    ROTATE_LOGS = 0x03
    SET_FAN_SPEED = 0x04
    SET_POWER_MODE = 0x05
    CALIBRATE_ANTENNA = 0x06
    SWITCH_CHANNEL = 0x07
    ENABLE_FILTER = 0x08
    BLOCK_IP = 0x09
    RUN_DIAGNOSTIC = 0x0A
    CUSTOM_SHELL = 0xFF


class DeviceStatus(IntEnum):
    """Device status codes"""
    OK = 0x00
    WARNING = 0x01
    ERROR = 0x02
    CRITICAL = 0x03
    MAINTENANCE = 0x04
    OFFLINE = 0x05


# Protocol constants
PROTOCOL_HEADER = b'\xAA\x55'  # Magic bytes
PROTOCOL_VERSION = 0x01
MAX_PAYLOAD_SIZE = 4096
CRC_POLYNOMIAL = 0x1021  # CRC-16-CCITT


@dataclass
class ProtocolMessage:
    """A protocol message"""
    msg_type: MessageType
    sequence: int
    payload: bytes = b''
    timestamp: float = field(default_factory=time.time)

    def to_bytes(self) -> bytes:
        """Serialize message to bytes"""
        length = len(self.payload)
        header = struct.pack('>2sHBB', PROTOCOL_HEADER, length, self.msg_type, self.sequence)
        data = header + self.payload
        crc = calculate_crc16(data)
        return data + struct.pack('>H', crc)

    @classmethod
    def from_bytes(cls, data: bytes) -> Optional['ProtocolMessage']:
        """Deserialize message from bytes"""
        if len(data) < 8:  # Minimum message size
            return None

        header, length, msg_type, sequence = struct.unpack('>2sHBB', data[:6])
        if header != PROTOCOL_HEADER:
            return None

        if len(data) < 8 + length:
            return None

        payload = data[6:6+length]
        received_crc = struct.unpack('>H', data[6+length:8+length])[0]
        calculated_crc = calculate_crc16(data[:6+length])

        if received_crc != calculated_crc:
            return None

        return cls(
            msg_type=MessageType(msg_type),
            sequence=sequence,
            payload=payload
        )


def calculate_crc16(data: bytes) -> int:
    """Calculate CRC-16-CCITT checksum"""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = (crc << 1) ^ CRC_POLYNOMIAL
            else:
                crc <<= 1
            crc &= 0xFFFF
    return crc


# =============================================================================
# DEVICE PROTOCOL HANDLER
# =============================================================================

class DeviceProtocol:
    """
    Protocol handler for PC ↔ Device communication.
    Handles message encoding/decoding, request/response matching, and async events.
    """

    def __init__(self):
        self.sequence = 0
        self.pending_requests: Dict[int, Tuple[ProtocolMessage, float]] = {}
        self.event_handlers: Dict[MessageType, List[Callable]] = {}
        self.timeout = 5.0  # seconds

    def next_sequence(self) -> int:
        """Get next sequence number"""
        self.sequence = (self.sequence + 1) % 256
        return self.sequence

    def create_ping(self) -> ProtocolMessage:
        """Create a ping message"""
        return ProtocolMessage(
            msg_type=MessageType.PING,
            sequence=self.next_sequence()
        )

    def create_metrics_request(self, metric_types: List[MetricType] = None) -> ProtocolMessage:
        """Create a metrics request message"""
        if metric_types is None:
            metric_types = [MetricType.ALL_METRICS]

        payload = struct.pack(f'>{len(metric_types)}B', *[m.value for m in metric_types])
        return ProtocolMessage(
            msg_type=MessageType.REQUEST_METRICS,
            sequence=self.next_sequence(),
            payload=payload
        )

    def create_command(self, cmd_type: CommandType, params: bytes = b'') -> ProtocolMessage:
        """Create a command execution message"""
        payload = struct.pack('>B', cmd_type.value) + params
        return ProtocolMessage(
            msg_type=MessageType.EXECUTE_COMMAND,
            sequence=self.next_sequence(),
            payload=payload
        )

    def create_shell_command(self, command: str) -> ProtocolMessage:
        """Create a custom shell command message"""
        payload = struct.pack('>B', CommandType.CUSTOM_SHELL.value) + command.encode('utf-8')
        return ProtocolMessage(
            msg_type=MessageType.EXECUTE_COMMAND,
            sequence=self.next_sequence(),
            payload=payload
        )

    def create_status_request(self) -> ProtocolMessage:
        """Create a status request message"""
        return ProtocolMessage(
            msg_type=MessageType.GET_STATUS,
            sequence=self.next_sequence()
        )

    def parse_metrics_response(self, msg: ProtocolMessage) -> Dict[str, float]:
        """Parse metrics from response message"""
        metrics = {}
        if msg.msg_type != MessageType.METRICS_RESPONSE:
            return metrics

        payload = msg.payload
        offset = 0

        while offset < len(payload) - 4:
            metric_type = payload[offset]
            value = struct.unpack('>f', payload[offset+1:offset+5])[0]

            # Map metric type to name
            try:
                metric_name = MetricType(metric_type).name
                metrics[metric_name] = value
            except ValueError:
                metrics[f"UNKNOWN_{metric_type}"] = value

            offset += 5

        return metrics

    def parse_command_result(self, msg: ProtocolMessage) -> Dict[str, Any]:
        """Parse command result from response message"""
        if msg.msg_type != MessageType.COMMAND_RESULT:
            return {"success": False, "error": "Invalid message type"}

        if len(msg.payload) < 2:
            return {"success": False, "error": "Invalid payload"}

        success = msg.payload[0] == 0x00
        return_code = msg.payload[1]
        output = msg.payload[2:].decode('utf-8', errors='replace') if len(msg.payload) > 2 else ""

        return {
            "success": success,
            "return_code": return_code,
            "output": output
        }

    def parse_status_response(self, msg: ProtocolMessage) -> Dict[str, Any]:
        """Parse status from response message"""
        if msg.msg_type != MessageType.STATUS_RESPONSE:
            return {}

        if len(msg.payload) < 8:
            return {}

        status, uptime, error_count, warning_count = struct.unpack('>BIHH', msg.payload[:9])

        return {
            "status": DeviceStatus(status).name,
            "uptime_seconds": uptime,
            "error_count": error_count,
            "warning_count": warning_count
        }

    def register_event_handler(self, event_type: MessageType, handler: Callable):
        """Register handler for async events"""
        if event_type not in self.event_handlers:
            self.event_handlers[event_type] = []
        self.event_handlers[event_type].append(handler)

    def handle_event(self, msg: ProtocolMessage):
        """Handle an async event message"""
        handlers = self.event_handlers.get(msg.msg_type, [])
        for handler in handlers:
            try:
                handler(msg)
            except Exception as e:
                print(f"Event handler error: {e}")


# =============================================================================
# SERIAL DEVICE CONNECTION
# =============================================================================

class SerialDeviceConnection:
    """
    Serial/UART connection to MIPS device.
    Supports both real serial ports and PTY for testing.
    """

    def __init__(self, port: str, baudrate: int = 115200, timeout: float = 1.0):
        self.port = port
        self.baudrate = baudrate
        self.timeout = timeout
        self.serial: Optional['serial.Serial'] = None
        self.protocol = DeviceProtocol()
        self.connected = False
        self.read_buffer = bytearray()
        self.read_thread: Optional[threading.Thread] = None
        self.running = False
        self.message_queue: deque = deque(maxlen=100)

    def connect(self) -> bool:
        """Open serial connection"""
        if not SERIAL_AVAILABLE:
            print("pyserial not installed - run: pip install pyserial")
            return False

        try:
            self.serial = serial.Serial(
                port=self.port,
                baudrate=self.baudrate,
                timeout=self.timeout,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE
            )
            self.connected = True
            self.running = True
            self.read_thread = threading.Thread(target=self._read_loop, daemon=True)
            self.read_thread.start()
            return True
        except Exception as e:
            print(f"Serial connection failed: {e}")
            return False

    def disconnect(self):
        """Close serial connection"""
        self.running = False
        if self.read_thread:
            self.read_thread.join(timeout=2.0)
        if self.serial:
            self.serial.close()
        self.connected = False

    def _read_loop(self):
        """Background thread for reading serial data"""
        while self.running and self.serial:
            try:
                data = self.serial.read(1024)
                if data:
                    self.read_buffer.extend(data)
                    self._process_buffer()
            except Exception as e:
                if self.running:
                    print(f"Serial read error: {e}")
                break

    def _process_buffer(self):
        """Process read buffer for complete messages"""
        while len(self.read_buffer) >= 8:
            # Look for header
            header_pos = self.read_buffer.find(PROTOCOL_HEADER)
            if header_pos == -1:
                self.read_buffer.clear()
                break

            if header_pos > 0:
                # Discard data before header
                del self.read_buffer[:header_pos]

            if len(self.read_buffer) < 6:
                break

            # Get message length
            length = struct.unpack('>H', self.read_buffer[2:4])[0]
            total_length = 8 + length

            if len(self.read_buffer) < total_length:
                break

            # Extract and parse message
            msg_data = bytes(self.read_buffer[:total_length])
            del self.read_buffer[:total_length]

            msg = ProtocolMessage.from_bytes(msg_data)
            if msg:
                self.message_queue.append(msg)

                # Handle async events
                if msg.msg_type >= 0xA0:
                    self.protocol.handle_event(msg)

    def send(self, msg: ProtocolMessage) -> bool:
        """Send a message"""
        if not self.connected or not self.serial:
            return False

        try:
            self.serial.write(msg.to_bytes())
            return True
        except Exception as e:
            print(f"Serial write error: {e}")
            return False

    def receive(self, timeout: float = 5.0) -> Optional[ProtocolMessage]:
        """Receive a message (blocking with timeout)"""
        start = time.time()
        while time.time() - start < timeout:
            if self.message_queue:
                return self.message_queue.popleft()
            time.sleep(0.01)
        return None

    def request_metrics(self, metric_types: List[MetricType] = None) -> Dict[str, float]:
        """Request metrics from device"""
        msg = self.protocol.create_metrics_request(metric_types)
        if not self.send(msg):
            return {}

        response = self.receive()
        if response and response.msg_type == MessageType.METRICS_RESPONSE:
            return self.protocol.parse_metrics_response(response)
        return {}

    def execute_command(self, command: str) -> Dict[str, Any]:
        """Execute a shell command on device"""
        msg = self.protocol.create_shell_command(command)
        if not self.send(msg):
            return {"success": False, "error": "Send failed"}

        response = self.receive(timeout=30.0)  # Commands may take longer
        if response and response.msg_type == MessageType.COMMAND_RESULT:
            return self.protocol.parse_command_result(response)
        return {"success": False, "error": "No response"}

    def ping(self) -> bool:
        """Ping device to check connectivity"""
        msg = self.protocol.create_ping()
        if not self.send(msg):
            return False

        response = self.receive(timeout=2.0)
        return response is not None and response.msg_type == MessageType.PONG

    def get_status(self) -> Dict[str, Any]:
        """Get device status"""
        msg = self.protocol.create_status_request()
        if not self.send(msg):
            return {}

        response = self.receive()
        if response and response.msg_type == MessageType.STATUS_RESPONSE:
            return self.protocol.parse_status_response(response)
        return {}


# =============================================================================
# DEVICE SIMULATOR
# =============================================================================

class DeviceSimulator:
    """
    Simulates a MIPS base station device for testing.
    Generates realistic metrics and responds to commands.
    """

    def __init__(self, station_id: int = 1, station_name: str = "Simulated Station"):
        self.station_id = station_id
        self.station_name = station_name
        self.protocol = DeviceProtocol()
        self.running = False
        self.server_socket: Optional[socket.socket] = None
        self.serial_fd: Optional[int] = None

        # Simulated device state
        self.state = {
            "status": DeviceStatus.OK,
            "uptime": 0,
            "errors": 0,
            "warnings": 0,
            "fan_speed": 3000,
            "power_mode": "normal"
        }

        # Base values for metrics (with realistic ranges)
        self.metric_bases = {
            MetricType.CPU_USAGE: (25, 15),          # mean, stddev
            MetricType.MEMORY_USAGE: (45, 10),
            MetricType.TEMPERATURE: (55, 8),
            MetricType.HUMIDITY: (40, 10),
            MetricType.FAN_SPEED: (3000, 200),
            MetricType.VOLTAGE: (48, 0.5),
            MetricType.CURRENT: (15, 2),
            MetricType.POWER: (720, 50),
            MetricType.SIGNAL_STRENGTH: (-75, 5),
            MetricType.SIGNAL_QUALITY: (85, 8),
            MetricType.INTERFERENCE: (-90, 5),
            MetricType.BER: (0.0001, 0.00005),
            MetricType.VSWR: (1.3, 0.1),
            MetricType.ANTENNA_TILT: (0, 1),
            MetricType.THROUGHPUT: (80, 15),
            MetricType.LATENCY: (15, 8),
            MetricType.PACKET_LOSS: (0.1, 0.1),
            MetricType.JITTER: (5, 3),
            MetricType.CONNECTION_COUNT: (150, 50),
            MetricType.BATTERY_LEVEL: (95, 5),
            MetricType.UPTIME: (720, 0),  # hours
            MetricType.ERROR_COUNT: (2, 2),
        }

        # Simulated anomalies (can be triggered for testing)
        self.anomalies = {
            "high_cpu": False,
            "high_temp": False,
            "low_signal": False,
            "high_ber": False,
            "fan_failure": False,
        }

    def generate_metric(self, metric_type: MetricType) -> float:
        """Generate a realistic metric value"""
        if metric_type not in self.metric_bases:
            return 0.0

        mean, stddev = self.metric_bases[metric_type]
        value = random.gauss(mean, stddev)

        # Apply anomalies
        if self.anomalies["high_cpu"] and metric_type == MetricType.CPU_USAGE:
            value += 50
        if self.anomalies["high_temp"] and metric_type == MetricType.TEMPERATURE:
            value += 30
        if self.anomalies["low_signal"] and metric_type == MetricType.SIGNAL_STRENGTH:
            value -= 25
        if self.anomalies["high_ber"] and metric_type == MetricType.BER:
            value *= 100
        if self.anomalies["fan_failure"] and metric_type == MetricType.FAN_SPEED:
            value = 500

        # Clamp to realistic ranges
        if metric_type == MetricType.CPU_USAGE:
            value = max(0, min(100, value))
        elif metric_type == MetricType.MEMORY_USAGE:
            value = max(0, min(100, value))
        elif metric_type == MetricType.BATTERY_LEVEL:
            value = max(0, min(100, value))
        elif metric_type == MetricType.SIGNAL_STRENGTH:
            value = max(-120, min(-30, value))

        return value

    def generate_all_metrics(self) -> Dict[MetricType, float]:
        """Generate all metrics"""
        return {mt: self.generate_metric(mt) for mt in self.metric_bases}

    def handle_message(self, msg: ProtocolMessage) -> Optional[ProtocolMessage]:
        """Handle incoming message and generate response"""

        if msg.msg_type == MessageType.PING:
            return ProtocolMessage(
                msg_type=MessageType.PONG,
                sequence=msg.sequence
            )

        elif msg.msg_type == MessageType.REQUEST_METRICS:
            metrics = self.generate_all_metrics()
            payload = bytearray()
            for metric_type, value in metrics.items():
                payload.append(metric_type.value)
                payload.extend(struct.pack('>f', value))

            return ProtocolMessage(
                msg_type=MessageType.METRICS_RESPONSE,
                sequence=msg.sequence,
                payload=bytes(payload)
            )

        elif msg.msg_type == MessageType.EXECUTE_COMMAND:
            # Simulate command execution
            if len(msg.payload) < 1:
                return self._command_result(msg.sequence, False, 1, "Invalid command")

            cmd_type = msg.payload[0]
            params = msg.payload[1:].decode('utf-8', errors='replace')

            # Simulate different commands
            if cmd_type == CommandType.CUSTOM_SHELL.value:
                # Simulate shell command
                output = f"Simulated execution of: {params}\nOK"
                return self._command_result(msg.sequence, True, 0, output)

            elif cmd_type == CommandType.SET_FAN_SPEED.value:
                self.state["fan_speed"] = int(params) if params.isdigit() else 3000
                return self._command_result(msg.sequence, True, 0, f"Fan speed set to {self.state['fan_speed']}")

            elif cmd_type == CommandType.RESTART_SERVICE.value:
                return self._command_result(msg.sequence, True, 0, f"Service restarted: {params}")

            else:
                return self._command_result(msg.sequence, True, 0, "Command executed")

        elif msg.msg_type == MessageType.GET_STATUS:
            payload = struct.pack('>BIHH',
                self.state["status"].value,
                int(self.state["uptime"] * 3600),  # Convert hours to seconds
                self.state["errors"],
                self.state["warnings"]
            )
            return ProtocolMessage(
                msg_type=MessageType.STATUS_RESPONSE,
                sequence=msg.sequence,
                payload=payload
            )

        return None

    def _command_result(self, sequence: int, success: bool, code: int, output: str) -> ProtocolMessage:
        """Create a command result response"""
        payload = struct.pack('>BB', 0x00 if success else 0x01, code) + output.encode('utf-8')
        return ProtocolMessage(
            msg_type=MessageType.COMMAND_RESULT,
            sequence=sequence,
            payload=payload
        )

    def trigger_anomaly(self, anomaly: str, enable: bool = True):
        """Trigger or clear an anomaly for testing"""
        if anomaly in self.anomalies:
            self.anomalies[anomaly] = enable
            print(f"Anomaly '{anomaly}' {'enabled' if enable else 'disabled'}")

    def start_tcp_server(self, host: str = "127.0.0.1", port: int = 9999):
        """Start TCP server for testing (simulates serial-over-TCP)"""
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((host, port))
        self.server_socket.listen(1)
        self.running = True

        print(f"Device simulator listening on {host}:{port}")

        while self.running:
            try:
                self.server_socket.settimeout(1.0)
                try:
                    conn, addr = self.server_socket.accept()
                except socket.timeout:
                    continue

                print(f"Connection from {addr}")
                self._handle_connection(conn)

            except Exception as e:
                if self.running:
                    print(f"Server error: {e}")
                break

    def _handle_connection(self, conn: socket.socket):
        """Handle a client connection"""
        buffer = bytearray()

        while self.running:
            try:
                conn.settimeout(1.0)
                try:
                    data = conn.recv(4096)
                except socket.timeout:
                    continue

                if not data:
                    break

                buffer.extend(data)

                # Process complete messages
                while len(buffer) >= 8:
                    header_pos = buffer.find(PROTOCOL_HEADER)
                    if header_pos == -1:
                        buffer.clear()
                        break

                    if header_pos > 0:
                        del buffer[:header_pos]

                    if len(buffer) < 6:
                        break

                    length = struct.unpack('>H', buffer[2:4])[0]
                    total_length = 8 + length

                    if len(buffer) < total_length:
                        break

                    msg_data = bytes(buffer[:total_length])
                    del buffer[:total_length]

                    msg = ProtocolMessage.from_bytes(msg_data)
                    if msg:
                        response = self.handle_message(msg)
                        if response:
                            conn.send(response.to_bytes())

            except Exception as e:
                print(f"Connection error: {e}")
                break

        conn.close()

    def stop(self):
        """Stop the simulator"""
        self.running = False
        if self.server_socket:
            self.server_socket.close()


# =============================================================================
# TCP DEVICE CONNECTION (for testing with simulator)
# =============================================================================

class TCPDeviceConnection:
    """
    TCP connection to device simulator or serial-over-TCP.
    Useful for testing without real hardware.
    """

    def __init__(self, host: str = "127.0.0.1", port: int = 9999, timeout: float = 5.0):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.socket: Optional[socket.socket] = None
        self.protocol = DeviceProtocol()
        self.connected = False

    def connect(self) -> bool:
        """Connect to device"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.settimeout(self.timeout)
            self.socket.connect((self.host, self.port))
            self.connected = True
            return True
        except Exception as e:
            print(f"TCP connection failed: {e}")
            return False

    def disconnect(self):
        """Disconnect from device"""
        if self.socket:
            self.socket.close()
        self.connected = False

    def send(self, msg: ProtocolMessage) -> bool:
        """Send a message"""
        if not self.connected or not self.socket:
            return False

        try:
            self.socket.send(msg.to_bytes())
            return True
        except Exception as e:
            print(f"TCP send error: {e}")
            return False

    def receive(self, timeout: float = None) -> Optional[ProtocolMessage]:
        """Receive a message"""
        if not self.connected or not self.socket:
            return None

        try:
            self.socket.settimeout(timeout or self.timeout)
            data = self.socket.recv(4096)
            if data:
                return ProtocolMessage.from_bytes(data)
        except socket.timeout:
            pass
        except Exception as e:
            print(f"TCP receive error: {e}")

        return None

    def request_metrics(self) -> Dict[str, float]:
        """Request all metrics from device"""
        msg = self.protocol.create_metrics_request()
        if not self.send(msg):
            return {}

        response = self.receive()
        if response and response.msg_type == MessageType.METRICS_RESPONSE:
            return self.protocol.parse_metrics_response(response)
        return {}

    def execute_command(self, command: str) -> Dict[str, Any]:
        """Execute a shell command on device"""
        msg = self.protocol.create_shell_command(command)
        if not self.send(msg):
            return {"success": False, "error": "Send failed"}

        response = self.receive(timeout=30.0)
        if response and response.msg_type == MessageType.COMMAND_RESULT:
            return self.protocol.parse_command_result(response)
        return {"success": False, "error": "No response"}

    def ping(self) -> bool:
        """Ping device"""
        msg = self.protocol.create_ping()
        if not self.send(msg):
            return False

        response = self.receive(timeout=2.0)
        return response is not None and response.msg_type == MessageType.PONG

    def get_status(self) -> Dict[str, Any]:
        """Get device status"""
        msg = self.protocol.create_status_request()
        if not self.send(msg):
            return {}

        response = self.receive()
        if response and response.msg_type == MessageType.STATUS_RESPONSE:
            return self.protocol.parse_status_response(response)
        return {}


# =============================================================================
# MAIN (for standalone testing)
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description="Device Protocol Utility")
    parser.add_argument("--simulate", action="store_true", help="Run device simulator")
    parser.add_argument("--host", default="127.0.0.1", help="Host for simulator")
    parser.add_argument("--port", type=int, default=9999, help="Port for simulator")
    parser.add_argument("--test", action="store_true", help="Run test client")
    parser.add_argument("--anomaly", help="Trigger anomaly: high_cpu, high_temp, low_signal, high_ber, fan_failure")

    args = parser.parse_args()

    if args.simulate:
        # Run simulator
        sim = DeviceSimulator(station_id=1, station_name="Test Station")

        if args.anomaly:
            sim.trigger_anomaly(args.anomaly)

        try:
            sim.start_tcp_server(args.host, args.port)
        except KeyboardInterrupt:
            print("\nShutting down simulator...")
            sim.stop()

    elif args.test:
        # Run test client
        print(f"Connecting to {args.host}:{args.port}...")
        client = TCPDeviceConnection(args.host, args.port)

        if not client.connect():
            print("Failed to connect")
            return

        print("Connected!")

        # Test ping
        print("\nTesting ping...")
        if client.ping():
            print("  Pong received!")
        else:
            print("  No response")

        # Test metrics
        print("\nRequesting metrics...")
        metrics = client.request_metrics()
        for name, value in sorted(metrics.items()):
            print(f"  {name}: {value:.4f}")

        # Test status
        print("\nRequesting status...")
        status = client.get_status()
        for key, value in status.items():
            print(f"  {key}: {value}")

        # Test command
        print("\nExecuting test command...")
        result = client.execute_command("echo 'Hello from device'")
        print(f"  Success: {result.get('success')}")
        print(f"  Output: {result.get('output')}")

        client.disconnect()
        print("\nDisconnected")

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
