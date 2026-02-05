#!/usr/bin/env python3
"""
Anomaly Simulator - Generates realistic base station anomalies for testing AI diagnostics.

This simulator periodically injects anomalous metrics that trigger the AI diagnostic
system, allowing real-time demonstration of problem detection and solution generation.
"""

import json
import random
import time
import logging
import hmac
import hashlib
import os
import requests
from datetime import datetime
from dataclasses import dataclass, asdict
from typing import List, Dict, Any, Optional

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('anomaly-simulator')

# Configuration
MONITORING_SERVICE_URL = "http://monitoring-service:8082"
AI_DIAGNOSTIC_URL = "http://ai-diagnostic:9091"
STATION_ID = 1
STATION_NAME = "BS-001"

# Anomaly scenarios with their problem codes and metric patterns
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
        "probability": 0.15,
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
        "probability": 0.12,
    },
    {
        "name": "Signal Degradation",
        "code": "SIGNAL_DEGRADATION",
        "category": "RF",
        "severity": "warning",
        "metrics": {
            "SIGNAL_STRENGTH": (-105, -95),  # Weak signal
            "RSRP_NR3500": (-110, -100),     # Poor RSRP
            "SINR_NR3500": (2, 8),           # Low SINR
        },
        "message": "RF signal quality degraded below acceptable threshold",
        "duration_seconds": 90,
        "probability": 0.20,
    },
    {
        "name": "Backhaul Latency",
        "code": "BACKHAUL_LATENCY",
        "category": "NETWORK",
        "severity": "warning",
        "metrics": {
            "LATENCY_PING": (80, 150),       # High latency
            "DL_THROUGHPUT_NR3500": (200, 400),  # Reduced throughput
            "UL_THROUGHPUT_NR3500": (20, 40),
        },
        "message": "Backhaul network latency exceeds SLA threshold",
        "duration_seconds": 120,
        "probability": 0.18,
    },
    {
        "name": "Power Fluctuation",
        "code": "POWER_FLUCTUATION",
        "category": "POWER",
        "severity": "critical",
        "metrics": {
            "POWER_CONSUMPTION": (2500, 3500),  # Abnormal power
            "TEMPERATURE": (70, 80),            # Elevated temp
            "CPU_USAGE": (40, 60),
        },
        "message": "Abnormal power consumption detected, possible hardware fault",
        "duration_seconds": 30,
        "probability": 0.10,
    },
    {
        "name": "High Interference",
        "code": "HIGH_INTERFERENCE",
        "category": "RF",
        "severity": "warning",
        "metrics": {
            "SINR_NR3500": (-2, 5),           # Very low SINR (interference)
            "SINR_NR700": (0, 6),
            "TX_IMBALANCE": (3, 6),           # High TX imbalance
        },
        "message": "High RF interference detected on NR bands",
        "duration_seconds": 60,
        "probability": 0.15,
    },
]


@dataclass
class MetricData:
    stationId: int
    metricType: str
    value: float
    unit: str
    timestamp: str


@dataclass
class Problem:
    id: str
    timestamp: str
    station_id: str
    category: str
    severity: str
    code: str
    message: str
    metrics: Dict[str, Any]
    raw_logs: str


class AnomalySimulator:
    """Simulates anomalies and sends them to monitoring + AI diagnostic services."""

    def __init__(self, monitoring_url: str, diagnostic_url: str, station_id: int, station_name: str, internal_secret: str = ""):
        self.monitoring_url = monitoring_url
        self.diagnostic_url = diagnostic_url
        self.station_id = station_id
        self.station_name = station_name
        self.internal_secret = internal_secret
        self.active_anomaly: Optional[Dict] = None
        self.anomaly_end_time: float = 0
        self.problem_counter = 0
        self.session = requests.Session()

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
        # Normal ranges
        normal_ranges = {
            "CPU_USAGE": (15, 45),
            "MEMORY_USAGE": (40, 65),
            "TEMPERATURE": (35, 55),
            "POWER_CONSUMPTION": (1400, 1800),
            "SIGNAL_STRENGTH": (-75, -55),
            "DL_THROUGHPUT_NR3500": (800, 1400),
            "UL_THROUGHPUT_NR3500": (60, 100),
            "RSRP_NR3500": (-85, -70),
            "SINR_NR3500": (12, 25),
            "DL_THROUGHPUT_NR700": (50, 90),
            "UL_THROUGHPUT_NR700": (15, 35),
            "RSRP_NR700": (-90, -75),
            "SINR_NR700": (8, 18),
            "LATENCY_PING": (5, 20),
            "TX_IMBALANCE": (0.5, 2.0),
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
        }
        return units.get(metric_type, "")

    def select_anomaly(self) -> Optional[Dict]:
        """Randomly select an anomaly based on probability."""
        for scenario in ANOMALY_SCENARIOS:
            if random.random() < scenario["probability"]:
                return scenario
        return None

    def send_metrics(self, anomaly: Optional[Dict] = None) -> bool:
        """Send a batch of metrics to the monitoring service."""
        metric_types = [
            "CPU_USAGE", "MEMORY_USAGE", "TEMPERATURE", "POWER_CONSUMPTION",
            "SIGNAL_STRENGTH", "DL_THROUGHPUT_NR3500", "UL_THROUGHPUT_NR3500",
            "RSRP_NR3500", "SINR_NR3500", "DL_THROUGHPUT_NR700", "UL_THROUGHPUT_NR700",
            "RSRP_NR700", "SINR_NR700", "LATENCY_PING", "TX_IMBALANCE"
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
                logger.debug(f"Sent {len(metrics)} metrics")
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
        timestamp = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")

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
        logger.info(f"Starting Anomaly Simulator")
        logger.info(f"  Monitoring Service: {self.monitoring_url}")
        logger.info(f"  AI Diagnostic: {self.diagnostic_url}")
        logger.info(f"  Station: {self.station_name} (ID: {self.station_id})")
        logger.info(f"  Metric interval: {interval_seconds}s")
        logger.info(f"  Anomaly check interval: {anomaly_check_interval}s")

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

    if not internal_secret:
        logger.warning("SECURITY_INTERNAL_SECRET not set - metrics sending may fail")

    simulator = AnomalySimulator(
        monitoring_url=monitoring_url,
        diagnostic_url=diagnostic_url,
        station_id=station_id,
        station_name=station_name,
        internal_secret=internal_secret
    )

    simulator.run(
        interval_seconds=interval,
        anomaly_check_interval=anomaly_interval
    )


if __name__ == "__main__":
    main()
