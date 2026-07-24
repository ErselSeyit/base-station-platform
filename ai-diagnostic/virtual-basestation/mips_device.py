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
import json
from dataclasses import dataclass, field
from enum import IntEnum
from typing import Optional, Callable, Dict, Any, Tuple

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
    # 5G NR radio metrics (0x40-0x4F). Band-neutral; band travels alongside.
    DL_THROUGHPUT = 0x40
    UL_THROUGHPUT = 0x41
    RSRP = 0x42
    SINR = 0x43
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
    # Power & Energy (0x80-0x8F)
    UTILITY_VOLTAGE_L1 = 0x80
    UTILITY_VOLTAGE_L2 = 0x81
    UTILITY_VOLTAGE_L3 = 0x82
    POWER_FACTOR = 0x83
    GENERATOR_FUEL_LEVEL = 0x84
    GENERATOR_RUNTIME = 0x85
    BATTERY_SOC = 0x86
    BATTERY_DOD = 0x87
    BATTERY_CELL_TEMP_MIN = 0x88
    BATTERY_CELL_TEMP_MAX = 0x89
    SOLAR_PANEL_VOLTAGE = 0x8A
    SOLAR_CHARGE_CURRENT = 0x8B
    SITE_POWER_KWH = 0x8C
    # Special
    ALL = 0xFF


class Band(IntEnum):
    """NR frequency band, carried beside a reading rather than in its type."""
    NONE = 0x00
    N28 = 0x01   # 700 MHz
    N78 = 0x02   # 3.5 GHz


class StatusCode(IntEnum):
    OK = 0x00
    WARNING = 0x01
    ERROR = 0x02
    CRITICAL = 0x03
    OFFLINE = 0x04


class CmdType(IntEnum):
    """Command sub-types (aligned with Go edge-bridge)."""
    RESTART = 0x01
    SHUTDOWN = 0x02
    RESET_CONFIG = 0x03
    UPDATE_FIRMWARE = 0x04
    RUN_DIAGNOSTIC = 0x05
    SET_PARAMETER = 0x06


class DeviceMode(IntEnum):
    """Device operational mode — drives state machine transitions."""
    NORMAL = 0
    DEGRADED = 1      # Mild fault active — still operational with reduced performance
    FAULTED = 2       # Severe fault active — needs intervention
    RESTARTING = 3    # Restart in progress — no metrics, rejects commands
    MAINTENANCE = 4   # Maintenance mode — accepts diagnostic commands only


# Restart timing
RESTART_DURATION_MIN = 30  # seconds
RESTART_DURATION_MAX = 60  # seconds

# Failure probabilities per mode
RESTART_SUCCESS_RATE = {
    DeviceMode.NORMAL: 1.0,
    DeviceMode.DEGRADED: 0.95,
    DeviceMode.FAULTED: 0.90,
    DeviceMode.RESTARTING: 0.0,   # Cannot restart while restarting
    DeviceMode.MAINTENANCE: 0.98,
}


@dataclass
class FaultScenario:
    """Defines anomalous metric ranges for a fault type."""
    name: str
    metric_overrides: Dict[str, Tuple[float, float]]  # metric_field -> (min, max)
    status: StatusCode = StatusCode.WARNING


# Fault scenarios mirroring anomaly_simulator.py ANOMALY_SCENARIOS.
# Keys match the problem codes from DiagnosticSessionService.metricTypeToProblemCode().
FAULT_SCENARIOS: Dict[str, FaultScenario] = {
    "CPU_OVERHEAT": FaultScenario(
        name="CPU Overheat",
        metric_overrides={
            "cpu_usage": (92, 99),
            "temperature": (82, 95),
        },
        status=StatusCode.CRITICAL,
    ),
    "MEMORY_PRESSURE": FaultScenario(
        name="Memory Pressure",
        metric_overrides={
            "memory_usage": (96, 99),
            "cpu_usage": (70, 85),
        },
        status=StatusCode.CRITICAL,
    ),
    "SIGNAL_DEGRADATION": FaultScenario(
        name="Signal Degradation",
        metric_overrides={
            "signal_strength": (-105, -95),
            "rsrp_nr3500": (-110, -100),
            "sinr_nr3500": (2, 8),
        },
        status=StatusCode.WARNING,
    ),
    "HIGH_LATENCY": FaultScenario(
        name="High Latency",
        metric_overrides={
            "latency_ping": (110, 200),
            "data_throughput": (30, 45),
        },
        status=StatusCode.CRITICAL,
    ),
    "HIGH_POWER_CONSUMPTION": FaultScenario(
        name="High Power Consumption",
        metric_overrides={
            "power_consumption": (3100, 3500),
            "temperature": (72, 82),
        },
        status=StatusCode.CRITICAL,
    ),
    "HIGH_INTERFERENCE": FaultScenario(
        name="High Interference",
        metric_overrides={
            "interference_level": (-68, -60),
            "sinr_nr3500": (-2, 5),
            "sinr_nr700": (0, 6),
        },
        status=StatusCode.WARNING,
    ),
    "HIGH_BLOCK_ERROR_RATE": FaultScenario(
        name="High Block Error Rate",
        metric_overrides={
            "initial_bler": (32, 50),
            "sinr_nr3500": (3, 8),
        },
        status=StatusCode.CRITICAL,
    ),
    "LOW_BATTERY": FaultScenario(
        name="Low Battery",
        metric_overrides={
            "battery_soc": (5, 9),
        },
        status=StatusCode.CRITICAL,
    ),
    "LOW_THROUGHPUT": FaultScenario(
        name="Low Throughput",
        metric_overrides={
            "data_throughput": (15, 19),
            "latency_ping": (40, 60),
        },
        status=StatusCode.CRITICAL,
    ),
    "HANDOVER_FAILURE": FaultScenario(
        name="Handover Failure",
        metric_overrides={
            "handover_success_rate": (85, 89),
            "signal_strength": (-90, -80),
        },
        status=StatusCode.CRITICAL,
    ),
}


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
    handover_success_rate: float = 98.5   # %
    interference_level: float = -85.0     # dBm

    # 5G Radio metrics
    initial_bler: float = 2.0             # %
    data_throughput: float = 180.0        # Mbps

    # Battery / Power
    battery_soc: float = 85.0             # %
    battery_dod: float = 15.0             # %


# Thresholds for alerts
THRESHOLDS = {
    MetricType.CPU_USAGE: {"warning": 70, "critical": 85},
    MetricType.MEMORY_USAGE: {"warning": 75, "critical": 90},
    MetricType.TEMPERATURE: {"warning": 60, "critical": 75},
    (MetricType.RSRP, Band.N78): {"warning": -90, "critical": -100},  # Lower is worse
    (MetricType.RSRP, Band.N28): {"warning": -95, "critical": -105},
    (MetricType.SINR, Band.N78): {"warning": 8, "critical": 3},  # Lower is worse
    (MetricType.SINR, Band.N28): {"warning": 5, "critical": 0},
    MetricType.LATENCY_PING: {"warning": 20, "critical": 50},  # Higher is worse
}


class MIPSDevice:
    """Virtual MIPS base station device."""

    def __init__(self, station_id: str, port: int = 9999,
                 tls_cert: Optional[str] = None, tls_key: Optional[str] = None,
                 tls_ca: Optional[str] = None):
        self.station_id = station_id
        self.port = port
        self.tls_cert = tls_cert
        self.tls_key = tls_key
        self.tls_ca = tls_ca
        self.state = DeviceState(station_id=station_id)
        self.running = False
        self.server_socket: Optional[socket.socket] = None
        self.start_time = time.time()
        self.connected_clients: list = []
        self.alert_sequence = 0
        self.last_alert_time = 0

        # Fault injection state
        self.active_faults: Dict[str, FaultScenario] = {}
        self._fault_lock = threading.Lock()

        # State machine
        self.mode = DeviceMode.NORMAL
        self._restart_until: Optional[float] = None  # timestamp when restart completes
        self._mode_lock = threading.Lock()

    def _update_mode(self):
        """Recalculate device mode based on active faults and restart state."""
        with self._mode_lock:
            # RESTARTING takes priority — don't change until restart completes
            if self.mode == DeviceMode.RESTARTING:
                if self._restart_until and time.time() >= self._restart_until:
                    self._restart_until = None
                    self.mode = DeviceMode.NORMAL
                    logger.info("Restart complete — mode → NORMAL")
                return

            # MAINTENANCE is sticky — only cleared explicitly
            if self.mode == DeviceMode.MAINTENANCE:
                return

            with self._fault_lock:
                if not self.active_faults:
                    self.mode = DeviceMode.NORMAL
                    return
                worst = max(s.status for s in self.active_faults.values())

            if worst >= StatusCode.CRITICAL:
                self.mode = DeviceMode.FAULTED
            elif worst >= StatusCode.WARNING:
                self.mode = DeviceMode.DEGRADED
            else:
                self.mode = DeviceMode.NORMAL

    def inject_fault(self, fault_type: str) -> Dict[str, Any]:
        """Inject a named fault. Returns status dict."""
        fault_type = fault_type.upper()
        if fault_type not in FAULT_SCENARIOS:
            return {"status": "error", "message": f"Unknown fault: {fault_type}",
                    "available": list(FAULT_SCENARIOS.keys())}
        with self._fault_lock:
            self.active_faults[fault_type] = FAULT_SCENARIOS[fault_type]
        self._update_mode()
        logger.info("Fault injected: %s (%s) — mode → %s",
                     fault_type, FAULT_SCENARIOS[fault_type].name, self.mode.name)
        return {"status": "injected", "fault": fault_type, "mode": self.mode.name}

    def clear_fault(self, fault_type: str) -> Dict[str, Any]:
        """Clear a specific fault. Returns status dict."""
        fault_type = fault_type.upper()
        with self._fault_lock:
            removed = self.active_faults.pop(fault_type, None)
        if removed:
            self._update_mode()
            logger.info("Fault cleared: %s — mode → %s", fault_type, self.mode.name)
            return {"status": "cleared", "fault": fault_type, "mode": self.mode.name}
        return {"status": "not_active", "fault": fault_type}

    def clear_all_faults(self) -> Dict[str, Any]:
        """Clear all active faults."""
        with self._fault_lock:
            count = len(self.active_faults)
            self.active_faults.clear()
        self._update_mode()
        logger.info("All faults cleared (count=%d) — mode → %s", count, self.mode.name)
        return {"status": "cleared_all", "count": count, "mode": self.mode.name}

    def get_active_faults(self) -> Dict[str, Any]:
        """Return list of active faults."""
        with self._fault_lock:
            faults = {k: v.name for k, v in self.active_faults.items()}
        return {"active_faults": faults, "count": len(faults), "mode": self.mode.name}

    def _get_fault_overrides(self) -> Dict[str, Tuple[float, float]]:
        """Collect all metric overrides from active faults."""
        overrides: Dict[str, Tuple[float, float]] = {}
        with self._fault_lock:
            for scenario in self.active_faults.values():
                overrides.update(scenario.metric_overrides)
        return overrides

    def _get_worst_status(self) -> StatusCode:
        """Return the worst status code across all active faults."""
        with self._fault_lock:
            if not self.active_faults:
                return StatusCode.OK
            return max(s.status for s in self.active_faults.values())

    def _apply_cascading_effects(self):
        """Apply realistic cascading effects based on current metric values.

        Physics-inspired relationships:
        - High temperature → increased fan speed, CPU throttling → reduced throughput
        - Signal degradation → BLER increases, handover rate drops, throughput drops
        - Memory pressure → latency increases
        - High power consumption → temperature rises further
        """
        s = self.state

        # Temperature cascades: high temp → throughput reduction from CPU throttling
        if s.temperature > 75:
            throttle_factor = 1.0 - (s.temperature - 75) / 50  # 75C=1.0, 95C=0.6
            throttle_factor = max(0.4, throttle_factor)
            s.dl_throughput_nr3500 *= throttle_factor
            s.ul_throughput_nr3500 *= throttle_factor
            s.data_throughput *= throttle_factor

        # Signal degradation cascades: weak RSRP → BLER up, handover rate down
        if s.rsrp_nr3500 < -95:
            signal_penalty = (-95 - s.rsrp_nr3500) / 15  # 0 at -95, 1.0 at -110
            signal_penalty = min(1.0, signal_penalty)
            s.initial_bler += signal_penalty * 25  # up to +25% BLER
            s.handover_success_rate -= signal_penalty * 10  # up to -10% handover
            s.dl_throughput_nr3500 *= (1.0 - signal_penalty * 0.5)
            s.data_throughput *= (1.0 - signal_penalty * 0.4)

        # Memory pressure cascades: high memory → latency spike
        if s.memory_usage > 90:
            mem_pressure = (s.memory_usage - 90) / 10  # 0 at 90%, 1.0 at 100%
            s.latency_ping += mem_pressure * 80  # up to +80ms latency

        # High power → temperature feedback loop
        if s.power_consumption > 2500:
            power_heat = (s.power_consumption - 2500) / 1000  # 0 at 2500W, 1.0 at 3500W
            s.temperature += power_heat * 8  # up to +8C from power

        # Clamp values to physical bounds
        s.dl_throughput_nr3500 = max(0, s.dl_throughput_nr3500)
        s.ul_throughput_nr3500 = max(0, s.ul_throughput_nr3500)
        s.data_throughput = max(0, s.data_throughput)
        s.initial_bler = min(100, max(0, s.initial_bler))
        s.handover_success_rate = max(50, min(100, s.handover_success_rate))
        s.latency_ping = max(1, s.latency_ping)
        s.temperature = min(120, s.temperature)

    def simulate_metrics(self):
        """Update metrics with realistic variations."""
        # Check if restart has completed
        self._update_mode()

        # During RESTARTING, don't generate metrics — device is offline
        if self.mode == DeviceMode.RESTARTING:
            self.state.uptime_seconds = 0
            self.state.status = StatusCode.OFFLINE
            return

        hour = time.localtime().tm_hour

        # Time-based load factor (higher during business hours)
        if 8 <= hour <= 18:
            load_factor = 1.2 + 0.1 * math.sin((hour - 8) * math.pi / 10)
        elif 0 <= hour <= 6:
            load_factor = 0.7
        else:
            load_factor = 0.9

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
        self.state.handover_success_rate = max(92, min(99.9,
            98.5 + random.uniform(-1.5, 1.0)))
        self.state.interference_level = max(-100, min(-70,
            -85 + random.uniform(-5, 5)))

        # 5G Radio metrics
        self.state.initial_bler = max(0.5, min(8,
            2.0 + random.uniform(-0.5, 1.0)))
        self.state.data_throughput = max(100, min(300,
            180 * load_factor + random.uniform(-20, 25)))

        # Battery (slow drift)
        self.state.battery_soc = max(20, min(100,
            85 + random.uniform(-2, 2)))
        self.state.battery_dod = 100 - self.state.battery_soc

        # Update uptime
        self.state.uptime_seconds = int(time.time() - self.start_time)

        # Apply fault overrides (replace normal values with anomalous ranges)
        overrides = self._get_fault_overrides()
        for field_name, (lo, hi) in overrides.items():
            if hasattr(self.state, field_name):
                setattr(self.state, field_name, random.uniform(lo, hi))

        # Apply cascading effects (secondary metric degradation from faults)
        self._apply_cascading_effects()

        # Set status based on active faults
        fault_status = self._get_worst_status()
        if fault_status != StatusCode.OK:
            self.state.status = fault_status
        else:
            self.state.status = StatusCode.OK

    def get_metrics_payload(self, requested_types: Optional[list] = None) -> bytes:
        """Build metrics response payload. Returns empty during RESTARTING."""
        self.simulate_metrics()

        # During restart, return empty payload — edge-bridge handles this gracefully
        if self.mode == DeviceMode.RESTARTING:
            return b''

        # Readings as (type, band, value). The NR metrics carry their band; the
        # rest are band-neutral. Two carriers can share a type without colliding.
        readings = [
            (MetricType.CPU_USAGE, Band.NONE, self.state.cpu_usage),
            (MetricType.MEMORY_USAGE, Band.NONE, self.state.memory_usage),
            (MetricType.TEMPERATURE, Band.NONE, self.state.temperature),
            (MetricType.POWER_CONSUMPTION, Band.NONE, self.state.power_consumption),
            (MetricType.SIGNAL_STRENGTH, Band.NONE, self.state.signal_strength),
            (MetricType.DL_THROUGHPUT, Band.N78, self.state.dl_throughput_nr3500),
            (MetricType.UL_THROUGHPUT, Band.N78, self.state.ul_throughput_nr3500),
            (MetricType.RSRP, Band.N78, self.state.rsrp_nr3500),
            (MetricType.SINR, Band.N78, self.state.sinr_nr3500),
            (MetricType.DL_THROUGHPUT, Band.N28, self.state.dl_throughput_nr700),
            (MetricType.UL_THROUGHPUT, Band.N28, self.state.ul_throughput_nr700),
            (MetricType.RSRP, Band.N28, self.state.rsrp_nr700),
            (MetricType.SINR, Band.N28, self.state.sinr_nr700),
            (MetricType.LATENCY_PING, Band.NONE, self.state.latency_ping),
            (MetricType.TX_IMBALANCE, Band.NONE, self.state.tx_imbalance),
            (MetricType.HANDOVER_SUCCESS_RATE, Band.NONE, self.state.handover_success_rate),
            (MetricType.INTERFERENCE_LEVEL, Band.NONE, self.state.interference_level),
            (MetricType.INITIAL_BLER, Band.NONE, self.state.initial_bler),
            (MetricType.DATA_THROUGHPUT, Band.NONE, self.state.data_throughput),
            (MetricType.BATTERY_SOC, Band.NONE, self.state.battery_soc),
            (MetricType.BATTERY_DOD, Band.NONE, self.state.battery_dod),
        ]

        # Determine which metrics to include (filter by type; band-neutral).
        if requested_types is None or MetricType.ALL in requested_types:
            selected = readings
        else:
            selected = [r for r in readings if r[0] in requested_types]

        # Build payload: [type(1)][band(1)][value(4 float32 BE)] * count.
        # No count prefix - edge bridge expects raw metric entries.
        payload = bytearray()
        for metric_type, band, value in selected:
            payload.append(metric_type)
            payload.append(band)
            payload.extend(struct.pack('>f', value))

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
            return self._handle_execute_command(sequence, payload)

        else:
            logger.warning(f"Unknown message type: 0x{msg_type:02X}")
            return None

    def _handle_execute_command(self, sequence: int, payload: bytes) -> bytes:
        """Handle EXECUTE_COMMAND with real sub-type dispatch.

        Payload format from edge-bridge:
          Byte 0: Command sub-type (CmdType enum)
          Byte 1: Parameter length (N)
          Bytes 2..2+N: Parameter data (JSON-encoded string)
        """
        if len(payload) < 2:
            logger.warning("EXECUTE_COMMAND payload too short (%d bytes)", len(payload))
            return self._command_result(sequence, success=False, code=0x01)

        cmd_sub = payload[0]
        param_len = payload[1]
        params_raw = payload[2:2 + param_len] if param_len > 0 else b''

        # Parse JSON params if present
        params: Dict[str, Any] = {}
        if params_raw:
            try:
                params = json.loads(params_raw.decode('utf-8'))
            except (json.JSONDecodeError, UnicodeDecodeError) as e:
                logger.warning("Failed to parse command params: %s", e)

        logger.info("EXECUTE_COMMAND sub=0x%02X params=%s (seq=%d, mode=%s)",
                     cmd_sub, params, sequence, self.mode.name)

        # Reject commands while device is restarting (except PING handled above)
        if self.mode == DeviceMode.RESTARTING:
            logger.warning("Command rejected — device is RESTARTING")
            return self._command_result(sequence, success=False, code=0x04)

        # In MAINTENANCE mode, only allow diagnostic and reset commands
        if self.mode == DeviceMode.MAINTENANCE and cmd_sub not in (
            CmdType.RUN_DIAGNOSTIC, CmdType.RESET_CONFIG
        ):
            logger.warning("Command 0x%02X rejected — device in MAINTENANCE mode", cmd_sub)
            return self._command_result(sequence, success=False, code=0x05)

        try:
            if cmd_sub == CmdType.RESTART:
                return self._cmd_restart(sequence, params)
            elif cmd_sub == CmdType.SET_PARAMETER:
                return self._cmd_set_parameter(sequence, params)
            elif cmd_sub == CmdType.RUN_DIAGNOSTIC:
                return self._cmd_run_diagnostic(sequence)
            elif cmd_sub == CmdType.SHUTDOWN:
                return self._cmd_shutdown(sequence)
            elif cmd_sub == CmdType.RESET_CONFIG:
                return self._cmd_reset_config(sequence)
            else:
                logger.warning("Unknown command sub-type: 0x%02X", cmd_sub)
                return self._command_result(sequence, success=False, code=0x02)
        except Exception as e:
            logger.error("Command execution failed: %s", e)
            return self._command_result(sequence, success=False, code=0xFF)

    def _command_result(self, sequence: int, success: bool, code: int = 0) -> bytes:
        """Build a COMMAND_RESULT frame.

        Byte 0: 0x00=success, 0x01=failure (matches Go edge-bridge convention).
        Byte 1: return code.
        """
        result_payload = struct.pack('>BB', 0x00 if success else 0x01, code)
        return build_frame(MsgType.COMMAND_RESULT, sequence, result_payload)

    def _cmd_restart(self, sequence: int, params: Dict[str, Any]) -> bytes:
        """Handle RESTART command with realistic failure probability and downtime.

        Success rate depends on current device mode (90% when FAULTED).
        On success, enters RESTARTING mode for 30-60s — no metrics, rejects commands.
        On completion, faults are cleared and state is reset.
        """
        component = params.get("component", "full")
        success_rate = RESTART_SUCCESS_RATE.get(self.mode, 0.95)

        if random.random() > success_rate:
            logger.warning("RESTART failed (mode=%s, success_rate=%.0f%%)",
                           self.mode.name, success_rate * 100)
            self.state.errors += 1
            return self._command_result(sequence, success=False, code=0x10)

        # Calculate restart duration
        duration = random.uniform(RESTART_DURATION_MIN, RESTART_DURATION_MAX)
        logger.info("RESTART command accepted: component=%s, downtime=%.0fs (mode=%s)",
                     component, duration, self.mode.name)

        # Clear faults and enter RESTARTING mode
        with self._fault_lock:
            self.active_faults.clear()
        with self._mode_lock:
            self.mode = DeviceMode.RESTARTING
            self._restart_until = time.time() + duration

        self.state.errors = 0
        self.state.warnings = 0
        self.start_time = time.time() + duration  # uptime resets after restart
        return self._command_result(sequence, success=True)

    def _cmd_set_parameter(self, sequence: int, params: Dict[str, Any]) -> bytes:
        """Handle SET_PARAMETER command — the primary healing action path.

        Expected params:
          {"action": "clear_fault", "fault_type": "CPU_OVERHEAT"}
          {"action": "inject_fault", "fault_type": "CPU_OVERHEAT"}
          {"action": "clear_all_faults"}
        """
        action = params.get("action", "")

        if action == "clear_fault":
            fault_type = params.get("fault_type", "")
            result = self.clear_fault(fault_type)
            success = result.get("status") == "cleared"
            return self._command_result(sequence, success=success)

        elif action == "inject_fault":
            fault_type = params.get("fault_type", "")
            result = self.inject_fault(fault_type)
            success = result.get("status") == "injected"
            return self._command_result(sequence, success=success)

        elif action == "clear_all_faults":
            self.clear_all_faults()
            return self._command_result(sequence, success=True)

        elif action == "enter_maintenance":
            with self._mode_lock:
                self.mode = DeviceMode.MAINTENANCE
            logger.info("Device entered MAINTENANCE mode (exit via RESET_CONFIG)")
            return self._command_result(sequence, success=True)

        else:
            logger.warning("Unknown SET_PARAMETER action: %s", action)
            return self._command_result(sequence, success=False, code=0x03)

    def _cmd_run_diagnostic(self, sequence: int) -> bytes:
        """Handle RUN_DIAGNOSTIC — returns device diagnostic data as JSON payload."""
        diag = {
            "station_id": self.station_id,
            "mode": self.mode.name,
            "uptime_seconds": self.state.uptime_seconds,
            "status": self.state.status.name,
            "active_faults": {k: v.name for k, v in self.active_faults.items()},
            "cpu_usage": round(self.state.cpu_usage, 1),
            "memory_usage": round(self.state.memory_usage, 1),
            "temperature": round(self.state.temperature, 1),
        }
        diag_json = json.dumps(diag).encode('utf-8')
        # COMMAND_RESULT with success=0x00, code=0, followed by JSON
        result_payload = struct.pack('>BB', 0x00, 0) + diag_json
        return build_frame(MsgType.COMMAND_RESULT, sequence, result_payload)

    def _cmd_shutdown(self, sequence: int) -> bytes:
        """Handle SHUTDOWN — acknowledge and schedule stop."""
        logger.info("SHUTDOWN command received — acknowledging")
        # Send success first, then schedule stop
        threading.Timer(1.0, self.stop).start()
        return self._command_result(sequence, success=True)

    def _cmd_reset_config(self, sequence: int) -> bytes:
        """Handle RESET_CONFIG — clear all faults, exit maintenance, reset to defaults.

        This is the authoritative "factory reset" — always transitions to NORMAL.
        Allowed even in MAINTENANCE mode.
        """
        logger.info("RESET_CONFIG command — restoring defaults")
        with self._fault_lock:
            self.active_faults.clear()
        with self._mode_lock:
            self.mode = DeviceMode.NORMAL
            self._restart_until = None
        self.state = DeviceState(station_id=self.station_id)
        self.start_time = time.time()
        logger.info("Device reset — mode → NORMAL")
        return self._command_result(sequence, success=True)

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

    def _create_ssl_context(self):
        """Create SSL context for TLS server mode."""
        import ssl
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.load_cert_chain(certfile=self.tls_cert, keyfile=self.tls_key)
        if self.tls_ca:
            ctx.load_verify_locations(cafile=self.tls_ca)
            ctx.verify_mode = ssl.CERT_REQUIRED  # mutual TLS
        logger.info("TLS enabled: cert=%s, key=%s, ca=%s, mTLS=%s",
                    self.tls_cert, self.tls_key, self.tls_ca or "none",
                    "yes" if self.tls_ca else "no")
        return ctx

    def run(self):
        """Run the device server."""
        self.running = True
        raw_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        raw_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        raw_socket.bind(('0.0.0.0', self.port))
        raw_socket.listen(5)
        raw_socket.settimeout(1.0)

        if self.tls_cert and self.tls_key:
            ssl_ctx = self._create_ssl_context()
            self.server_socket = ssl_ctx.wrap_socket(raw_socket, server_side=True)
        else:
            self.server_socket = raw_socket

        transport = "TLS" if self.tls_cert else "TCP"
        logger.info(f"MIPS Virtual Base Station '{self.station_id}' listening on {transport} port {self.port}")
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


def create_control_api(device: MIPSDevice):
    """Create Flask control API for external fault injection (port 8098).

    Endpoints:
      POST /api/inject    — inject a named fault
      POST /api/clear     — clear a specific fault
      POST /api/clear-all — clear all faults
      GET  /api/faults    — list active faults
      GET  /health        — health check
    """
    try:
        from flask import Flask, request as flask_request, jsonify
    except ImportError:
        logger.warning("Flask not installed — control API disabled. Install with: pip install flask")
        return None

    app = Flask(__name__)
    # Suppress Flask request logs (use our own logger)
    flog = logging.getLogger('werkzeug')
    flog.setLevel(logging.WARNING)

    @app.route('/api/inject', methods=['POST'])
    def inject_fault():
        data = flask_request.get_json(silent=True) or {}
        fault_type = data.get('fault') or data.get('fault_type', '')
        if not fault_type:
            return jsonify({"status": "error", "message": "Missing 'fault' field",
                            "available": list(FAULT_SCENARIOS.keys())}), 400
        result = device.inject_fault(fault_type)
        status_code = 200 if result.get("status") == "injected" else 400
        return jsonify(result), status_code

    @app.route('/api/clear', methods=['POST'])
    def clear_fault():
        data = flask_request.get_json(silent=True) or {}
        fault_type = data.get('fault') or data.get('fault_type', '')
        if not fault_type:
            return jsonify({"status": "error", "message": "Missing 'fault' field"}), 400
        result = device.clear_fault(fault_type)
        return jsonify(result)

    @app.route('/api/clear-all', methods=['POST'])
    def clear_all():
        result = device.clear_all_faults()
        return jsonify(result)

    @app.route('/api/faults', methods=['GET'])
    def list_faults():
        result = device.get_active_faults()
        return jsonify(result)

    @app.route('/health', methods=['GET'])
    def health():
        return jsonify({
            "status": "healthy",
            "station_id": device.station_id,
            "running": device.running,
            "mode": device.mode.name,
            "active_faults": len(device.active_faults),
        })

    return app


def main():
    parser = argparse.ArgumentParser(description="MIPS Virtual Base Station Device")
    parser.add_argument("--station-id", default="MIPS-BS-001", help="Station identifier")
    parser.add_argument("--port", type=int, default=9999, help="TCP port to listen on")
    parser.add_argument("--control-port", type=int, default=8098, help="Flask control API port")
    parser.add_argument("--no-control-api", action="store_true", help="Disable Flask control API")
    parser.add_argument("--tls-cert", default=None, help="TLS certificate file (PEM)")
    parser.add_argument("--tls-key", default=None, help="TLS private key file (PEM)")
    parser.add_argument("--tls-ca", default=None, help="CA cert for mutual TLS (PEM)")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")
    args = parser.parse_args()

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    device = MIPSDevice(
        station_id=args.station_id,
        port=args.port,
        tls_cert=args.tls_cert,
        tls_key=args.tls_key,
        tls_ca=args.tls_ca,
    )

    # Start Flask control API in a daemon thread
    if not args.no_control_api:
        app = create_control_api(device)
        if app:
            api_thread = threading.Thread(
                target=lambda: app.run(
                    host='0.0.0.0', port=args.control_port, debug=False, use_reloader=False
                ),
                daemon=True,
                name="control-api",
            )
            api_thread.start()
            logger.info("Control API started on port %d", args.control_port)

    try:
        device.run()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        device.stop()


if __name__ == "__main__":
    main()
