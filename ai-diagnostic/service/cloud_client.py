"""
Cloud client for the AI diagnostic service.

Posts AI-generated solutions back to the cloud API gateway, where they become
commands for the edge bridge to execute. Handles JWT auth (with token refresh)
and maps problem codes to device command types. Extracted from
diagnostic_service.py; `requests` stays an optional dependency imported lazily.
"""

import logging
import threading
from typing import Optional

from service.models import Problem, Solution  # noqa: F401  (string annotations)

logger = logging.getLogger(__name__)


class CloudClient:
    """
    Client for posting AI solutions back to the cloud.
    Solutions are converted to commands and queued for edge-bridge to execute.
    """

    def __init__(self, base_url: str = "http://api-gateway:8080",
                 username: Optional[str] = None, password: Optional[str] = None):
        if base_url and (not username or not password):
            raise ValueError("username and password are required when base_url is set - use CLOUD_USERNAME and CLOUD_PASSWORD env vars")
        self.base_url = base_url
        self.username = username
        self.password = password
        self.token: Optional[str] = None
        self.token_expires: float = 0
        self.enabled = bool(base_url)
        self._lock = threading.Lock()

        # Import requests here to make it optional
        try:
            import requests
            self.requests = requests
            self.http_available = True
        except ImportError:
            self.http_available = False
            logger.warning("requests library not available - cloud integration disabled")

    def _ensure_token(self) -> bool:
        """Ensure we have a valid JWT token."""
        if not self.http_available or not self.enabled:
            return False

        import time
        if self.token and time.time() < self.token_expires - 60:
            return True

        try:
            resp = self.requests.post(
                f"{self.base_url}/api/v1/auth/login",
                json={"username": self.username, "password": self.password},
                timeout=10
            )
            if resp.status_code == 200:
                data = resp.json()
                self.token = data.get("token")
                expires_in = data.get("expiresIn", 3600)
                self.token_expires = time.time() + expires_in
                logger.info("Cloud client authenticated successfully")
                return True
            else:
                logger.warning(f"Cloud auth failed: {resp.status_code}")
                return False
        except Exception as e:
            logger.warning(f"Cloud auth error: {e}")
            return False

    def post_solution(self, problem: 'Problem', solution: 'Solution',
                      diagnostic_session_id: str = "") -> bool:
        """
        Post a solution to the cloud to create commands for edge-bridge.

        Args:
            problem: The original problem
            solution: The AI-generated solution
            diagnostic_session_id: Optional session ID for tracking

        Returns:
            True if solution was posted successfully
        """
        if not self.http_available or not self.enabled:
            return False

        with self._lock:
            if not self._ensure_token():
                return False

            try:
                # Extract station ID (numeric) from problem
                station_id = self._extract_station_id(problem.station_id)
                if not station_id:
                    logger.warning(f"Could not extract station ID from: {problem.station_id}")
                    return False

                # Map solution to command type
                command_type = self._map_to_command_type(problem.code, solution)

                # Create AI command request
                payload = {
                    "diagnosticSessionId": diagnostic_session_id or f"ai-{problem.id}",
                    "problemCode": problem.code,
                    "commandType": command_type,
                    "params": {
                        "action": solution.action,
                        "commands": ";".join(solution.commands) if solution.commands else "",
                        "expectedOutcome": solution.expected_outcome
                    },
                    "confidence": solution.confidence,
                    "riskLevel": solution.risk_level
                }

                resp = self.requests.post(
                    f"{self.base_url}/api/v1/stations/{station_id}/commands/ai",
                    json=payload,
                    headers={"Authorization": f"Bearer {self.token}"},
                    timeout=10
                )

                if resp.status_code in (200, 201):
                    data = resp.json()
                    cmd_id = data.get("id", "unknown")
                    logger.info(f"Posted solution to cloud: command {cmd_id} for station {station_id}")
                    return True
                else:
                    logger.warning(f"Failed to post solution: {resp.status_code} - {resp.text}")
                    return False

            except Exception as e:
                logger.error(f"Error posting solution to cloud: {e}")
                return False

    def _extract_station_id(self, station_id_str: str) -> Optional[int]:
        """Extract numeric station ID from string like 'MIPS-BS-001' or '27'."""
        import re
        # Try direct numeric
        if station_id_str.isdigit():
            return int(station_id_str)
        # Try extracting number from end (e.g., MIPS-BS-001 -> 1)
        match = re.search(r'(\d+)$', station_id_str)
        if match:
            return int(match.group(1))
        # Default to station 1 for testing
        return 1

    def _map_to_command_type(self, problem_code: str, _solution: 'Solution') -> str:
        """Map problem code to a command type for the device."""
        # Map common problem codes to device command types
        command_map = {
            "CPU_OVERHEAT": "THERMAL_CONTROL",
            "CPU_HIGH_USAGE": "PROCESS_CONTROL",
            "MEMORY_PRESSURE": "MEMORY_CLEANUP",
            "MEMORY_LEAK": "SERVICE_RESTART",
            "FAN_FAILURE": "HARDWARE_CHECK",
            "SIGNAL_DEGRADATION": "RF_CALIBRATION",
            "SIGNAL_INTERFERENCE": "FREQUENCY_ADJUST",
            "BACKHAUL_LATENCY": "NETWORK_OPTIMIZE",
            "PACKET_LOSS": "NETWORK_DIAGNOSE",
            "PROCESS_CRASH": "SERVICE_RESTART",
            "CONFIG_ERROR": "CONFIG_RELOAD",
            "VOLTAGE_FLUCTUATION": "POWER_CHECK",
            "HIGH_POWER_CONSUMPTION": "POWER_OPTIMIZE",
            "AUTH_FAILURE": "SECURITY_AUDIT",
            "CERTIFICATE_EXPIRY": "CERT_RENEWAL",
            # Extended problem codes
            "HIGH_BLOCK_ERROR_RATE": "RF_CALIBRATION",
            "LOW_BATTERY": "POWER_CHECK",
            "HIGH_LATENCY": "NETWORK_OPTIMIZE",
            "LOW_THROUGHPUT": "NETWORK_OPTIMIZE",
            "HANDOVER_FAILURE": "RF_CALIBRATION",
            "HIGH_INTERFERENCE": "FREQUENCY_ADJUST",
        }
        return command_map.get(problem_code, "GENERIC_FIX")
