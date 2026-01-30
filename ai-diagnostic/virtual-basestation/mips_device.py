#!/usr/bin/env python3
"""
MIPS Virtual Base Station Device

A proper virtual base station that speaks the binary protocol expected by the
edge bridge. Generates realistic metrics including 5G NR bands and responds
to protocol requests.

Protocol format:
  Header: 0xAA 0x55
  Length: 2 bytes (big-endian) - payload length
  Type: 1 byte
  Sequence: 1 byte
  Payload: variable
  CRC-16: 2 bytes (big-endian, CCITT)
"""

import socket
import struct
import time
import random
import math
import threading
import logging
import argparse
from dataclasses import dataclass
from enum import IntEnum
from typing import Optional, Callable

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)


# Protocol constants
HEADER_BYTE0 = 0xAA
HEADER_BYTE1 = 0x55
HEADER_SIZE = 6
CRC_SIZE = 2
MAX_PAYLOAD = 4096


class MsgType(IntEnum):
    # Requests
    PING = 0x01
    REQUEST_METRICS = 0x02
    GET_STATUS = 0x03
    SET_CONFIG = 0x04
    EXECUTE_COMMAND = 0x05
    START_STREAM = 0x06
    STOP_STREAM = 0x07
    # Responses
    PONG = 0x81
    METRICS_RESPONSE = 0x82
    STATUS_RESPONSE = 0x83
    CONFIG_ACK = 0x84
    COMMAND_RESULT = 0x85
    STREAM_ACK = 0x86
    # Events
    METRICS_EVENT = 0xA1
    THRESHOLD_EXCEEDED = 0xA2
    DEVICE_STATE_CHANGE = 0xA3
    ERROR = 0xA4


class MetricType(IntEnum):
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
    # Carrier Aggregation
    CA_DL_THROUGHPUT = 0x78
    CA_UL_THROUGHPUT = 0x79
    # Special
    ALL = 0xFF


class StatusCode(IntEnum):
    OK = 0x00
    WARNING = 0x01
    ERROR = 0x02
    CRITICAL = 0x03
    OFFLINE = 0x04


# CRC-16 CCITT lookup table
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


def crc16_ccitt(data: bytes) -> int:
    """Calculate CRC-16 CCITT."""
    crc = 0xFFFF
    for byte in data:
        crc = ((crc << 8) & 0xFFFF) ^ CRC_TABLE[((crc >> 8) ^ byte) & 0xFF]
    return crc


def build_frame(msg_type: int, sequence: int, payload: bytes = b'') -> bytes:
    """Build a protocol frame."""
    # Header
    frame = bytearray([HEADER_BYTE0, HEADER_BYTE1])
    # Length (big-endian)
    frame.extend(struct.pack('>H', len(payload)))
    # Type and sequence
    frame.append(msg_type)
    frame.append(sequence)
    # Payload
    frame.extend(payload)
    # CRC-16 over header + payload
    crc = crc16_ccitt(bytes(frame))
    frame.extend(struct.pack('>H', crc))
    return bytes(frame)


def parse_frame(data: bytes) -> Optional[tuple]:
    """Parse a protocol frame. Returns (msg_type, sequence, payload) or None."""
    if len(data) < HEADER_SIZE + CRC_SIZE:
        return None
    if data[0] != HEADER_BYTE0 or data[1] != HEADER_BYTE1:
        return None

    payload_len = struct.unpack('>H', data[2:4])[0]
    expected_len = HEADER_SIZE + payload_len + CRC_SIZE

    if len(data) < expected_len:
        return None

    msg_type = data[4]
    sequence = data[5]
    payload = data[6:6+payload_len]

    # Verify CRC
    received_crc = struct.unpack('>H', data[6+payload_len:8+payload_len])[0]
    calculated_crc = crc16_ccitt(data[:6+payload_len])

    if received_crc != calculated_crc:
        logger.warning(f"CRC mismatch: received 0x{received_crc:04X}, calculated 0x{calculated_crc:04X}")
        return None

    return (msg_type, sequence, payload)


@dataclass
class DeviceState:
    """Current state of the virtual MIPS device."""
    station_id: str
    uptime_seconds: int = 0
    errors: int = 0
    warnings: int = 0
    status: StatusCode = StatusCode.OK

    # System metrics
    cpu_usage: float = 35.0
    memory_usage: float = 45.0
    temperature: float = 42.0
    power_consumption: float = 1500.0

    # RF metrics
    signal_strength: float = -65.0

    # 5G NR3500 (n78 - 3.5GHz high-speed band)
    dl_throughput_nr3500: float = 1200.0  # Mbps
    ul_throughput_nr3500: float = 85.0    # Mbps
    rsrp_nr3500: float = -78.0            # dBm
    sinr_nr3500: float = 18.0             # dB

    # 5G NR700 (n28 - 700MHz coverage band)
    dl_throughput_nr700: float = 65.0     # Mbps
    ul_throughput_nr700: float = 25.0     # Mbps
    rsrp_nr700: float = -82.0             # dBm
    sinr_nr700: float = 12.0              # dB

    # Quality metrics
    latency_ping: float = 8.5             # ms
    tx_imbalance: float = 1.2             # dB

    # Stress mode
    stress_mode: bool = False
    stress_type: str = ""  # "cpu", "temperature", "signal", "throughput"


# Thresholds for alerts
THRESHOLDS = {
    MetricType.CPU_USAGE: {"warning": 70, "critical": 85},
    MetricType.MEMORY_USAGE: {"warning": 75, "critical": 90},
    MetricType.TEMPERATURE: {"warning": 60, "critical": 75},
    MetricType.RSRP_NR3500: {"warning": -90, "critical": -100},  # Lower is worse
    MetricType.RSRP_NR700: {"warning": -95, "critical": -105},
    MetricType.SINR_NR3500: {"warning": 8, "critical": 3},  # Lower is worse
    MetricType.SINR_NR700: {"warning": 5, "critical": 0},
    MetricType.LATENCY_PING: {"warning": 20, "critical": 50},  # Higher is worse
}


class MIPSDevice:
    """Virtual MIPS base station device."""

    def __init__(self, station_id: str, port: int = 9999):
        self.station_id = station_id
        self.port = port
        self.state = DeviceState(station_id=station_id)
        self.running = False
        self.server_socket: Optional[socket.socket] = None
        self.start_time = time.time()
        self.connected_clients: list = []
        self.alert_sequence = 0
        self.last_alert_time = 0

    def simulate_metrics(self):
        """Update metrics with realistic variations."""
        hour = time.localtime().tm_hour

        # Time-based load factor (higher during business hours)
        if 8 <= hour <= 18:
            load_factor = 1.2 + 0.1 * math.sin((hour - 8) * math.pi / 10)
        elif 0 <= hour <= 6:
            load_factor = 0.7
        else:
            load_factor = 0.9

        # Stress mode overrides
        if self.state.stress_mode:
            if self.state.stress_type == "cpu":
                self.state.cpu_usage = random.uniform(88, 95)
                self.state.temperature = random.uniform(72, 80)
                self.state.status = StatusCode.WARNING
            elif self.state.stress_type == "temperature":
                self.state.temperature = random.uniform(78, 85)
                self.state.cpu_usage = random.uniform(60, 75)
                self.state.status = StatusCode.CRITICAL
            elif self.state.stress_type == "signal":
                self.state.rsrp_nr3500 = random.uniform(-98, -105)
                self.state.rsrp_nr700 = random.uniform(-102, -110)
                self.state.sinr_nr3500 = random.uniform(2, 6)
                self.state.sinr_nr700 = random.uniform(-1, 3)
                self.state.status = StatusCode.WARNING
            elif self.state.stress_type == "throughput":
                self.state.dl_throughput_nr3500 = random.uniform(200, 400)
                self.state.ul_throughput_nr3500 = random.uniform(15, 30)
                self.state.latency_ping = random.uniform(35, 60)
                self.state.status = StatusCode.WARNING
            return  # Skip normal simulation

        # System metrics with realistic patterns
        self.state.cpu_usage = max(15, min(85,
            35 * load_factor + random.uniform(-5, 8)))
        self.state.memory_usage = max(30, min(80,
            45 + random.uniform(-3, 5)))
        self.state.temperature = max(35, min(70,
            42 + self.state.cpu_usage * 0.2 + random.uniform(-2, 3)))
        self.state.power_consumption = max(1000, min(3500,
            1200 + self.state.cpu_usage * 15 + random.uniform(-50, 50)))

        # RF signal varies slightly
        self.state.signal_strength = max(-90, min(-50,
            -65 + random.uniform(-5, 5)))

        # 5G NR3500 (n78) - high speed band, more variable throughput
        self.state.dl_throughput_nr3500 = max(500, min(2000,
            1200 * load_factor + random.uniform(-100, 150)))
        self.state.ul_throughput_nr3500 = max(40, min(150,
            85 * load_factor + random.uniform(-10, 15)))
        self.state.rsrp_nr3500 = max(-95, min(-65,
            -78 + random.uniform(-5, 5)))
        self.state.sinr_nr3500 = max(5, min(30,
            18 + random.uniform(-3, 3)))

        # 5G NR700 (n28) - coverage band, more stable
        self.state.dl_throughput_nr700 = max(30, min(100,
            65 * load_factor + random.uniform(-8, 10)))
        self.state.ul_throughput_nr700 = max(10, min(40,
            25 * load_factor + random.uniform(-3, 5)))
        self.state.rsrp_nr700 = max(-100, min(-70,
            -82 + random.uniform(-4, 4)))
        self.state.sinr_nr700 = max(3, min(20,
            12 + random.uniform(-2, 2)))

        # Quality metrics
        self.state.latency_ping = max(3, min(25,
            8.5 + random.uniform(-2, 3)))
        self.state.tx_imbalance = max(0.5, min(4,
            1.2 + random.uniform(-0.3, 0.5)))

        # Update uptime
        self.state.uptime_seconds = int(time.time() - self.start_time)

    def get_metrics_payload(self, requested_types: list = None) -> bytes:
        """Build metrics response payload."""
        self.simulate_metrics()

        # Map metric types to values
        metrics_map = {
            MetricType.CPU_USAGE: self.state.cpu_usage,
            MetricType.MEMORY_USAGE: self.state.memory_usage,
            MetricType.TEMPERATURE: self.state.temperature,
            MetricType.POWER_CONSUMPTION: self.state.power_consumption,
            MetricType.SIGNAL_STRENGTH: self.state.signal_strength,
            MetricType.DL_THROUGHPUT_NR3500: self.state.dl_throughput_nr3500,
            MetricType.UL_THROUGHPUT_NR3500: self.state.ul_throughput_nr3500,
            MetricType.RSRP_NR3500: self.state.rsrp_nr3500,
            MetricType.SINR_NR3500: self.state.sinr_nr3500,
            MetricType.DL_THROUGHPUT_NR700: self.state.dl_throughput_nr700,
            MetricType.UL_THROUGHPUT_NR700: self.state.ul_throughput_nr700,
            MetricType.RSRP_NR700: self.state.rsrp_nr700,
            MetricType.SINR_NR700: self.state.sinr_nr700,
            MetricType.LATENCY_PING: self.state.latency_ping,
            MetricType.TX_IMBALANCE: self.state.tx_imbalance,
        }

        # Determine which metrics to include
        if requested_types is None or MetricType.ALL in requested_types:
            types_to_send = list(metrics_map.keys())
        else:
            types_to_send = [t for t in requested_types if t in metrics_map]

        # Build payload: [type (1 byte) + value (4 bytes float32 BE)] * count
        # No count prefix - edge bridge expects raw metric entries
        payload = bytearray()
        for metric_type in types_to_send:
            payload.append(metric_type)
            payload.extend(struct.pack('>f', metrics_map[metric_type]))

        return bytes(payload)

    def get_status_payload(self) -> bytes:
        """Build status response payload."""
        # Status (1 byte) + Uptime (4 bytes) + Errors (2 bytes) + Warnings (2 bytes)
        return struct.pack('>BIHH',
            self.state.status,
            self.state.uptime_seconds,
            self.state.errors,
            self.state.warnings
        )

    def handle_message(self, msg_type: int, sequence: int, payload: bytes) -> Optional[bytes]:
        """Handle incoming message and return response frame."""
        if msg_type == MsgType.PING:
            logger.debug(f"PING received (seq={sequence})")
            return build_frame(MsgType.PONG, sequence)

        elif msg_type == MsgType.REQUEST_METRICS:
            requested = list(payload) if payload else [MetricType.ALL]
            logger.info(f"Metrics request (seq={sequence}), types={[hex(t) for t in requested]}")
            metrics_payload = self.get_metrics_payload(requested)
            return build_frame(MsgType.METRICS_RESPONSE, sequence, metrics_payload)

        elif msg_type == MsgType.GET_STATUS:
            logger.info(f"Status request (seq={sequence})")
            status_payload = self.get_status_payload()
            return build_frame(MsgType.STATUS_RESPONSE, sequence, status_payload)

        elif msg_type == MsgType.EXECUTE_COMMAND:
            logger.info(f"Command execution request (seq={sequence})")
            result_payload = struct.pack('>BB', 1, 0)
            return build_frame(MsgType.COMMAND_RESULT, sequence, result_payload)

        else:
            logger.warning(f"Unknown message type: 0x{msg_type:02X}")
            return None

    def _process_buffer(self, buffer: bytearray, client_socket: socket.socket) -> bytearray:
        """Process complete frames from buffer. Returns remaining buffer."""
        while len(buffer) >= HEADER_SIZE + CRC_SIZE:
            if buffer[0] != HEADER_BYTE0 or buffer[1] != HEADER_BYTE1:
                buffer.pop(0)
                continue

            if len(buffer) < 4:
                break

            payload_len = struct.unpack('>H', bytes(buffer[2:4]))[0]
            frame_len = HEADER_SIZE + payload_len + CRC_SIZE

            if len(buffer) < frame_len:
                break

            frame_data = bytes(buffer[:frame_len])
            buffer = buffer[frame_len:]

            result = parse_frame(frame_data)
            if result:
                msg_type, sequence, payload = result
                response = self.handle_message(msg_type, sequence, payload)
                if response:
                    client_socket.sendall(response)

        return buffer

    def handle_client(self, client_socket: socket.socket, address: tuple):
        """Handle a connected client."""
        logger.info(f"Client connected: {address}")
        buffer = bytearray()

        try:
            while self.running:
                try:
                    data = client_socket.recv(1024)
                    if not data:
                        break
                    buffer.extend(data)
                    buffer = self._process_buffer(buffer, client_socket)
                except socket.timeout:
                    continue
                except Exception as e:
                    logger.error(f"Error handling client data: {e}")
                    break
        finally:
            client_socket.close()
            logger.info(f"Client disconnected: {address}")

    def run(self):
        """Run the device server."""
        self.running = True
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind(('0.0.0.0', self.port))
        self.server_socket.listen(5)
        self.server_socket.settimeout(1.0)

        logger.info(f"MIPS Virtual Base Station '{self.station_id}' listening on port {self.port}")
        logger.info("Generating metrics: CPU, Memory, Temperature, Power, Signal")
        logger.info("                    5G NR3500 (n78): DL/UL throughput, RSRP, SINR")
        logger.info("                    5G NR700 (n28): DL/UL throughput, RSRP, SINR")
        logger.info("                    Quality: Latency, TX Imbalance")

        while self.running:
            try:
                client_socket, address = self.server_socket.accept()
                client_socket.settimeout(5.0)
                client_thread = threading.Thread(
                    target=self.handle_client,
                    args=(client_socket, address),
                    daemon=True
                )
                client_thread.start()
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    logger.error(f"Server error: {e}")
                break

        if self.server_socket:
            self.server_socket.close()
        logger.info("Device server stopped")

    def stop(self):
        """Stop the device server."""
        self.running = False


def main():
    parser = argparse.ArgumentParser(description="MIPS Virtual Base Station Device")
    parser.add_argument("--station-id", default="MIPS-BS-001", help="Station identifier")
    parser.add_argument("--port", type=int, default=9999, help="TCP port to listen on")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    device = MIPSDevice(station_id=args.station_id, port=args.port)

    try:
        device.run()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        device.stop()


if __name__ == "__main__":
    main()
