#!/usr/bin/env python3
"""
Virtual MIPS Base Station Simulator

Simulates a real base station with MIPS CPU that:
- Generates realistic hardware/software problems
- Sends problems to AI diagnostic service
- Receives and applies solutions
- Reports back status

This allows testing the AI diagnostic pipeline without real hardware.
"""

import socket
import json
import time
import random
import threading
import logging
from dataclasses import dataclass, asdict
from enum import Enum
from typing import Optional, Dict, Any
from datetime import datetime, timezone

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
    """Current state of the virtual base station"""
    station_id: str
    cpu_temp: float  # Celsius
    cpu_usage: float  # Percentage
    memory_usage: float  # Percentage
    power_consumption: float  # Watts
    signal_strength: float  # dBm
    active_connections: int
    uptime_seconds: int
    firmware_version: str
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
    - Radio transceiver for cellular signals
    - Power management unit
    - Network interface (Ethernet/Fiber)
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
    ]

    def __init__(self, station_id: str = "MIPS-BS-001", diagnostic_host: str = "localhost", diagnostic_port: int = 9090):
        self.station_id = station_id
        self.diagnostic_host = diagnostic_host
        self.diagnostic_port = diagnostic_port
        self.running = False
        self.state = self._initial_state()
        self.problem_history = []
        self.solution_history = []

    def _initial_state(self) -> BaseStationState:
        """Initialize base station with normal operating parameters"""
        return BaseStationState(
            station_id=self.station_id,
            cpu_temp=45.0,
            cpu_usage=25.0,
            memory_usage=40.0,
            power_consumption=1500.0,  # Match configured station power
            signal_strength=-85.0,
            active_connections=50,
            uptime_seconds=0,
            firmware_version="v2.4.1-mips"
        )

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
        base_power = 1500.0  # Match configured station power
        load_factor = (self.state.cpu_usage - 50) / 50  # -1 to +1 normalized
        self.state.power_consumption = base_power + (load_factor * 300) + random.uniform(-50, 50)

        # Signal strength fluctuates
        self.state.signal_strength += random.uniform(-3, 3)
        self.state.signal_strength = max(-120, min(-60, self.state.signal_strength))

        # Active connections change
        self.state.active_connections += random.randint(-5, 8)
        self.state.active_connections = max(0, min(200, self.state.active_connections))

        # Uptime increases
        self.state.uptime_seconds += 5

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
                    },
                    raw_logs=scenario["logs"]
                )
                problems.append(problem)
                self.problem_history.append(problem)

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
        logger.info(f"Starting Virtual MIPS Base Station: {self.station_id}")
        logger.info(f"Diagnostic service: {self.diagnostic_host}:{self.diagnostic_port}")

        while self.running:
            try:
                # Update environment
                self._simulate_environment()

                # Log current state
                logger.debug(f"State: CPU={self.state.cpu_temp:.1f}C, "
                           f"Mem={self.state.memory_usage:.1f}%, "
                           f"Signal={self.state.signal_strength:.1f}dBm")

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
    import argparse

    parser = argparse.ArgumentParser(description="Virtual MIPS Base Station Simulator")
    parser.add_argument("--station-id", default="MIPS-BS-001", help="Base station identifier")
    parser.add_argument("--host", default="localhost", help="AI diagnostic service host")
    parser.add_argument("--port", type=int, default=9090, help="AI diagnostic service port")
    parser.add_argument("--interval", type=float, default=5.0, help="Simulation interval in seconds")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")

    args = parser.parse_args()

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    station = VirtualMIPSBaseStation(
        station_id=args.station_id,
        diagnostic_host=args.host,
        diagnostic_port=args.port
    )

    station.run(interval=args.interval)


if __name__ == "__main__":
    main()
