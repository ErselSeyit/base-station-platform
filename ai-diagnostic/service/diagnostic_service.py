#!/usr/bin/env python3
"""
AI Diagnostic Service

Universal diagnostic service that:
- Accepts problems from ANY communication protocol
- Uses AI to analyze and generate solutions
- Sends solutions back to the device

Supported Protocols:
- TCP/IP Socket (Ethernet)
- Serial/UART (RS-232, RS-485)
- USB Serial
- MQTT (IoT standard)
- HTTP/REST API
- WebSocket (real-time)
- gRPC (high performance)

Supported AI Backends:
- Local Ollama (LLaMA, Mistral)
- Rule-based expert system (offline)
"""

import json
import logging
import threading
import socket
import os
import hmac
import hashlib
import time
from functools import wraps
from abc import ABC, abstractmethod


# Optional serial support
try:
    import serial
    import serial.tools.list_ports
    SERIAL_AVAILABLE = True
except ImportError:
    SERIAL_AVAILABLE = False
from dataclasses import dataclass, asdict, field
from typing import Optional, Dict, Any, List, Callable
from datetime import datetime
from enum import Enum
import queue

# Optional imports for various protocols
try:
    import paho.mqtt.client as mqtt
    MQTT_AVAILABLE = True
except ImportError:
    MQTT_AVAILABLE = False

# Optional services, availability flags and error/route constants live in
# service/optional_services.py (shared to avoid a circular import with the
# extracted HTTP adapter).
from service.optional_services import *  # noqa: F401,F403


from service.logging_config import configure_logging
from service.metrics import register_metrics_endpoint
configure_logging()
logger = logging.getLogger(__name__)



# ============================================================================
# Data Models
# ============================================================================
# Extracted to service/models.py; re-exported here so existing references
# (and importers of this module) keep working unchanged.
from service.models import Problem, Solution, LearnedPattern  # noqa: E402


# ============================================================================
# Cloud Client - Posts solutions back to cloud for edge-bridge execution
# ============================================================================

# Extracted to service/cloud_client.py; re-exported for existing importers.
from service.cloud_client import CloudClient  # noqa: E402


# ============================================================================
# Learning Engine
# ============================================================================

# Extracted to service/learning_engine.py; re-exported for existing importers.
from service.learning_engine import LearningEngine  # noqa: E402


# ============================================================================
# AI Backends
# ============================================================================

# Extracted to service/backends.py; re-exported for existing importers.
from service.backends import AIBackend, RuleBasedBackend, OllamaBackend  # noqa: E402


# ============================================================================
# Protocol Adapters
# ============================================================================

# Extracted to service/transport_adapters.py; re-exported for existing importers.
from service.transport_adapters import (  # noqa: E402
    ProtocolAdapter, TCPAdapter, SerialAdapter, MQTTAdapter,
)


# Extracted to service/http_adapter.py; re-exported for existing importers.
from service.http_adapter import HTTPAdapter  # noqa: E402


# ============================================================================
# Main Diagnostic Service
# ============================================================================

class DiagnosticService:
    """
    Main AI Diagnostic Service

    Manages multiple protocol adapters, AI backends, and the learning engine.
    Optionally posts solutions to cloud for edge-bridge execution.
    """

    def __init__(self, cloud_url: str = "", cloud_user: Optional[str] = None,
                 cloud_password: Optional[str] = None):
        self.adapters: List[ProtocolAdapter] = []
        self.backend: AIBackend = RuleBasedBackend()
        self.problem_log: List[Problem] = []
        self.solution_log: List[Solution] = []
        self.learning_engine = LearningEngine()
        self.cloud_client: Optional[CloudClient] = None

        # Initialize cloud client if URL provided
        if cloud_url:
            if not cloud_user or not cloud_password:
                raise ValueError("cloud_user and cloud_password are required when cloud_url is set - use CLOUD_USER and CLOUD_PASSWORD env vars")
            self.cloud_client = CloudClient(cloud_url, cloud_user, cloud_password)
            logger.info(f"Cloud integration enabled: {cloud_url}")

    def set_backend(self, backend: AIBackend):
        """Set the AI backend for diagnosis"""
        self.backend = backend
        logger.info(f"AI backend set to: {type(backend).__name__}")

    def add_adapter(self, adapter: ProtocolAdapter):
        """Add a protocol adapter"""
        self.adapters.append(adapter)
        logger.info(f"Added adapter: {type(adapter).__name__}")

    def record_feedback(self, problem_code: str, category: str,
                        was_effective: bool, action: str) -> LearnedPattern:
        """Record operator feedback and update learning patterns."""
        pattern = self.learning_engine.update_pattern(
            problem_code, category, was_effective, action
        )
        logger.info(f"Feedback recorded for {problem_code}: "
                   f"effective={was_effective}, success_rate={pattern.success_rate():.1f}%")
        return pattern

    def _handle_problem(self, problem: Problem) -> Solution:
        """Central problem handler called by all adapters"""
        logger.info(f"Received problem from {problem.source_protocol}: "
                   f"[{problem.severity}] {problem.code}")

        self.problem_log.append(problem)

        # Diagnose using AI backend
        solution = self.backend.diagnose(problem)

        # Adjust confidence based on learned patterns
        adjusted_confidence = self.learning_engine.get_adjusted_confidence(
            problem.code, solution.confidence
        )
        if adjusted_confidence != solution.confidence:
            logger.info(f"Adjusted confidence for {problem.code}: "
                       f"{solution.confidence:.0%} -> {adjusted_confidence:.0%}")
            solution.confidence = adjusted_confidence

        self.solution_log.append(solution)

        logger.info(f"Generated solution: {solution.action} "
                   f"(confidence: {solution.confidence:.0%})")

        # Post solution to cloud for edge-bridge execution
        if self.cloud_client:
            self._post_solution_to_cloud(problem, solution)

        return solution

    def _post_solution_to_cloud(self, problem: Problem, solution: Solution):
        """Post solution to cloud to create commands for edge-bridge."""
        if self.cloud_client is None:
            return

        try:
            # Only auto-apply if confidence is high enough and risk is low/medium
            min_confidence = 0.90
            allowed_risk = ["low", "medium"]

            if solution.confidence >= min_confidence and solution.risk_level in allowed_risk:
                success = self.cloud_client.post_solution(problem, solution)
                if success:
                    logger.info(f"Solution auto-applied to cloud for {problem.code}")
                else:
                    logger.warning(f"Failed to post solution to cloud for {problem.code}")
            else:
                logger.info(f"Solution for {problem.code} requires manual approval "
                           f"(confidence={solution.confidence:.0%}, risk={solution.risk_level})")
        except Exception as e:
            logger.error(f"Error posting solution to cloud: {e}")

    def start(self):
        """Start all adapters"""
        logger.info("Starting AI Diagnostic Service")
        logger.info(f"Backend: {type(self.backend).__name__}")
        logger.info(f"Adapters: {len(self.adapters)}")
        logger.info("Learning Engine: enabled")

        for adapter in self.adapters:
            adapter.on_problem = self._handle_problem
            # Share log and learning references with HTTPAdapter
            if isinstance(adapter, HTTPAdapter):
                adapter.problem_log = self.problem_log
                adapter.solution_log = self.solution_log
                adapter.learning_engine = self.learning_engine
                adapter.diagnostic_service = self
            adapter.start()

    def stop(self):
        """Stop all adapters"""
        for adapter in self.adapters:
            adapter.stop()
        logger.info("Diagnostic service stopped")


def _create_arg_parser():
    """Create and configure the argument parser."""
    import argparse
    parser = argparse.ArgumentParser(description="AI Diagnostic Service")
    parser.add_argument("--tcp-port", type=int, default=9090, help="TCP port")
    parser.add_argument("--http-port", type=int, default=9091, help="HTTP port")
    parser.add_argument("--serial", help="Serial port (e.g., /dev/ttyUSB0)")
    parser.add_argument("--mqtt-broker", help="MQTT broker address")
    parser.add_argument("--backend", choices=["rules", "ollama"],
                       default="rules", help="AI backend")
    parser.add_argument("--ollama-model", default="llama3.2", help="Ollama model")
    parser.add_argument("--cloud-url", default=os.environ.get("CLOUD_URL", ""),
                       help="Cloud API gateway URL for posting solutions")
    parser.add_argument("--cloud-user", default=os.environ.get("CLOUD_USER"),
                       help="Cloud auth username (required if cloud-url set)")
    parser.add_argument("--cloud-password", default=os.environ.get("CLOUD_PASSWORD"),
                       help="Cloud auth password (required if cloud-url set)")
    return parser


def _setup_adapters(service: 'DiagnosticService', args) -> None:
    """Configure and add protocol adapters to the service."""
    service.add_adapter(TCPAdapter(None, port=args.tcp_port))

    if FLASK_AVAILABLE:
        service.add_adapter(HTTPAdapter(None, port=args.http_port))

    if args.serial:
        service.add_adapter(SerialAdapter(None, port=args.serial))

    if args.mqtt_broker and MQTT_AVAILABLE:
        service.add_adapter(MQTTAdapter(None, broker=args.mqtt_broker))


def _start_son_scheduler(cloud_user: Optional[str], cloud_password: Optional[str]) -> None:
    """Start SON scheduler if available."""
    if not SON_SCHEDULER_AVAILABLE:
        return

    try:
        monitoring_url = os.environ.get("MONITORING_SERVICE_URL", "http://monitoring-service:8082")
        son_interval = int(os.environ.get("SON_ANALYSIS_INTERVAL", "300"))

        scheduler = get_son_scheduler(
            monitoring_url=monitoring_url,
            auth_user=cloud_user or "admin",
            auth_password=cloud_password or "",
            interval_seconds=son_interval
        )
        scheduler.start()
        logger.info(f"SON scheduler started (interval: {son_interval}s)")
    except Exception as e:
        logger.error(f"Failed to start SON scheduler: {e}")


def _log_available_endpoints(http_port: int):
    """Log all available HTTP endpoints based on loaded services."""
    logger.info(f"  HTTP: http://localhost:{http_port}")
    logger.info("  Endpoints:")
    logger.info("    POST /diagnose           - AI diagnosis")
    logger.info("    GET  /health             - Health check")
    logger.info("    GET  /reports/bi         - BI report PDF")
    logger.info("    GET  /reports/diagnostics- Diagnostic log")

    # Optional service endpoints
    endpoint_map = [
        (VISION_AVAILABLE, ["POST /vision/analyze-led - LED panel analysis"]),
        (ALARM_CORRELATION_AVAILABLE, ["POST /alarms/correlate   - Alarm correlation"]),
        (PREDICTIVE_MAINTENANCE_AVAILABLE, [
            "GET  /maintenance/{id}/health  - Health report",
            "GET  /maintenance/{id}/battery - Battery analysis",
            "GET  /maintenance/{id}/fiber   - Fiber transport analysis",
            "POST /maintenance/analyze      - Analyze metrics"
        ]),
        (CONFIG_DRIFT_AVAILABLE, [
            "POST /config/drift       - Detect drift",
            "POST /config/baseline    - Set baseline"
        ]),
        (RCA_AVAILABLE, [
            "POST /rca/analyze        - Root cause analysis",
            "GET  /rca/stats          - RCA statistics"
        ]),
        (SELF_HEALING_AVAILABLE, [
            "POST /healing/actions    - Submit healing action",
            "POST /healing/from-son   - Create from SON",
            "POST /healing/from-rca   - Create from RCA",
            "GET  /healing/pending    - Pending actions",
            "GET  /healing/history    - Execution history",
            "GET  /healing/stats      - Healing statistics"
        ]),
    ]

    for available, endpoints in endpoint_map:
        if available:
            for endpoint in endpoints:
                logger.info(f"    {endpoint}")


def main():
    parser = _create_arg_parser()
    args = parser.parse_args()

    # Validate cloud credentials if cloud URL is provided
    if args.cloud_url:
        if not args.cloud_user:
            parser.error("--cloud-user is required when --cloud-url is set. Set CLOUD_USER environment variable.")
        if not args.cloud_password:
            parser.error("--cloud-password is required when --cloud-url is set. Set CLOUD_PASSWORD environment variable.")

    service = DiagnosticService(
        cloud_url=args.cloud_url,
        cloud_user=args.cloud_user,
        cloud_password=args.cloud_password
    )

    # Set AI backend
    backend = OllamaBackend(model=args.ollama_model) if args.backend == "ollama" else RuleBasedBackend()
    service.set_backend(backend)

    # Add protocol adapters and start service
    _setup_adapters(service, args)
    service.start()

    # Start SON scheduler if available
    _start_son_scheduler(args.cloud_user, args.cloud_password)

    logger.info("\n" + "="*60)
    logger.info("AI Diagnostic Service is running")
    logger.info("="*60)
    logger.info(f"  TCP:  localhost:{args.tcp_port}")
    if FLASK_AVAILABLE:
        _log_available_endpoints(args.http_port)
    if args.serial:
        logger.info(f"  Serial: {args.serial}")
    if args.mqtt_broker:
        logger.info(f"  MQTT: {args.mqtt_broker}")
    logger.info("="*60 + "\n")

    try:
        while True:
            import time
            time.sleep(1)
    except KeyboardInterrupt:
        service.stop()


if __name__ == "__main__":
    main()
