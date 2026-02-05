#!/usr/bin/env python3
"""
Virtual MIPS Base Station Simulator

Simulates a real base station with MIPS CPU that:
- Generates realistic hardware/software problems
- Sends problems to AI diagnostic service
- Receives and applies solutions
- Reports back status
- Implements the device protocol for edge-bridge connectivity

This allows testing the AI diagnostic pipeline without real hardware.
The edge-bridge can connect to get real-time metrics that flow to
the Java monitoring service.
"""

import socket
import json
import time
import random
import threading
import logging
import argparse
from dataclasses import dataclass, asdict, field
from enum import Enum
from typing import Optional, Dict, Any, List
from datetime import datetime, timezone

from device_protocol import (
    DeviceProtocolServer, MetricType, StatusCode, CommandType,
    Metric, StatusPayload, CommandResult
)

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class ProblemSeverity(Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class ProblemCategory(Enum):
    HARDWARE = "hardware"
    NETWORK = "network"
    SOFTWARE = "software"
    POWER = "power"
    SECURITY = "security"


@dataclass
class BaseStationState:
    """Current state of the virtual base station with comprehensive 5G metrics"""
    station_id: str = "MIPS-BS-001"

    # System metrics
    cpu_temp: float = 45.0  # Celsius
    cpu_usage: float = 25.0  # Percentage
    memory_usage: float = 40.0  # Percentage
    power_consumption: float = 1500.0  # Watts
    fan_speed: float = 3000.0  # RPM

    # RF metrics
    signal_strength: float = -85.0  # dBm
    signal_quality: float = 25.0  # dB SNR
    vswr: float = 1.2  # Ratio

    # Performance metrics
    active_connections: int = 50
    data_throughput: float = 500.0  # Mbps
    latency: float = 15.0  # ms
    packet_loss: float = 0.1  # Percentage

    # Device metrics
    uptime_seconds: int = 0
    error_count: int = 0
    warning_count: int = 0
    firmware_version: str = "v2.4.1-mips"

    # 5G NR700 (n28 band - 700MHz)
    dl_throughput_nr700: float = 55.0  # Mbps
    ul_throughput_nr700: float = 22.0  # Mbps
    rsrp_nr700: float = -95.0  # dBm
    sinr_nr700: float = 12.0  # dB

    # 5G NR3500 (n78 band - 3.5GHz)
    dl_throughput_nr3500: float = 850.0  # Mbps
    ul_throughput_nr3500: float = 85.0  # Mbps
    rsrp_nr3500: float = -88.0  # dBm
    sinr_nr3500: float = 18.0  # dB

    # 5G Radio metrics
    pdcp_throughput: float = 900.0  # Mbps
    rlc_throughput: float = 920.0  # Mbps
    initial_bler: float = 2.5  # Percentage
    avg_mcs: float = 24.0  # Modulation index
    rb_per_slot: float = 270.0  # Resource blocks
    rank_indicator: float = 4.0  # MIMO layers

    # RF Quality
    tx_imbalance: float = 1.5  # dB
    latency_ping: float = 12.0  # ms
    handover_success_rate: float = 99.5  # Percentage
    interference_level: float = -105.0  # dBm

    # Carrier Aggregation
    ca_dl_throughput: float = 1200.0  # Mbps combined
    ca_ul_throughput: float = 150.0  # Mbps combined

    # Power & Energy
    utility_voltage: float = 230.0  # V
    battery_soc: float = 85.0  # Percentage
    site_power_kwh: float = 0.0  # Cumulative kWh

    # Status
    last_error: Optional[str] = None


@dataclass
class Problem:
    """A problem detected by the base station"""
    id: str
    timestamp: str
    station_id: str
    category: str
    severity: str
    code: str
    message: str
    metrics: Dict[str, Any]
    raw_logs: str


@dataclass
class Solution:
    """A solution received from AI diagnostic service"""
    problem_id: str
    action: str
    commands: list
    expected_outcome: str
    risk_level: str


class VirtualMIPSBaseStation:
    """
    Simulates a MIPS-based base station with realistic behavior.

    Architecture:
    - MIPS CPU running embedded Linux
    - Radio transceiver for cellular signals (5G NR)
    - Power management unit
    - Network interface (Ethernet/Fiber)

    Exposes metrics via device protocol for edge-bridge connectivity.
    """

    # Realistic problem scenarios based on actual base station issues
    PROBLEM_SCENARIOS = [
        {
            "category": ProblemCategory.HARDWARE,
            "code": "CPU_OVERHEAT",
            "message": "CPU temperature exceeded threshold",
            "trigger": lambda s: s.cpu_temp > 75,
            "severity": ProblemSeverity.HIGH,
            "logs": "kernel: CPU thermal throttling activated\nkernel: temp zone0: 78.5C > 75C threshold"
        },
        {
            "category": ProblemCategory.HARDWARE,
            "code": "MEMORY_PRESSURE",
            "message": "System memory critically low",
            "trigger": lambda s: s.memory_usage > 90,
            "severity": ProblemSeverity.CRITICAL,
            "logs": "kernel: Out of memory: Kill process 1234 (radio_daemon)\nkernel: oom_kill_process+0x123/0x456"
        },
        {
            "category": ProblemCategory.NETWORK,
            "code": "SIGNAL_DEGRADATION",
            "message": "RF signal strength below acceptable level",
            "trigger": lambda s: s.signal_strength < -110,
            "severity": ProblemSeverity.MEDIUM,
            "logs": "radio: RSSI dropped to -115dBm\nradio: Interference detected on channel 38"
        },
        {
            "category": ProblemCategory.NETWORK,
            "code": "BACKHAUL_LATENCY",
            "message": "Backhaul network latency exceeds SLA",
            "trigger": lambda s: random.random() < 0.1,
            "severity": ProblemSeverity.MEDIUM,
            "logs": "net: RTT to core: 150ms (threshold: 50ms)\nnet: Packet loss: 2.5%"
        },
        {
            "category": ProblemCategory.SOFTWARE,
            "code": "PROCESS_CRASH",
            "message": "Critical process terminated unexpectedly",
            "trigger": lambda s: random.random() < 0.05,
            "severity": ProblemSeverity.HIGH,
            "logs": "systemd: radio_controller.service: Main process exited, code=killed, status=11/SEGV\nkernel: radio_controller[5678]: segfault at 0x0 ip 0x7f1234 sp 0x7fff56"
        },
        {
            "category": ProblemCategory.SOFTWARE,
            "code": "CONFIG_MISMATCH",
            "message": "Configuration file corrupted or invalid",
            "trigger": lambda s: random.random() < 0.03,
            "severity": ProblemSeverity.MEDIUM,
            "logs": "config: Failed to parse /etc/basestation/radio.conf at line 45\nconfig: Invalid frequency range: 0-0 MHz"
        },
        {
            "category": ProblemCategory.POWER,
            "code": "POWER_FLUCTUATION",
            "message": "Input power voltage unstable",
            "trigger": lambda s: random.random() < 0.08,
            "severity": ProblemSeverity.HIGH,
            "logs": "power: Input voltage: 45.2V (nominal: 48V)\npower: UPS battery engaged, estimated backup: 4 hours"
        },
        {
            "category": ProblemCategory.POWER,
            "code": "HIGH_POWER_CONSUMPTION",
            "message": "Power consumption exceeds rated capacity",
            "trigger": lambda s: s.power_consumption > 2000,
            "severity": ProblemSeverity.MEDIUM,
            "logs": "power: Current draw: 2150W (rated: 2000W)\npower: PA efficiency: 38% (expected: 45%)"
        },
        {
            "category": ProblemCategory.SECURITY,
            "code": "AUTH_FAILURE",
            "message": "Multiple authentication failures detected",
            "trigger": lambda s: random.random() < 0.02,
            "severity": ProblemSeverity.CRITICAL,
            "logs": "security: 15 failed SSH attempts from 192.168.1.100\nsecurity: Account locked: admin"
        },
        {
            "category": ProblemCategory.SECURITY,
            "code": "CERT_EXPIRING",
            "message": "TLS certificate expires within 7 days",
            "trigger": lambda s: random.random() < 0.01,
            "severity": ProblemSeverity.LOW,
            "logs": "tls: Certificate expires: 2026-01-20\ntls: Subject: CN=basestation-042.local"
        },
        {
            "category": ProblemCategory.NETWORK,
            "code": "5G_BLER_HIGH",
            "message": "5G Initial BLER exceeds threshold",
            "trigger": lambda s: s.initial_bler > 10,
            "severity": ProblemSeverity.MEDIUM,
            "logs": "5g: Initial BLER: 12.5% (threshold: 10%)\n5g: MCS downgrade recommended"
        },
        {
            "category": ProblemCategory.NETWORK,
            "code": "HANDOVER_FAILURES",
            "message": "Handover success rate below threshold",
            "trigger": lambda s: s.handover_success_rate < 95,
            "severity": ProblemSeverity.HIGH,
            "logs": "mobility: Handover success rate: 92.3%\nmobility: Failed handovers in last hour: 15"
        },
    ]

    def __init__(
        self,
        station_id: str = "MIPS-BS-001",
        diagnostic_host: str = "localhost",
        diagnostic_port: int = 9090,
        protocol_port: int = 8888
    ):
        self.station_id = station_id
        self.diagnostic_host = diagnostic_host
        self.diagnostic_port = diagnostic_port
        self.protocol_port = protocol_port
        self.running = False
        self.state = self._initial_state()
        self.problem_history = []
        self.solution_history = []

        # Device protocol server for edge-bridge connectivity
        self.protocol_server = DeviceProtocolServer(port=protocol_port)
        self.protocol_server.set_metrics_callback(self._get_metrics)
        self.protocol_server.set_status_callback(self._get_status)
        self.protocol_server.set_command_callback(self._execute_command)

    def _initial_state(self) -> BaseStationState:
        """Initialize base station with normal operating parameters"""
        return BaseStationState(station_id=self.station_id)

    def _simulate_environment(self):
        """Simulate environmental changes that affect base station"""
        # CPU temperature fluctuates based on load
        self.state.cpu_temp += random.uniform(-2, 3)
        self.state.cpu_temp = max(35, min(95, self.state.cpu_temp))

        # CPU usage varies
        self.state.cpu_usage += random.uniform(-10, 15)
        self.state.cpu_usage = max(5, min(100, self.state.cpu_usage))

        # Memory slowly increases (simulating leak) then resets
        self.state.memory_usage += random.uniform(0, 2)
        if self.state.memory_usage > 95:
            self.state.memory_usage = 40  # Simulated restart

        # Power consumption varies with load (baseline 1500W, ±300W based on CPU)
        base_power = 1500.0
        load_factor = (self.state.cpu_usage - 50) / 50  # -1 to +1 normalized
        self.state.power_consumption = base_power + (load_factor * 300) + random.uniform(-50, 50)

        # Fan speed correlates with temperature
        self.state.fan_speed = 2000 + (self.state.cpu_temp - 40) * 50 + random.uniform(-100, 100)
        self.state.fan_speed = max(1000, min(6000, self.state.fan_speed))

        # Signal strength fluctuates
        self.state.signal_strength += random.uniform(-3, 3)
        self.state.signal_strength = max(-120, min(-60, self.state.signal_strength))

        # Signal quality varies with signal strength
        self.state.signal_quality = 40 + (self.state.signal_strength + 90) * 0.5 + random.uniform(-3, 3)
        self.state.signal_quality = max(0, min(50, self.state.signal_quality))

        # VSWR varies slightly
        self.state.vswr += random.uniform(-0.05, 0.05)
        self.state.vswr = max(1.0, min(3.0, self.state.vswr))

        # Active connections change
        self.state.active_connections += random.randint(-5, 8)
        self.state.active_connections = max(0, min(200, self.state.active_connections))

        # Data throughput varies with connections
        base_throughput = 300 + self.state.active_connections * 3
        self.state.data_throughput = base_throughput + random.uniform(-50, 50)
        self.state.data_throughput = max(0, min(1000, self.state.data_throughput))

        # Latency varies
        self.state.latency += random.uniform(-2, 2)
        self.state.latency = max(5, min(100, self.state.latency))

        # Packet loss varies
        self.state.packet_loss += random.uniform(-0.1, 0.1)
        self.state.packet_loss = max(0, min(5, self.state.packet_loss))

        # 5G NR700 metrics
        self.state.rsrp_nr700 += random.uniform(-2, 2)
        self.state.rsrp_nr700 = max(-120, min(-70, self.state.rsrp_nr700))
        self.state.sinr_nr700 += random.uniform(-1, 1)
        self.state.sinr_nr700 = max(-5, min(25, self.state.sinr_nr700))
        self.state.dl_throughput_nr700 = 50 + (self.state.sinr_nr700 + 5) * 2 + random.uniform(-5, 5)
        self.state.ul_throughput_nr700 = 20 + (self.state.sinr_nr700 + 5) * 0.8 + random.uniform(-3, 3)

        # 5G NR3500 metrics (higher throughput, more variable)
        self.state.rsrp_nr3500 += random.uniform(-3, 3)
        self.state.rsrp_nr3500 = max(-110, min(-65, self.state.rsrp_nr3500))
        self.state.sinr_nr3500 += random.uniform(-2, 2)
        self.state.sinr_nr3500 = max(-5, min(30, self.state.sinr_nr3500))
        self.state.dl_throughput_nr3500 = 700 + (self.state.sinr_nr3500 + 5) * 15 + random.uniform(-50, 50)
        self.state.ul_throughput_nr3500 = 70 + (self.state.sinr_nr3500 + 5) * 3 + random.uniform(-10, 10)

        # 5G Radio metrics
        self.state.pdcp_throughput = self.state.dl_throughput_nr3500 * 0.95 + random.uniform(-20, 20)
        self.state.rlc_throughput = self.state.pdcp_throughput * 1.02 + random.uniform(-10, 10)
        self.state.initial_bler += random.uniform(-0.5, 0.5)
        self.state.initial_bler = max(0, min(30, self.state.initial_bler))
        self.state.avg_mcs = 20 + (self.state.sinr_nr3500 + 5) * 0.3 + random.uniform(-2, 2)
        self.state.avg_mcs = max(0, min(28, self.state.avg_mcs))
        self.state.rb_per_slot = 250 + random.uniform(-20, 20)
        self.state.rank_indicator = min(4, max(1, round(self.state.sinr_nr3500 / 8)))

        # RF Quality metrics
        self.state.tx_imbalance += random.uniform(-0.1, 0.1)
        self.state.tx_imbalance = max(0, min(5, self.state.tx_imbalance))
        self.state.latency_ping = self.state.latency * 0.8 + random.uniform(-2, 2)
        self.state.handover_success_rate += random.uniform(-0.5, 0.5)
        self.state.handover_success_rate = max(80, min(100, self.state.handover_success_rate))
        self.state.interference_level += random.uniform(-2, 2)
        self.state.interference_level = max(-120, min(-80, self.state.interference_level))

        # Carrier Aggregation
        self.state.ca_dl_throughput = self.state.dl_throughput_nr700 + self.state.dl_throughput_nr3500
        self.state.ca_ul_throughput = self.state.ul_throughput_nr700 + self.state.ul_throughput_nr3500

        # Power metrics
        self.state.utility_voltage += random.uniform(-5, 5)
        self.state.utility_voltage = max(200, min(250, self.state.utility_voltage))
        self.state.battery_soc += random.uniform(-0.5, 0.5)
        self.state.battery_soc = max(20, min(100, self.state.battery_soc))
        self.state.site_power_kwh += self.state.power_consumption / 1000 / 720  # Per 5 seconds

        # Uptime increases
        self.state.uptime_seconds += 5

    def _get_metrics(self, requested_types: List[MetricType]) -> List[Metric]:
        """Get current metrics for device protocol (callback for edge-bridge)"""
        all_metrics = [
            # System metrics
            Metric(MetricType.CPU_USAGE, self.state.cpu_usage),
            Metric(MetricType.MEMORY_USAGE, self.state.memory_usage),
            Metric(MetricType.TEMPERATURE, self.state.cpu_temp),
            Metric(MetricType.POWER_CONSUMPTION, self.state.power_consumption),
            Metric(MetricType.FAN_SPEED, self.state.fan_speed),

            # RF metrics
            Metric(MetricType.SIGNAL_STRENGTH, self.state.signal_strength),
            Metric(MetricType.SIGNAL_QUALITY, self.state.signal_quality),
            Metric(MetricType.VSWR, self.state.vswr),

            # Performance metrics
            Metric(MetricType.DATA_THROUGHPUT, self.state.data_throughput),
            Metric(MetricType.LATENCY, self.state.latency),
            Metric(MetricType.PACKET_LOSS, self.state.packet_loss),
            Metric(MetricType.CONNECTION_COUNT, float(self.state.active_connections)),

            # Device metrics
            Metric(MetricType.UPTIME, float(self.state.uptime_seconds)),
            Metric(MetricType.ERROR_COUNT, float(self.state.error_count)),

            # 5G NR700 metrics
            Metric(MetricType.DL_THROUGHPUT_NR700, self.state.dl_throughput_nr700),
            Metric(MetricType.UL_THROUGHPUT_NR700, self.state.ul_throughput_nr700),
            Metric(MetricType.RSRP_NR700, self.state.rsrp_nr700),
            Metric(MetricType.SINR_NR700, self.state.sinr_nr700),

            # 5G NR3500 metrics
            Metric(MetricType.DL_THROUGHPUT_NR3500, self.state.dl_throughput_nr3500),
            Metric(MetricType.UL_THROUGHPUT_NR3500, self.state.ul_throughput_nr3500),
            Metric(MetricType.RSRP_NR3500, self.state.rsrp_nr3500),
            Metric(MetricType.SINR_NR3500, self.state.sinr_nr3500),

            # 5G Radio metrics
            Metric(MetricType.PDCP_THROUGHPUT, self.state.pdcp_throughput),
            Metric(MetricType.RLC_THROUGHPUT, self.state.rlc_throughput),
            Metric(MetricType.INITIAL_BLER, self.state.initial_bler),
            Metric(MetricType.AVG_MCS, self.state.avg_mcs),
            Metric(MetricType.RB_PER_SLOT, self.state.rb_per_slot),
            Metric(MetricType.RANK_INDICATOR, self.state.rank_indicator),

            # RF Quality metrics
            Metric(MetricType.TX_IMBALANCE, self.state.tx_imbalance),
            Metric(MetricType.LATENCY_PING, self.state.latency_ping),
            Metric(MetricType.HANDOVER_SUCCESS_RATE, self.state.handover_success_rate),
            Metric(MetricType.INTERFERENCE_LEVEL, self.state.interference_level),

            # Carrier Aggregation
            Metric(MetricType.CA_DL_THROUGHPUT, self.state.ca_dl_throughput),
            Metric(MetricType.CA_UL_THROUGHPUT, self.state.ca_ul_throughput),

            # Power & Energy
            Metric(MetricType.UTILITY_VOLTAGE_L1, self.state.utility_voltage),
            Metric(MetricType.BATTERY_SOC, self.state.battery_soc),
            Metric(MetricType.SITE_POWER_KWH, self.state.site_power_kwh),
        ]

        if not requested_types:
            return all_metrics

        return [m for m in all_metrics if m.metric_type in requested_types]

    def _get_status(self) -> StatusPayload:
        """Get device status for device protocol (callback for edge-bridge)"""
        # Determine status based on current state
        if self.state.cpu_temp > 85 or self.state.memory_usage > 95:
            status = StatusCode.CRITICAL
        elif self.state.cpu_temp > 70 or self.state.memory_usage > 80:
            status = StatusCode.WARNING
        elif self.state.error_count > 0:
            status = StatusCode.ERROR
        else:
            status = StatusCode.OK

        return StatusPayload(
            status=status,
            uptime=self.state.uptime_seconds,
            errors=self.state.error_count,
            warnings=self.state.warning_count
        )

    def _execute_command(self, cmd_type: CommandType, params: bytes) -> CommandResult:
        """Execute command for device protocol (callback for edge-bridge)"""
        logger.info(f"Executing command: {cmd_type.name}, params: {params.hex() if params else 'none'}")

        if cmd_type == CommandType.RESTART:
            # Simulate restart
            self.state.memory_usage = 40
            self.state.cpu_usage = 25
            self.state.error_count = 0
            return CommandResult(True, 0, "Service restarted successfully")

        elif cmd_type == CommandType.RUN_DIAGNOSTIC:
            # Run diagnostic
            issues = []
            if self.state.cpu_temp > 70:
                issues.append(f"High CPU temp: {self.state.cpu_temp:.1f}C")
            if self.state.memory_usage > 80:
                issues.append(f"High memory: {self.state.memory_usage:.1f}%")
            if self.state.vswr > 2.0:
                issues.append(f"High VSWR: {self.state.vswr:.2f}")

            if issues:
                return CommandResult(True, 0, f"Diagnostic complete. Issues: {'; '.join(issues)}")
            return CommandResult(True, 0, "Diagnostic complete. No issues found.")

        elif cmd_type == CommandType.SET_PARAMETER:
            # Set parameter (format: key=value)
            try:
                param_str = params.decode('utf-8')
                return CommandResult(True, 0, f"Parameter set: {param_str}")
            except Exception as e:
                return CommandResult(False, 1, f"Invalid parameter format: {e}")

        else:
            return CommandResult(False, 1, f"Unsupported command: {cmd_type.name}")

    def _detect_problems(self) -> list[Problem]:
        """Check state and detect any problems"""
        problems = []

        for scenario in self.PROBLEM_SCENARIOS:
            if scenario["trigger"](self.state):
                problem = Problem(
                    id=f"PRB-{int(time.time())}-{random.randint(1000, 9999)}",
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    station_id=self.station_id,
                    category=scenario["category"].value,
                    severity=scenario["severity"].value,
                    code=scenario["code"],
                    message=scenario["message"],
                    metrics={
                        "cpu_temp": self.state.cpu_temp,
                        "cpu_usage": self.state.cpu_usage,
                        "memory_usage": self.state.memory_usage,
                        "power_consumption": self.state.power_consumption,
                        "signal_strength": self.state.signal_strength,
                        "active_connections": self.state.active_connections,
                        "initial_bler": self.state.initial_bler,
                        "handover_success_rate": self.state.handover_success_rate,
                    },
                    raw_logs=scenario["logs"]
                )
                problems.append(problem)
                self.problem_history.append(problem)
                self.state.error_count += 1

        return problems

    def send_problem(self, problem: Problem) -> Optional[Solution]:
        """Send problem to AI diagnostic service and receive solution"""
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.settimeout(30)
                sock.connect((self.diagnostic_host, self.diagnostic_port))

                # Send problem as JSON
                message = json.dumps(asdict(problem))
                sock.sendall(message.encode() + b'\n')

                logger.info(f"Sent problem {problem.code} to diagnostic service")

                # Receive solution
                response = b''
                while True:
                    chunk = sock.recv(4096)
                    if not chunk:
                        break
                    response += chunk
                    if b'\n' in response:
                        break

                if response:
                    solution_data = json.loads(response.decode().strip())
                    solution = Solution(**solution_data)
                    self.solution_history.append(solution)
                    logger.info(f"Received solution: {solution.action}")
                    return solution

        except ConnectionRefusedError:
            logger.warning("AI diagnostic service not available")
        except Exception as e:
            logger.error(f"Error communicating with diagnostic service: {e}")

        return None

    def apply_solution(self, solution: Solution):
        """Apply the received solution to the base station"""
        logger.info(f"Applying solution: {solution.action}")

        for command in solution.commands:
            logger.info(f"  Executing: {command}")

            # Simulate command effects
            if "restart" in command.lower():
                self.state.memory_usage = 40
                self.state.cpu_usage = 25
                logger.info("  -> Service restarted")

            elif "thermal" in command.lower() or "fan" in command.lower():
                self.state.cpu_temp -= 15
                logger.info(f"  -> Temperature reduced to {self.state.cpu_temp:.1f}C")

            elif "power" in command.lower():
                self.state.power_consumption *= 0.9
                logger.info(f"  -> Power reduced to {self.state.power_consumption:.0f}W")

            elif "signal" in command.lower() or "antenna" in command.lower():
                self.state.signal_strength += 10
                logger.info(f"  -> Signal improved to {self.state.signal_strength:.1f}dBm")

            time.sleep(0.5)  # Simulate execution time

        logger.info(f"Solution applied. Expected: {solution.expected_outcome}")

    def run(self, interval: float = 5.0):
        """Main simulation loop"""
        self.running = True

        # Start device protocol server
        self.protocol_server.start()

        logger.info(f"Starting Virtual MIPS Base Station: {self.station_id}")
        logger.info(f"AI Diagnostic service: {self.diagnostic_host}:{self.diagnostic_port}")
        logger.info(f"Device protocol server: 0.0.0.0:{self.protocol_port}")

        while self.running:
            try:
                # Update environment
                self._simulate_environment()

                # Log current state
                logger.debug(f"State: CPU={self.state.cpu_temp:.1f}C, "
                             f"Mem={self.state.memory_usage:.1f}%, "
                             f"Signal={self.state.signal_strength:.1f}dBm, "
                             f"DL_NR3500={self.state.dl_throughput_nr3500:.0f}Mbps")

                # Detect problems
                problems = self._detect_problems()

                for problem in problems:
                    logger.warning(f"PROBLEM DETECTED: [{problem.severity.upper()}] {problem.code}: {problem.message}")

                    # Send to AI diagnostic service
                    solution = self.send_problem(problem)

                    if solution:
                        self.apply_solution(solution)
                    else:
                        logger.info("No solution received, problem logged for manual review")

                time.sleep(interval)

            except KeyboardInterrupt:
                self.running = False
                logger.info("Shutting down virtual base station")
                break

        self.protocol_server.stop()

    def get_status(self) -> dict:
        """Get current status for API"""
        return {
            "station_id": self.station_id,
            "state": asdict(self.state),
            "problems_detected": len(self.problem_history),
            "solutions_applied": len(self.solution_history),
            "running": self.running
        }


def main():
    parser = argparse.ArgumentParser(description="Virtual MIPS Base Station Simulator")
    parser.add_argument("--station-id", default="MIPS-BS-001", help="Base station identifier")
    parser.add_argument("--host", default="localhost", help="AI diagnostic service host")
    parser.add_argument("--port", type=int, default=9090, help="AI diagnostic service port")
    parser.add_argument("--protocol-port", type=int, default=8888, help="Device protocol server port")
    parser.add_argument("--interval", type=float, default=5.0, help="Simulation interval in seconds")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")

    args = parser.parse_args()

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    station = VirtualMIPSBaseStation(
        station_id=args.station_id,
        diagnostic_host=args.host,
        diagnostic_port=args.port,
        protocol_port=args.protocol_port
    )

    station.run(interval=args.interval)


if __name__ == "__main__":
    main()
