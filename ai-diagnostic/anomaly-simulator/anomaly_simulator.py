#!/usr/bin/env python3
"""
Fault Orchestrator (formerly Anomaly Simulator)

Periodically injects fault scenarios into the device-simulator via its control API.
The device-simulator then generates anomalous metrics that flow through the binary
protocol → edge-bridge → monitoring-service pipeline, triggering real alerts and
AI diagnostics.

Architecture:
  fault-orchestrator → POST /api/inject → device-simulator (port 8098)
  device-simulator → binary protocol → edge-bridge → HTTP → monitoring-service
  fault-orchestrator → POST /diagnose → ai-diagnostic (triggers AI session)
"""

import random
import time
import logging
import hmac
import hashlib
import os
import threading
import requests
from datetime import datetime, timezone
from typing import Dict, Optional

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('fault-orchestrator')

# Configuration defaults
DEVICE_SIMULATOR_URL = "http://device-simulator:8098"
AI_DIAGNOSTIC_URL = "http://ai-diagnostic:9091"
STATION_ID = 1
STATION_NAME = "BS-001"

# Anomaly scenarios with their problem codes and metric patterns.
# These map to problem codes in DiagnosticSessionService.metricTypeToProblemCode()
# and to FAULT_SCENARIOS in device-simulator's mips_device.py.
ANOMALY_SCENARIOS = [
    {
        "name": "CPU Overheat",
        "code": "CPU_OVERHEAT",
        "category": "THERMAL",
        "severity": "critical",
        "metrics": {
            "CPU_USAGE": (92, 99),
            "TEMPERATURE": (82, 95),
        },
        "message": "CPU temperature exceeded safe operating threshold",
        "duration_seconds": 120,
    },
    {
        "name": "Memory Pressure",
        "code": "MEMORY_PRESSURE",
        "category": "RESOURCE",
        "severity": "critical",
        "metrics": {
            "MEMORY_USAGE": (96, 99),
            "CPU_USAGE": (70, 85),
        },
        "message": "System memory critically low, risk of OOM",
        "duration_seconds": 120,
    },
    {
        "name": "Signal Degradation",
        "code": "SIGNAL_DEGRADATION",
        "category": "RF",
        "severity": "warning",
        "metrics": {
            "SIGNAL_STRENGTH": (-105, -95),
            "RSRP_NR3500": (-110, -100),
            "SINR_NR3500": (2, 8),
        },
        "message": "RF signal quality degraded below acceptable threshold",
        "duration_seconds": 120,
    },
    {
        "name": "High Latency",
        "code": "HIGH_LATENCY",
        "category": "NETWORK",
        "severity": "critical",
        "metrics": {
            "LATENCY_PING": (110, 200),
            "DATA_THROUGHPUT": (30, 45),
        },
        "message": "Network latency exceeded critical threshold",
        "duration_seconds": 120,
    },
    {
        "name": "High Power Consumption",
        "code": "HIGH_POWER_CONSUMPTION",
        "category": "POWER",
        "severity": "critical",
        "metrics": {
            "POWER_CONSUMPTION": (3100, 3500),
            "TEMPERATURE": (72, 82),
        },
        "message": "Power consumption exceeded critical threshold",
        "duration_seconds": 120,
    },
    {
        "name": "High Interference",
        "code": "HIGH_INTERFERENCE",
        "category": "RF",
        "severity": "warning",
        "metrics": {
            "INTERFERENCE_LEVEL": (-68, -60),
            "SINR_NR3500": (-2, 5),
            "SINR_NR700": (0, 6),
        },
        "message": "High RF interference detected on NR bands",
        "duration_seconds": 120,
    },
    {
        "name": "High Block Error Rate",
        "code": "HIGH_BLOCK_ERROR_RATE",
        "category": "RF",
        "severity": "critical",
        "metrics": {
            "INITIAL_BLER": (32, 50),
            "SINR_NR3500": (3, 8),
        },
        "message": "Block error rate exceeded critical threshold",
        "duration_seconds": 120,
    },
    {
        "name": "Low Battery",
        "code": "LOW_BATTERY",
        "category": "POWER",
        "severity": "critical",
        "metrics": {
            "BATTERY_SOC": (5, 9),
        },
        "message": "Battery state of charge critically low",
        "duration_seconds": 120,
    },
    {
        "name": "Low Throughput",
        "code": "LOW_THROUGHPUT",
        "category": "NETWORK",
        "severity": "critical",
        "metrics": {
            "DATA_THROUGHPUT": (15, 19),
            "LATENCY_PING": (40, 60),
        },
        "message": "Data throughput critically low",
        "duration_seconds": 120,
    },
    {
        "name": "Handover Failure",
        "code": "HANDOVER_FAILURE",
        "category": "NETWORK",
        "severity": "critical",
        "metrics": {
            "HANDOVER_SUCCESS_RATE": (85, 89),
            "SIGNAL_STRENGTH": (-90, -80),
        },
        "message": "Handover success rate below critical threshold",
        "duration_seconds": 120,
    },
]


class DeviceTarget:
    """Represents a device-simulator target for fault injection."""

    def __init__(self, url: str, station_id: int, station_name: str):
        self.url = url
        self.station_id = station_id
        self.station_name = station_name
        self.active_anomaly: Optional[Dict] = None
        self.anomaly_end_time: float = 0


class FaultOrchestrator:
    """Orchestrates fault injection into device-simulator(s) and triggers AI diagnostics.

    Supports multiple device-simulator targets. When multiple targets are configured,
    faults are injected in round-robin across all devices.

    Instead of generating metrics directly (old approach), this orchestrator:
    1. Calls device-simulator's control API to inject faults
    2. Device-simulator generates anomalous metrics via binary protocol
    3. Edge-bridge collects and uploads to monitoring-service
    4. Triggers AI diagnostic session for the injected fault
    """

    def __init__(self, device_simulator_url: str, diagnostic_url: str,
                 station_id: int, station_name: str, internal_secret: str = "",
                 extra_targets: Optional[list] = None):
        self.diagnostic_url = diagnostic_url
        self.internal_secret = internal_secret
        self.problem_counter = 0
        self.session = requests.Session()
        self.anomaly_index = 0

        # Build target list: primary + any extras
        self.targets = [DeviceTarget(device_simulator_url, station_id, station_name)]
        for t in (extra_targets or []):
            self.targets.append(DeviceTarget(t["url"], t["station_id"], t["station_name"]))
        self.target_index = 0

        # Backwards-compatible single-target properties
        self.device_simulator_url = device_simulator_url
        self.station_id = station_id
        self.station_name = station_name
        self.active_anomaly: Optional[Dict] = None
        self.anomaly_end_time: float = 0

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
            "X-User-Name": "fault-orchestrator",
            "X-User-Role": "SERVICE",
            "Content-Type": "application/json"
        }
        if self.internal_secret:
            timestamp = int(time.time() * 1000)
            payload = f"fault-orchestrator:SERVICE:{timestamp}"
            signature = self._compute_hmac(payload)
            headers["X-Internal-Auth"] = f"{signature}.{payload}"
        return headers

    def select_anomaly(self) -> Dict:
        """Select the next anomaly in rotation (cycles through all types)."""
        scenario = ANOMALY_SCENARIOS[self.anomaly_index]
        self.anomaly_index = (self.anomaly_index + 1) % len(ANOMALY_SCENARIOS)
        return scenario

    def _select_target(self) -> DeviceTarget:
        """Select the next target in round-robin rotation."""
        target = self.targets[self.target_index]
        self.target_index = (self.target_index + 1) % len(self.targets)
        return target

    def inject_fault(self, fault_code: str, target_url: Optional[str] = None) -> bool:
        """Inject a fault into device-simulator via its control API."""
        url = target_url or self.device_simulator_url
        try:
            response = self.session.post(
                f"{url}/api/inject",
                json={"fault": fault_code},
                timeout=5
            )
            if response.ok:
                result = response.json()
                logger.info("Fault injected into %s: %s -> %s",
                            url, fault_code, result.get("status"))
                return result.get("status") == "injected"
            logger.warning("Failed to inject fault: %d %s",
                           response.status_code, response.text[:200])
            return False
        except requests.RequestException as e:
            logger.error("Error injecting fault into %s: %s", url, e)
            return False

    def clear_fault(self, fault_code: str, target_url: Optional[str] = None) -> bool:
        """Clear a fault from device-simulator via its control API."""
        url = target_url or self.device_simulator_url
        try:
            response = self.session.post(
                f"{url}/api/clear",
                json={"fault": fault_code},
                timeout=5
            )
            if response.ok:
                result = response.json()
                logger.info("Fault cleared from %s: %s -> %s",
                            url, fault_code, result.get("status"))
                return True
            return False
        except requests.RequestException as e:
            logger.error("Error clearing fault from %s: %s", url, e)
            return False

    def clear_all_faults(self) -> bool:
        """Clear all faults from all device-simulators."""
        success = True
        for target in self.targets:
            try:
                response = self.session.post(
                    f"{target.url}/api/clear-all",
                    timeout=5
                )
                if response.ok:
                    logger.info("All faults cleared on %s: %s",
                                target.station_name, response.json())
                else:
                    success = False
            except requests.RequestException as e:
                logger.error("Error clearing faults on %s: %s", target.station_name, e)
                success = False
        return success

    def suppress_anomaly(self, problem_code: str) -> dict:
        """Suppress an active anomaly — clears the fault from device-simulator.

        Searches all targets for the matching anomaly.
        Called by the self-healing service via the control API.
        """
        for target in self.targets:
            if target.active_anomaly and target.active_anomaly["code"] == problem_code:
                name = target.active_anomaly["name"]
                self.clear_fault(problem_code, target.url)
                target.active_anomaly = None
                target.anomaly_end_time = 0
                logger.info("Anomaly '%s' suppressed on %s by self-healing",
                            name, target.station_name)
                # Keep backwards-compat single-target state in sync
                if target is self.targets[0]:
                    self.active_anomaly = None
                    self.anomaly_end_time = 0
                return {"status": "suppressed", "anomaly": name,
                        "station": target.station_name}

        return {"status": "no_matching_anomaly", "requested_code": problem_code}

    def send_problem_to_ai(self, anomaly: Dict,
                           target: Optional[DeviceTarget] = None) -> Optional[Dict]:
        """Send the problem directly to AI diagnostic service."""
        t = target or self.targets[0]
        self.problem_counter += 1
        problem_id = f"anomaly-{self.problem_counter:04d}-{int(time.time())}"
        timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

        metrics_snapshot = {}
        for metric_type, (lo, hi) in anomaly.get("metrics", {}).items():
            metrics_snapshot[metric_type] = random.uniform(lo, hi)

        problem = {
            "id": problem_id,
            "timestamp": timestamp,
            "station_id": str(t.station_id),
            "category": anomaly["category"],
            "severity": anomaly["severity"],
            "code": anomaly["code"],
            "message": anomaly["message"],
            "metrics": metrics_snapshot,
            "raw_logs": f"FAULT INJECTED: {anomaly['name']} at station {t.station_name}"
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
                logger.info("AI Solution for %s@%s: action=%s confidence=%.0f%% risk=%s",
                            anomaly['code'], t.station_name,
                            solution.get('action', 'N/A'),
                            solution.get('confidence', 0) * 100,
                            solution.get('risk_level', 'N/A'))

                healing = solution.get('healing')
                if healing:
                    if healing.get('error'):
                        logger.warning("  Auto-healing error: %s", healing['error'])
                    else:
                        logger.info("  Auto-Healing: %s (action_id=%s, auto=%s)",
                                    healing.get('status', 'N/A'),
                                    healing.get('action_id', 'N/A'),
                                    healing.get('auto_execute', False))
                return solution
            logger.warning("AI diagnostic failed: %d %s",
                           response.status_code, response.text[:200])
            return None
        except requests.RequestException as e:
            logger.error("Error calling AI diagnostic: %s", e)
            return None

    def _expire_anomalies(self, current_time: float):
        """Clear anomalies whose duration has expired on all targets."""
        for target in self.targets:
            if not target.active_anomaly or current_time <= target.anomaly_end_time:
                continue
            logger.info("Anomaly '%s' expired on %s — clearing",
                        target.active_anomaly['name'], target.station_name)
            self.clear_fault(target.active_anomaly['code'], target.url)
            target.active_anomaly = None
            if target is self.targets[0]:
                self.active_anomaly = None

    def _find_idle_target(self) -> Optional[DeviceTarget]:
        """Find the next target without an active anomaly via round-robin."""
        for _ in range(len(self.targets)):
            target = self._select_target()
            if not target.active_anomaly:
                return target
        return None

    def _inject_new_anomaly(self, current_time: float):
        """Pick an idle target and inject the next anomaly scenario."""
        target = self._find_idle_target()
        if target is None:
            return

        new_anomaly = self.select_anomaly()
        if not self.inject_fault(new_anomaly["code"], target.url):
            logger.warning("Failed to inject fault %s on %s",
                           new_anomaly['code'], target.station_name)
            return

        target.active_anomaly = new_anomaly
        target.anomaly_end_time = current_time + new_anomaly["duration_seconds"]
        if target is self.targets[0]:
            self.active_anomaly = new_anomaly
            self.anomaly_end_time = target.anomaly_end_time

        logger.warning(">>> FAULT INJECTED on %s: %s (%s)",
                       target.station_name,
                       new_anomaly['name'], new_anomaly['code'])
        logger.warning("    Severity: %s, Duration: %ds",
                       new_anomaly['severity'], new_anomaly['duration_seconds'])
        # Anomalous metrics flow naturally through the telemetry pipeline:
        #   device-simulator → binary protocol → edge-bridge → monitoring-service
        # Monitoring-service detects threshold breaches and triggers AI diagnostics
        # automatically. No need to call send_problem_to_ai() directly.

    def run(self, anomaly_check_interval: float = 30.0):
        """Main loop — periodically inject faults and trigger AI diagnostics.

        With multiple targets, faults are injected in round-robin across devices.
        Each target maintains its own active fault state independently.
        """
        logger.info("Starting Fault Orchestrator")
        logger.info("  Targets: %d device-simulator(s)", len(self.targets))
        for t in self.targets:
            logger.info("    - %s (station %d) at %s",
                        t.station_name, t.station_id, t.url)
        logger.info("  AI Diagnostic: %s", self.diagnostic_url)
        logger.info("  Anomaly check interval: %.0fs", anomaly_check_interval)

        last_anomaly_check = 0

        while True:
            try:
                current_time = time.time()
                self._expire_anomalies(current_time)

                if (current_time - last_anomaly_check) > anomaly_check_interval:
                    last_anomaly_check = current_time
                    self._inject_new_anomaly(current_time)

                time.sleep(anomaly_check_interval / 3)

            except KeyboardInterrupt:
                logger.info("Shutting down — clearing all faults")
                self.clear_all_faults()
                break
            except Exception as e:
                logger.error("Error in main loop: %s", e)
                time.sleep(5)


def _parse_extra_targets(env_value: str) -> list:
    """Parse EXTRA_TARGETS env var.

    Format: url1,station_id1,name1;url2,station_id2,name2
    Example: http://device-sim-2:8098,2,BS-002;http://device-sim-3:8098,3,BS-003
    """
    targets = []
    for entry in env_value.split(";"):
        entry = entry.strip()
        if not entry:
            continue
        parts = entry.split(",", 2)
        if len(parts) == 3:
            targets.append({
                "url": parts[0].strip(),
                "station_id": int(parts[1].strip()),
                "station_name": parts[2].strip(),
            })
        else:
            logger.warning("Ignoring malformed EXTRA_TARGETS entry: %s", entry)
    return targets


def main():
    device_simulator_url = os.environ.get("DEVICE_SIMULATOR_URL", DEVICE_SIMULATOR_URL)
    diagnostic_url = os.environ.get("AI_DIAGNOSTIC_URL", AI_DIAGNOSTIC_URL)
    station_id = int(os.environ.get("STATION_ID", STATION_ID))
    station_name = os.environ.get("STATION_NAME", STATION_NAME)
    anomaly_interval = float(os.environ.get("ANOMALY_CHECK_INTERVAL", "10"))
    internal_secret = os.environ.get("SECURITY_INTERNAL_SECRET", "")
    extra_targets = _parse_extra_targets(
        os.environ.get("EXTRA_TARGETS", "")
    )

    orchestrator = FaultOrchestrator(
        device_simulator_url=device_simulator_url,
        diagnostic_url=diagnostic_url,
        station_id=station_id,
        station_name=station_name,
        internal_secret=internal_secret,
        extra_targets=extra_targets,
    )

    # Start Flask control API in background thread for self-healing integration
    from flask import Flask, request as flask_request, jsonify

    control_app = Flask("fault-orchestrator")
    flask_log = logging.getLogger("werkzeug")
    flask_log.setLevel(logging.WARNING)

    @control_app.route("/api/suppress", methods=["POST"])
    def suppress():
        data = flask_request.get_json(silent=True) or {}
        code = data.get("problem_code", "")
        result = orchestrator.suppress_anomaly(code)
        return jsonify(result)

    @control_app.route("/health")
    def health():
        active_targets = [
            {"station": t.station_name, "code": t.active_anomaly["code"]}
            for t in orchestrator.targets if t.active_anomaly
        ]
        return jsonify({
            "status": "ok",
            "targets": len(orchestrator.targets),
            "active_faults": active_targets,
        })

    api_port = int(os.environ.get("CONTROL_API_PORT", "8099"))
    api_thread = threading.Thread(
        target=lambda: control_app.run(
            host="0.0.0.0", port=api_port, use_reloader=False,
        ),
        daemon=True,
    )
    api_thread.start()
    logger.info("Control API started on port %d", api_port)

    orchestrator.run(anomaly_check_interval=anomaly_interval)


if __name__ == "__main__":
    main()
