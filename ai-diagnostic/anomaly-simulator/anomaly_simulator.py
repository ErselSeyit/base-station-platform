#!/usr/bin/env python3
"""
Anomaly Simulator - Generates realistic base station anomalies for testing AI diagnostics.

This simulator periodically injects anomalous metrics that trigger the AI diagnostic
system, allowing real-time demonstration of problem detection and solution generation.

Supports both:
- HTTP API (direct to monitoring-service)
- Binary protocol (via edge-bridge/device-simulator protocol)
"""

import json
import random
import socket
import struct
import time
import logging
import hmac
import hashlib
import os
import requests
from datetime import datetime
from datetime import timezone
from typing import Dict, List, Optional

# Import binary protocol
from device_protocol import (
    MessageType, MetricType, Metric, Message,
    build_frame, FrameParser, encode_metrics
)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('anomaly-simulator')

# Configuration
MONITORING_SERVICE_URL = "http://monitoring-service:8082"
AI_DIAGNOSTIC_URL = "http://ai-diagnostic:9091"
DEVICE_PROTOCOL_HOST = "device-simulator"
DEVICE_PROTOCOL_PORT = 9999
STATION_ID = 1
STATION_NAME = "BS-001"

# Anomaly scenarios with their problem codes and metric patterns
# These map to problem codes in DiagnosticSessionService.metricTypeToProblemCode()
ANOMALY_SCENARIOS = [
    {
        "name": "CPU Overheat",
        "code": "CPU_OVERHEAT",
        "category": "THERMAL",
        "severity": "critical",
        "metrics": {
            "CPU_USAGE": (92, 99),      # High CPU
            "TEMPERATURE": (82, 95),     # High temp
            "MEMORY_USAGE": (60, 75),    # Normal-ish memory
        },
        "message": "CPU temperature exceeded safe operating threshold",
        "duration_seconds": 60,
        "probability": 0.10,
    },
    {
        "name": "Memory Pressure",
        "code": "MEMORY_PRESSURE",
        "category": "RESOURCE",
        "severity": "critical",
        "metrics": {
            "MEMORY_USAGE": (96, 99),    # Critical memory
            "CPU_USAGE": (70, 85),       # Elevated CPU
            "TEMPERATURE": (55, 65),     # Slightly elevated temp
        },
        "message": "System memory critically low, risk of OOM",
        "duration_seconds": 45,
        "probability": 0.10,
    },
    {
        "name": "Signal Degradation",
        "code": "SIGNAL_DEGRADATION",
        "category": "RF",
        "severity": "warning",
        "metrics": {
            "SIGNAL_STRENGTH": (-105, -95),  # Weak signal (below -100 threshold)
            "RSRP_NR3500": (-110, -100),     # Poor RSRP
            "SINR_NR3500": (2, 8),           # Low SINR
        },
        "message": "RF signal quality degraded below acceptable threshold",
        "duration_seconds": 60,
        "probability": 0.10,
    },
    {
        "name": "High Latency",
        "code": "HIGH_LATENCY",
        "category": "NETWORK",
        "severity": "critical",
        "metrics": {
            "LATENCY_PING": (110, 200),      # Critical latency (>100ms threshold)
            "DATA_THROUGHPUT": (30, 45),     # Reduced throughput
        },
        "message": "Network latency exceeded critical threshold",
        "duration_seconds": 60,
        "probability": 0.10,
    },
    {
        "name": "High Power Consumption",
        "code": "HIGH_POWER_CONSUMPTION",
        "category": "POWER",
        "severity": "critical",
        "metrics": {
            "POWER_CONSUMPTION": (720, 900),  # Critical power (>700W threshold)
            "TEMPERATURE": (72, 82),          # Elevated temp
        },
        "message": "Power consumption exceeded critical threshold",
        "duration_seconds": 45,
        "probability": 0.10,
    },
    {
        "name": "High Interference",
        "code": "HIGH_INTERFERENCE",
        "category": "RF",
        "severity": "warning",
        "metrics": {
            "INTERFERENCE_LEVEL": (-68, -60),  # High interference (>-70 dBm threshold)
            "SINR_NR3500": (-2, 5),            # Very low SINR
            "SINR_NR700": (0, 6),
        },
        "message": "High RF interference detected on NR bands",
        "duration_seconds": 60,
        "probability": 0.10,
    },
    {
        "name": "High Block Error Rate",
        "code": "HIGH_BLOCK_ERROR_RATE",
        "category": "RF",
        "severity": "critical",
        "metrics": {
            "INITIAL_BLER": (32, 50),         # Critical BLER (>30% threshold)
            "SINR_NR3500": (3, 8),             # Degraded SINR
        },
        "message": "Block error rate exceeded critical threshold",
        "duration_seconds": 60,
        "probability": 0.10,
    },
    {
        "name": "Low Battery",
        "code": "LOW_BATTERY",
        "category": "POWER",
        "severity": "critical",
        "metrics": {
            "BATTERY_SOC": (5, 9),             # Critical battery (<10% threshold)
            "POWER_CONSUMPTION": (400, 500),   # Normal power draw
        },
        "message": "Battery state of charge critically low",
        "duration_seconds": 45,
        "probability": 0.10,
    },
    {
        "name": "Low Throughput",
        "code": "LOW_THROUGHPUT",
        "category": "NETWORK",
        "severity": "critical",
        "metrics": {
            "DATA_THROUGHPUT": (15, 19),      # Critical throughput (<20 Mbps threshold)
            "LATENCY_PING": (40, 60),         # Elevated latency
        },
        "message": "Data throughput critically low",
        "duration_seconds": 60,
        "probability": 0.10,
    },
    {
        "name": "Handover Failure",
        "code": "HANDOVER_FAILURE",
        "category": "NETWORK",
        "severity": "critical",
        "metrics": {
            "HANDOVER_SUCCESS_RATE": (85, 89),  # Critical rate (<90% threshold)
            "SIGNAL_STRENGTH": (-90, -80),      # Borderline signal
        },
        "message": "Handover success rate below critical threshold",
        "duration_seconds": 60,
        "probability": 0.10,
    },
]


class AnomalySimulator:
    """Simulates anomalies and sends them to monitoring + AI diagnostic services."""

    # Map metric type strings to protocol MetricType enum
    METRIC_TYPE_MAP = {
        "CPU_USAGE": MetricType.CPU_USAGE,
        "MEMORY_USAGE": MetricType.MEMORY_USAGE,
        "TEMPERATURE": MetricType.TEMPERATURE,
        "POWER_CONSUMPTION": MetricType.POWER_CONSUMPTION,
        "SIGNAL_STRENGTH": MetricType.SIGNAL_STRENGTH,
        "DL_THROUGHPUT_NR3500": MetricType.DL_THROUGHPUT_NR3500,
        "UL_THROUGHPUT_NR3500": MetricType.UL_THROUGHPUT_NR3500,
        "RSRP_NR3500": MetricType.RSRP_NR3500,
        "SINR_NR3500": MetricType.SINR_NR3500,
        "DL_THROUGHPUT_NR700": MetricType.DL_THROUGHPUT_NR700,
        "UL_THROUGHPUT_NR700": MetricType.UL_THROUGHPUT_NR700,
        "RSRP_NR700": MetricType.RSRP_NR700,
        "SINR_NR700": MetricType.SINR_NR700,
        "LATENCY_PING": MetricType.LATENCY_PING,
        "TX_IMBALANCE": MetricType.TX_IMBALANCE,
        "INITIAL_BLER": MetricType.INITIAL_BLER,
        "BATTERY_SOC": MetricType.BATTERY_SOC,
        "DATA_THROUGHPUT": MetricType.DATA_THROUGHPUT,
        "HANDOVER_SUCCESS_RATE": MetricType.HANDOVER_SUCCESS_RATE,
        "INTERFERENCE_LEVEL": MetricType.INTERFERENCE_LEVEL,
    }

    def __init__(self, monitoring_url: str, diagnostic_url: str, station_id: int, station_name: str,
                 internal_secret: str = "", protocol_host: str = "", protocol_port: int = 9999,
                 use_protocol: bool = False):
        self.monitoring_url = monitoring_url
        self.diagnostic_url = diagnostic_url
        self.station_id = station_id
        self.station_name = station_name
        self.internal_secret = internal_secret
        self.active_anomaly: Optional[Dict] = None
        self.anomaly_end_time: float = 0
        self.problem_counter = 0
        self.session = requests.Session()
        # Rotate through anomalies instead of random selection
        self.anomaly_index = 0
        # Binary protocol configuration
        self.protocol_host = protocol_host
        self.protocol_port = protocol_port
        self.use_protocol = use_protocol
        self.protocol_socket: Optional[socket.socket] = None
        self.protocol_sequence = 0

    def _compute_hmac(self, payload: str) -> str:
        """Compute HMAC-SHA256 signature for internal auth."""
        return hmac.new(
            self.internal_secret.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()

    def _get_auth_headers(self) -> Dict[str, str]:
        """Get auth headers with HMAC signature for service-to-service auth."""
        headers = {
            "X-User-Name": "anomaly-simulator",
            "X-User-Role": "SERVICE",
            "Content-Type": "application/json"
        }

        # Add HMAC signature if internal secret is configured
        if self.internal_secret:
            timestamp = int(time.time() * 1000)
            payload = f"anomaly-simulator:SERVICE:{timestamp}"
            signature = self._compute_hmac(payload)
            headers["X-Internal-Auth"] = f"{signature}.{payload}"

        return headers

    def generate_metric_value(self, metric_type: str, anomaly: Optional[Dict] = None) -> float:
        """Generate a metric value, either normal or anomalous."""
        # Normal ranges - values that won't trigger alerts
        normal_ranges = {
            "CPU_USAGE": (15, 45),
            "MEMORY_USAGE": (40, 65),
            "TEMPERATURE": (35, 55),
            "POWER_CONSUMPTION": (200, 450),        # Normal: <500W warning threshold
            "SIGNAL_STRENGTH": (-75, -55),          # Normal: >-100 dBm threshold
            "DL_THROUGHPUT_NR3500": (800, 1400),
            "UL_THROUGHPUT_NR3500": (60, 100),
            "RSRP_NR3500": (-85, -70),
            "SINR_NR3500": (12, 25),
            "DL_THROUGHPUT_NR700": (50, 90),
            "UL_THROUGHPUT_NR700": (15, 35),
            "RSRP_NR700": (-90, -75),
            "SINR_NR700": (8, 18),
            "LATENCY_PING": (5, 25),                # Normal: <50ms warning threshold
            "TX_IMBALANCE": (0.5, 2.0),
            # New metrics for extended AI diagnostics
            "INITIAL_BLER": (1, 10),                # Normal: <15% warning threshold
            "BATTERY_SOC": (60, 95),                # Normal: >20% warning threshold
            "DATA_THROUGHPUT": (80, 200),           # Normal: >50 Mbps warning threshold
            "HANDOVER_SUCCESS_RATE": (96, 99),      # Normal: >95% warning threshold
            "INTERFERENCE_LEVEL": (-95, -85),       # Normal: <-80 dBm warning threshold
        }

        # Check if this metric should be anomalous
        if anomaly and metric_type in anomaly.get("metrics", {}):
            low, high = anomaly["metrics"][metric_type]
            return random.uniform(low, high)

        # Normal value
        if metric_type in normal_ranges:
            low, high = normal_ranges[metric_type]
            return random.uniform(low, high)

        return random.uniform(0, 100)

    def get_unit(self, metric_type: str) -> str:
        """Get the unit for a metric type."""
        units = {
            "CPU_USAGE": "%",
            "MEMORY_USAGE": "%",
            "TEMPERATURE": "°C",
            "POWER_CONSUMPTION": "W",
            "SIGNAL_STRENGTH": "dBm",
            "DL_THROUGHPUT_NR3500": "Mbps",
            "UL_THROUGHPUT_NR3500": "Mbps",
            "RSRP_NR3500": "dBm",
            "SINR_NR3500": "dB",
            "DL_THROUGHPUT_NR700": "Mbps",
            "UL_THROUGHPUT_NR700": "Mbps",
            "RSRP_NR700": "dBm",
            "SINR_NR700": "dB",
            "LATENCY_PING": "ms",
            "TX_IMBALANCE": "dB",
            # New metrics for extended AI diagnostics
            "INITIAL_BLER": "%",
            "BATTERY_SOC": "%",
            "DATA_THROUGHPUT": "Mbps",
            "HANDOVER_SUCCESS_RATE": "%",
            "INTERFERENCE_LEVEL": "dBm",
        }
        return units.get(metric_type, "")

    def select_anomaly(self) -> Optional[Dict]:
        """Select the next anomaly in rotation (cycles through all types)."""
        # Always return the next anomaly in sequence
        scenario = ANOMALY_SCENARIOS[self.anomaly_index]
        self.anomaly_index = (self.anomaly_index + 1) % len(ANOMALY_SCENARIOS)
        return scenario

    def _connect_protocol(self) -> bool:
        """Connect to device-simulator via binary protocol."""
        if self.protocol_socket:
            return True
        try:
            self.protocol_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.protocol_socket.settimeout(5.0)
            self.protocol_socket.connect((self.protocol_host, self.protocol_port))
            logger.info(f"Connected to device protocol at {self.protocol_host}:{self.protocol_port}")
            return True
        except Exception as e:
            logger.error(f"Failed to connect to device protocol: {e}")
            self.protocol_socket = None
            return False

    def _send_protocol_metrics(self, metrics: List[Metric]) -> bool:
        """Send metrics via binary protocol."""
        if not self._connect_protocol():
            return False

        try:
            self.protocol_sequence = (self.protocol_sequence + 1) % 256
            payload = encode_metrics(metrics)
            msg = Message(MessageType.METRICS_EVENT, self.protocol_sequence, payload)
            frame = build_frame(msg)
            self.protocol_socket.sendall(frame)
            logger.debug(f"Sent {len(metrics)} metrics via protocol")
            return True
        except Exception as e:
            logger.error(f"Failed to send protocol metrics: {e}")
            self.protocol_socket = None
            return False

    def send_metrics(self, anomaly: Optional[Dict] = None) -> bool:
        """Send a batch of metrics to the monitoring service."""
        metric_types = [
            # System metrics
            "CPU_USAGE", "MEMORY_USAGE", "TEMPERATURE", "POWER_CONSUMPTION",
            # 5G NR metrics
            "SIGNAL_STRENGTH", "DL_THROUGHPUT_NR3500", "UL_THROUGHPUT_NR3500",
            "RSRP_NR3500", "SINR_NR3500", "DL_THROUGHPUT_NR700", "UL_THROUGHPUT_NR700",
            "RSRP_NR700", "SINR_NR700", "LATENCY_PING", "TX_IMBALANCE",
            # Extended metrics for AI diagnostics
            "INITIAL_BLER", "BATTERY_SOC", "DATA_THROUGHPUT",
            "HANDOVER_SUCCESS_RATE", "INTERFERENCE_LEVEL",
        ]

        timestamp = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
        metrics = []

        for metric_type in metric_types:
            value = self.generate_metric_value(metric_type, anomaly)
            metrics.append({
                "type": metric_type,
                "value": value,
                "timestamp": timestamp
            })

        # Send via binary protocol if enabled
        if self.use_protocol:
            protocol_metrics = []
            for m in metrics:
                if m["type"] in self.METRIC_TYPE_MAP:
                    protocol_metrics.append(Metric(self.METRIC_TYPE_MAP[m["type"]], m["value"]))
            if protocol_metrics:
                self._send_protocol_metrics(protocol_metrics)

        # Batch request format expected by the API
        batch_request = {
            "stationId": self.station_name,
            "metrics": metrics
        }

        try:
            response = self.session.post(
                f"{self.monitoring_url}/api/v1/metrics/batch",
                json=batch_request,
                headers=self._get_auth_headers(),
                timeout=10
            )
            if response.ok:
                logger.debug(f"Sent {len(metrics)} metrics via HTTP")
                return True
            else:
                logger.warning(f"Failed to send metrics: {response.status_code} - {response.text[:200]}")
                return False
        except Exception as e:
            logger.error(f"Error sending metrics: {e}")
            return False

    def send_problem_to_ai(self, anomaly: Dict) -> Optional[Dict]:
        """Send the problem directly to AI diagnostic service."""
        self.problem_counter += 1
        problem_id = f"anomaly-{self.problem_counter:04d}-{int(time.time())}"
        timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

        # Collect current anomalous metric values
        metrics_snapshot = {}
        for metric_type in anomaly.get("metrics", {}).keys():
            metrics_snapshot[metric_type] = self.generate_metric_value(metric_type, anomaly)

        problem = {
            "id": problem_id,
            "timestamp": timestamp,
            "station_id": str(self.station_id),
            "category": anomaly["category"],
            "severity": anomaly["severity"],
            "code": anomaly["code"],
            "message": anomaly["message"],
            "metrics": metrics_snapshot,
            "raw_logs": f"ANOMALY DETECTED: {anomaly['name']} at station {self.station_name}"
        }

        try:
            response = self.session.post(
                f"{self.diagnostic_url}/diagnose",
                json=problem,
                headers=self._get_auth_headers(),
                timeout=30
            )
            if response.ok:
                solution = response.json()
                logger.info(f"AI Solution received for {anomaly['code']}:")
                logger.info(f"  Action: {solution.get('action', 'N/A')}")
                logger.info(f"  Confidence: {solution.get('confidence', 0):.0%}")
                logger.info(f"  Risk Level: {solution.get('risk_level', 'N/A')}")

                # Log auto-healing status if present
                healing = solution.get('healing')
                if healing:
                    if healing.get('error'):
                        logger.warning(f"  Auto-healing error: {healing['error']}")
                    else:
                        logger.info(f"  Auto-Healing: {healing.get('status', 'N/A')}")
                        logger.info(f"    Action ID: {healing.get('action_id', 'N/A')}")
                        logger.info(f"    Auto-execute: {healing.get('auto_execute', False)}")

                return solution
            else:
                logger.warning(f"AI diagnostic failed: {response.status_code} - {response.text[:200]}")
                return None
        except Exception as e:
            logger.error(f"Error calling AI diagnostic: {e}")
            return None

    def run(self, interval_seconds: float = 5.0, anomaly_check_interval: float = 30.0):
        """Main loop - send metrics and occasionally trigger anomalies."""
        logger.info("Starting Anomaly Simulator")
        logger.info(f"  Monitoring Service: {self.monitoring_url}")
        logger.info(f"  AI Diagnostic: {self.diagnostic_url}")
        logger.info(f"  Station: {self.station_name} (ID: {self.station_id})")
        logger.info(f"  Metric interval: {interval_seconds}s")
        logger.info(f"  Anomaly check interval: {anomaly_check_interval}s")
        if self.use_protocol:
            logger.info(f"  Binary Protocol: {self.protocol_host}:{self.protocol_port}")

        last_anomaly_check = 0

        while True:
            try:
                current_time = time.time()

                # Check if current anomaly has ended
                if self.active_anomaly and current_time > self.anomaly_end_time:
                    logger.info(f"Anomaly '{self.active_anomaly['name']}' resolved")
                    self.active_anomaly = None

                # Check for new anomaly
                if not self.active_anomaly and (current_time - last_anomaly_check) > anomaly_check_interval:
                    last_anomaly_check = current_time
                    new_anomaly = self.select_anomaly()
                    if new_anomaly:
                        self.active_anomaly = new_anomaly
                        self.anomaly_end_time = current_time + new_anomaly["duration_seconds"]
                        logger.warning(f">>> ANOMALY STARTED: {new_anomaly['name']} ({new_anomaly['code']})")
                        logger.warning(f"    Severity: {new_anomaly['severity']}")
                        logger.warning(f"    Duration: {new_anomaly['duration_seconds']}s")

                        # Send problem to AI diagnostic
                        self.send_problem_to_ai(new_anomaly)

                # Send metrics
                self.send_metrics(self.active_anomaly)

                time.sleep(interval_seconds)

            except KeyboardInterrupt:
                logger.info("Shutting down...")
                break
            except Exception as e:
                logger.error(f"Error in main loop: {e}")
                time.sleep(5)


def main():
    monitoring_url = os.environ.get("MONITORING_SERVICE_URL", MONITORING_SERVICE_URL)
    diagnostic_url = os.environ.get("AI_DIAGNOSTIC_URL", AI_DIAGNOSTIC_URL)
    station_id = int(os.environ.get("STATION_ID", STATION_ID))
    station_name = os.environ.get("STATION_NAME", STATION_NAME)
    interval = float(os.environ.get("METRIC_INTERVAL", "5"))
    anomaly_interval = float(os.environ.get("ANOMALY_CHECK_INTERVAL", "30"))
    internal_secret = os.environ.get("SECURITY_INTERNAL_SECRET", "")

    # Binary protocol configuration
    protocol_host = os.environ.get("DEVICE_PROTOCOL_HOST", DEVICE_PROTOCOL_HOST)
    protocol_port = int(os.environ.get("DEVICE_PROTOCOL_PORT", DEVICE_PROTOCOL_PORT))
    use_protocol = os.environ.get("USE_DEVICE_PROTOCOL", "false").lower() == "true"

    if not internal_secret:
        logger.warning("SECURITY_INTERNAL_SECRET not set - metrics sending may fail")

    simulator = AnomalySimulator(
        monitoring_url=monitoring_url,
        diagnostic_url=diagnostic_url,
        station_id=station_id,
        station_name=station_name,
        internal_secret=internal_secret,
        protocol_host=protocol_host,
        protocol_port=protocol_port,
        use_protocol=use_protocol
    )

    if use_protocol:
        logger.info(f"Binary protocol enabled: {protocol_host}:{protocol_port}")

    simulator.run(
        interval_seconds=interval,
        anomaly_check_interval=anomaly_interval
    )


if __name__ == "__main__":
    main()
