#!/usr/bin/env python3
"""
Live Data Simulator for Base Station Platform
==============================================
Generates realistic, continuously changing base station metrics and sends them
to the platform in real-time. This simulates a production environment with:

- Dynamic signal strength (affected by weather, interference, load)
- Variable throughput (peak hours, network congestion)
- Temperature fluctuations (daily cycles, equipment load)
- Random failures and recoveries
- Alert conditions (high temp, low signal, connection loss)

Usage:
    python3 testing/live-data-simulator.py [options]

Options:
    --api-url URL           API Gateway URL (default: http://localhost:30080)
    --stations NUM          Number of stations to simulate (default: 10)
    --interval SECONDS      Update interval in seconds (default: 5)
    --duration MINUTES      How long to run (default: unlimited)
    --scenario SCENARIO     Preset scenario (normal, peak_hours, failure, storm)
    --concurrent            Use concurrent workers for load testing
"""

import requests
import time
import random
import math
import argparse
from datetime import datetime, timedelta
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from typing import List, Optional, Dict, Any
import signal
import types


# Colors for terminal output
class Colors:
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    RED = "\033[91m"
    BLUE = "\033[94m"
    CYAN = "\033[96m"
    MAGENTA = "\033[95m"
    RESET = "\033[0m"
    BOLD = "\033[1m"


@dataclass
class BaseStationMetrics:
    """Real-time metrics for a base station"""

    station_id: int
    name: str
    latitude: float
    longitude: float
    signal_strength: float  # dBm (-120 to -40)
    temperature: float  # Celsius
    throughput: float  # Mbps
    connected_devices: int
    cpu_usage: float  # percentage
    memory_usage: float  # percentage
    uptime_hours: float
    status: str  # ONLINE, DEGRADED, OFFLINE
    last_update: str


class BaseStationSimulator:
    """Simulates a single base station with realistic behavior"""

    def __init__(self, station_id: int, name: str, lat: float, lon: float):
        self.station_id = station_id
        self.name = name
        self.latitude = lat
        self.longitude = lon

        # Base values (will fluctuate)
        self.base_signal = -60.0  # dBm
        self.base_temp = 45.0  # Celsius
        self.base_throughput = 150.0  # Mbps
        self.base_devices = 50

        # Current state
        self.signal_strength = self.base_signal
        self.temperature = self.base_temp
        self.throughput = self.base_throughput
        self.connected_devices = self.base_devices
        self.cpu_usage = 25.0
        self.memory_usage = 40.0
        self.uptime_hours = random.uniform(0, 720)  # 0-30 days
        self.status = "ONLINE"

        # Simulation parameters
        self.time_offset = random.uniform(0, 24)  # For daily cycles
        self.failure_probability = 0.001  # 0.1% chance per update
        self.recovery_countdown = 0

    def update(self, current_time: datetime, scenario: str = "normal"):
        """Update metrics based on time and scenario"""

        # Time-based factors
        hour_of_day = (current_time.hour + self.time_offset) % 24
        day_factor = math.sin((hour_of_day / 24) * 2 * math.pi)  # Daily cycle

        # Handle failures and recovery
        if self.recovery_countdown > 0:
            self.recovery_countdown -= 1
            if self.recovery_countdown == 0:
                self.status = "ONLINE"
                print(f"{Colors.GREEN}[RECOVERY]{Colors.RESET} {self.name} back online")
        elif random.random() < self.failure_probability and scenario != "normal":
            self.status = "OFFLINE"
            self.recovery_countdown = random.randint(5, 20)
            print(f"{Colors.RED}[FAILURE]{Colors.RESET} {self.name} went offline!")

        if self.status == "OFFLINE":
            self.signal_strength = -120.0
            self.throughput = 0.0
            self.connected_devices = 0
            self.cpu_usage = 0.0
            return

        # Scenario-based modifications
        scenario_multipliers = {
            "normal": {"load": 1.0, "temp": 1.0, "interference": 0.1},
            "peak_hours": {"load": 2.5, "temp": 1.3, "interference": 0.3},
            "storm": {"load": 0.5, "temp": 0.8, "interference": 0.8},
            "failure": {"load": 0.3, "temp": 1.5, "interference": 0.6},
        }

        mult = scenario_multipliers.get(scenario, scenario_multipliers["normal"])

        # Signal strength: affected by time of day, interference, load
        interference = random.gauss(0, 3) * mult["interference"]
        load_effect = (self.connected_devices / self.base_devices) * -5
        self.signal_strength = (
            self.base_signal + day_factor * 5 + interference + load_effect
        )
        self.signal_strength = max(-120, min(-40, self.signal_strength))

        # Temperature: daily cycle + equipment load + scenario
        equipment_heat = (self.cpu_usage / 100) * 20
        ambient_cycle = day_factor * 15  # ±15°C daily variation
        self.temperature = (self.base_temp + ambient_cycle + equipment_heat) * mult[
            "temp"
        ]
        self.temperature += random.gauss(0, 2)  # Random fluctuation
        self.temperature = max(20, min(95, self.temperature))

        # Throughput: affected by connected devices and time of day
        peak_hour_bonus = 1.0 + (day_factor * 0.3) if 8 <= hour_of_day <= 22 else 0.7
        device_factor = (self.connected_devices / self.base_devices) * 0.8 + 0.2
        self.throughput = (
            self.base_throughput * peak_hour_bonus * device_factor * mult["load"]
        )
        self.throughput += random.gauss(0, 10)
        self.throughput = max(0, min(1000, self.throughput))

        # Connected devices: peak during day, low at night
        peak_multiplier = 1.5 if 8 <= hour_of_day <= 22 else 0.4
        self.connected_devices = int(self.base_devices * peak_multiplier * mult["load"])
        self.connected_devices += random.randint(-10, 10)
        self.connected_devices = max(0, self.connected_devices)

        # CPU usage: correlates with load
        load_cpu = (self.connected_devices / self.base_devices) * 60
        self.cpu_usage = load_cpu + random.gauss(15, 5)
        self.cpu_usage = max(5, min(100, self.cpu_usage))

        # Memory usage: slowly increases over time, occasional spikes
        self.memory_usage += random.gauss(0, 0.5)
        if random.random() < 0.05:  # 5% chance of memory spike
            self.memory_usage += random.uniform(10, 20)
        self.memory_usage = max(20, min(95, self.memory_usage))

        # Status based on conditions
        if self.temperature > 85:
            self.status = "DEGRADED"
        elif self.signal_strength < -100:
            self.status = "DEGRADED"
        elif self.cpu_usage > 90:
            self.status = "DEGRADED"
        else:
            self.status = "ONLINE"

        self.uptime_hours += 5 / 3600  # Assuming 5-second updates

    def get_metrics(self) -> BaseStationMetrics:
        """Get current metrics"""
        return BaseStationMetrics(
            station_id=self.station_id,
            name=self.name,
            latitude=self.latitude,
            longitude=self.longitude,
            signal_strength=round(self.signal_strength, 2),
            temperature=round(self.temperature, 2),
            throughput=round(self.throughput, 2),
            connected_devices=self.connected_devices,
            cpu_usage=round(self.cpu_usage, 2),
            memory_usage=round(self.memory_usage, 2),
            uptime_hours=round(self.uptime_hours, 2),
            status=self.status,
            last_update=datetime.now().isoformat(),
        )


class LiveDataSimulator:
    """Main simulator orchestrating multiple base stations"""

    def __init__(self, api_url: str, num_stations: int, interval: int):
        self.api_url = api_url.rstrip("/")
        self.interval = interval
        self.stations: List[BaseStationSimulator] = []
        self.token: Optional[str] = None
        self.running = True
        self.stats = {
            "total_updates": 0,
            "successful_updates": 0,
            "failed_updates": 0,
            "alerts_sent": 0,
        }

        # Initialize stations
        self._initialize_stations(num_stations)

        # Setup signal handler for graceful shutdown
        signal.signal(signal.SIGINT, self._signal_handler)

    def _signal_handler(self, sig: int, frame: Optional[types.FrameType]) -> None:
        print(
            f"\n{Colors.YELLOW}[SHUTDOWN]{Colors.RESET} Gracefully stopping simulator..."
        )
        self.running = False

    def _initialize_stations(self, num_stations: int):
        """Create simulated base stations across a geographic area"""
        print(
            f"{Colors.BLUE}[INIT]{Colors.RESET} Creating {num_stations} base stations..."
        )

        # Simulate stations across Beijing area (example coordinates)
        base_lat, base_lon = 39.9042, 116.4074

        for i in range(num_stations):
            station_id = i + 1  # Use numeric IDs 1, 2, 3...
            name = f"Simulated Station {i + 1}"

            # Spread stations in a grid pattern
            lat_offset = (i % 5) * 0.1 - 0.2
            lon_offset = (i // 5) * 0.1 - 0.2

            lat = base_lat + lat_offset
            lon = base_lon + lon_offset

            station = BaseStationSimulator(station_id, name, lat, lon)
            self.stations.append(station)

        print(f"{Colors.GREEN}[INIT]{Colors.RESET} {len(self.stations)} stations ready")

    def authenticate(self) -> bool:
        """Get JWT token"""
        try:
            response = requests.post(
                f"{self.api_url}/api/v1/auth/login",
                json={"username": "admin", "password": "adminPassword123!"},
                timeout=10,
            )

            if response.status_code == 200:
                data = response.json()
                self.token = data.get("token")
                print(f"{Colors.GREEN}[AUTH]{Colors.RESET} Successfully authenticated")
                return True
            else:
                print(
                    f"{Colors.RED}[AUTH]{Colors.RESET} Login failed: {response.status_code}"
                )
                return False

        except Exception as e:
            print(f"{Colors.RED}[AUTH]{Colors.RESET} Authentication error: {e}")
            return False

    def send_metrics(self, metrics: BaseStationMetrics) -> bool:
        """Send individual metrics to the monitoring service"""
        try:
            headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}

            # Define all metrics to send individually
            metric_payloads: List[Dict[str, Any]] = [
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "SIGNAL_STRENGTH",
                    "value": metrics.signal_strength,
                    "unit": "dBm",
                },
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "TEMPERATURE",
                    "value": metrics.temperature,
                    "unit": "°C",
                },
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "DATA_THROUGHPUT",
                    "value": metrics.throughput,
                    "unit": "Mbps",
                },
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "CONNECTION_COUNT",
                    "value": float(metrics.connected_devices),
                    "unit": "connections",
                },
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "CPU_USAGE",
                    "value": metrics.cpu_usage,
                    "unit": "%",
                },
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "MEMORY_USAGE",
                    "value": metrics.memory_usage,
                    "unit": "%",
                },
                {
                    "stationId": metrics.station_id,
                    "stationName": metrics.name,
                    "metricType": "UPTIME",
                    "value": min(
                        metrics.uptime_hours / (metrics.uptime_hours + 1) * 100, 100.0
                    ),
                    "unit": "%",
                },
            ]

            # Send each metric individually
            all_successful = True
            for payload in metric_payloads:
                response = requests.post(
                    f"{self.api_url}/api/v1/metrics",
                    json=payload,
                    headers=headers,
                    timeout=5,
                )

                if response.status_code not in [200, 201]:
                    all_successful = False

            if all_successful:
                self.stats["successful_updates"] += 1
                return True
            else:
                self.stats["failed_updates"] += 1
                return False

        except Exception:
            self.stats["failed_updates"] += 1
            return False

    def send_alert(self, station: BaseStationSimulator, alert_type: str, message: str):
        """Send alert notification"""
        try:
            headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}

            payload: Dict[str, Any] = {
                "stationId": station.station_id,
                "type": alert_type,
                "message": message,
                "severity": "HIGH"
                if alert_type in ["TEMPERATURE", "OFFLINE"]
                else "MEDIUM",
                "timestamp": datetime.now().isoformat(),
            }

            response = requests.post(
                f"{self.api_url}/api/v1/notifications",
                json=payload,
                headers=headers,
                timeout=5,
            )

            if response.status_code in [200, 201]:
                self.stats["alerts_sent"] += 1
                print(
                    f"{Colors.MAGENTA}[ALERT]{Colors.RESET} {station.name}: {message}"
                )

        except Exception:
            pass

    def check_alerts(self, station: BaseStationSimulator):
        """Check if station metrics trigger alerts"""
        metrics = station.get_metrics()

        if metrics.temperature > 80:
            self.send_alert(
                station,
                "TEMPERATURE",
                f"High temperature: {metrics.temperature}°C (threshold: 80°C)",
            )

        if metrics.signal_strength < -100:
            self.send_alert(
                station, "SIGNAL", f"Low signal strength: {metrics.signal_strength} dBm"
            )

        if metrics.status == "OFFLINE":
            self.send_alert(
                station, "OFFLINE", "Station offline - investigating connectivity"
            )

        if metrics.cpu_usage > 90:
            self.send_alert(station, "CPU", f"High CPU usage: {metrics.cpu_usage}%")

    def print_summary(self, scenario: str):
        """Print current status summary"""
        online = sum(1 for s in self.stations if s.status == "ONLINE")
        degraded = sum(1 for s in self.stations if s.status == "DEGRADED")
        offline = sum(1 for s in self.stations if s.status == "OFFLINE")

        avg_signal = sum(s.signal_strength for s in self.stations) / len(self.stations)
        avg_temp = sum(s.temperature for s in self.stations) / len(self.stations)
        avg_throughput = sum(s.throughput for s in self.stations) / len(self.stations)
        total_devices = sum(s.connected_devices for s in self.stations)

        print(f"\n{Colors.BOLD}{'=' * 80}{Colors.RESET}")
        print(
            f"{Colors.CYAN}[SUMMARY]{Colors.RESET} Scenario: {scenario.upper()} | "
            f"Updates: {self.stats['total_updates']} | "
            f"Success: {self.stats['successful_updates']} | "
            f"Failed: {self.stats['failed_updates']} | "
            f"Alerts: {self.stats['alerts_sent']}"
        )
        print(f"{Colors.BOLD}{'=' * 80}{Colors.RESET}")

        print(
            f"  {Colors.GREEN}ONLINE:{Colors.RESET} {online} | "
            f"{Colors.YELLOW}DEGRADED:{Colors.RESET} {degraded} | "
            f"{Colors.RED}OFFLINE:{Colors.RESET} {offline}"
        )

        print(
            f"  Avg Signal: {avg_signal:.1f} dBm | "
            f"Avg Temp: {avg_temp:.1f}°C | "
            f"Avg Throughput: {avg_throughput:.1f} Mbps | "
            f"Total Devices: {total_devices}"
        )
        print("")

    def _print_header(self, scenario: str, concurrent: bool):
        """Print simulation header"""
        print(
            f"\n{Colors.BOLD}{Colors.BLUE}Base Station Live Data Simulator{Colors.RESET}"
        )
        print(f"{'=' * 80}\n")
        print(f"API URL: {self.api_url}")
        print(f"Stations: {len(self.stations)}")
        print(f"Update Interval: {self.interval}s")
        print(f"Scenario: {scenario.upper()}")
        print(f"Concurrent Mode: {'ON' if concurrent else 'OFF'}")
        print(f"\n{Colors.BOLD}{'=' * 80}{Colors.RESET}\n")

    def _print_start_info(self, start_time: datetime, end_time: Optional[datetime]):
        """Print simulation start information"""
        print(
            f"{Colors.GREEN}[START]{Colors.RESET} Simulation started at {start_time.strftime('%H:%M:%S')}"
        )
        if end_time:
            print(
                f"{Colors.YELLOW}[INFO]{Colors.RESET} Will run until {end_time.strftime('%H:%M:%S')}"
            )
        print("")

    def _update_stations(self, current_time: datetime, scenario: str):
        """Update all stations with new data"""
        for station in self.stations:
            station.update(current_time, scenario)

    def _send_all_metrics(self, concurrent: bool):
        """Send metrics for all stations"""
        if concurrent:
            with ThreadPoolExecutor(max_workers=10) as executor:
                futures = [
                    executor.submit(self.send_metrics, station.get_metrics())
                    for station in self.stations
                ]
                [f.result() for f in futures]
        else:
            for station in self.stations:
                self.send_metrics(station.get_metrics())

        self.stats["total_updates"] += len(self.stations)

    def _check_all_alerts(self):
        """Check alert conditions for all stations"""
        for station in self.stations:
            self.check_alerts(station)

    def _print_final_summary(self, start_time: datetime):
        """Print final simulation summary"""
        print(f"\n{Colors.BOLD}{Colors.GREEN}Simulation Complete{Colors.RESET}")
        print(f"{'=' * 80}")
        print(f"Runtime: {(datetime.now() - start_time).total_seconds():.0f}s")
        print(f"Total Updates: {self.stats['total_updates']}")
        print(
            f"Successful: {self.stats['successful_updates']} ({self.stats['successful_updates'] / max(1, self.stats['total_updates']) * 100:.1f}%)"
        )
        print(f"Failed: {self.stats['failed_updates']}")
        print(f"Alerts Sent: {self.stats['alerts_sent']}")
        print(f"{'=' * 80}\n")

    def run(
        self,
        duration_minutes: Optional[int] = None,
        scenario: str = "normal",
        concurrent: bool = False,
    ):
        """Run the simulation"""
        self._print_header(scenario, concurrent)

        # Authenticate
        if not self.authenticate():
            print(
                f"{Colors.RED}[ERROR]{Colors.RESET} Cannot proceed without authentication"
            )
            return

        start_time = datetime.now()
        end_time = (
            start_time + timedelta(minutes=duration_minutes)
            if duration_minutes
            else None
        )

        self._print_start_info(start_time, end_time)

        iteration = 0

        try:
            while self.running:
                iteration += 1
                current_time = datetime.now()

                # Check if duration exceeded
                if end_time and current_time >= end_time:
                    print(
                        f"\n{Colors.YELLOW}[COMPLETE]{Colors.RESET} Duration limit reached"
                    )
                    break

                self._update_stations(current_time, scenario)
                self._send_all_metrics(concurrent)
                self._check_all_alerts()

                # Print summary every 10 iterations
                if iteration % 10 == 0:
                    self.print_summary(scenario)

                time.sleep(self.interval)

        except KeyboardInterrupt:
            pass

        self._print_final_summary(start_time)


def main():
    parser = argparse.ArgumentParser(
        description="Live Data Simulator for Base Station Platform"
    )
    parser.add_argument(
        "--api-url", default="http://localhost:30080", help="API Gateway URL"
    )
    parser.add_argument(
        "--stations", type=int, default=10, help="Number of stations to simulate"
    )
    parser.add_argument(
        "--interval", type=int, default=5, help="Update interval in seconds"
    )
    parser.add_argument(
        "--duration", type=int, help="Duration in minutes (unlimited if not specified)"
    )
    parser.add_argument(
        "--scenario",
        default="normal",
        choices=["normal", "peak_hours", "storm", "failure"],
        help="Simulation scenario",
    )
    parser.add_argument(
        "--concurrent",
        action="store_true",
        help="Use concurrent updates for load testing",
    )

    args = parser.parse_args()

    simulator = LiveDataSimulator(args.api_url, args.stations, args.interval)
    simulator.run(args.duration, args.scenario, args.concurrent)


if __name__ == "__main__":
    main()
