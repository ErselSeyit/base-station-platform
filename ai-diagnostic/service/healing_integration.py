"""
Healing Integration Module

Bridges AI diagnostic solutions with the self-healing service.
Converts solutions to healing actions for automatic execution.
"""

import os
import time
import logging
from typing import Optional, Dict, Callable, TYPE_CHECKING

if TYPE_CHECKING:
    from diagnostic_service import Problem, Solution

logger = logging.getLogger(__name__)

# Import self-healing components
try:
    from self_healing import (
        get_self_healing_service,
        HealingAction,
        ActionType,
        RiskLevel,
    )
    SELF_HEALING_AVAILABLE = True
except ImportError:
    SELF_HEALING_AVAILABLE = False
    logger.warning("Self-healing service not available")


# Problem code to action type mapping
PROBLEM_ACTION_MAP: Dict[str, 'ActionType'] = {}
if SELF_HEALING_AVAILABLE:
    PROBLEM_ACTION_MAP = {
        # Thermal / resource
        "CPU_OVERHEAT": ActionType.PARAMETER_CHANGE,
        "CPU_HIGH_USAGE": ActionType.PARAMETER_CHANGE,
        "MEMORY_PRESSURE": ActionType.PARAMETER_CHANGE,
        "HIGH_POWER_CONSUMPTION": ActionType.PARAMETER_CHANGE,
        "FAN_FAILURE": ActionType.PARAMETER_CHANGE,
        # RF / signal quality
        "SIGNAL_DEGRADATION": ActionType.PARAMETER_CHANGE,
        "HIGH_INTERFERENCE": ActionType.PARAMETER_CHANGE,
        "RSRP_WEAK": ActionType.PARAMETER_CHANGE,
        "SINR_DEGRADATION": ActionType.PARAMETER_CHANGE,
        "TX_IMBALANCE_HIGH": ActionType.PARAMETER_CHANGE,
        "HIGH_BLOCK_ERROR_RATE": ActionType.PARAMETER_CHANGE,
        "BLER_HIGH": ActionType.PARAMETER_CHANGE,
        # Throughput / latency
        "BACKHAUL_LATENCY": ActionType.PARAMETER_CHANGE,
        "HIGH_LATENCY": ActionType.PARAMETER_CHANGE,
        "LATENCY_HIGH": ActionType.PARAMETER_CHANGE,
        "LOW_THROUGHPUT": ActionType.PARAMETER_CHANGE,
        "DL_THROUGHPUT_LOW_NR3500": ActionType.PARAMETER_CHANGE,
        "UL_THROUGHPUT_LOW_NR3500": ActionType.PARAMETER_CHANGE,
        "DL_THROUGHPUT_LOW_NR700": ActionType.PARAMETER_CHANGE,
        # Mobility / handover
        "HANDOVER_FAILURE": ActionType.PARAMETER_CHANGE,
        # Power / hardware
        "POWER_FLUCTUATION": ActionType.PARAMETER_CHANGE,
        "LOW_BATTERY": ActionType.PARAMETER_CHANGE,
        # Process / config
        "PROCESS_CRASH": ActionType.PARAMETER_CHANGE,
        "CONFIG_MISMATCH": ActionType.PARAMETER_CHANGE,
        "CERT_EXPIRING": ActionType.PARAMETER_CHANGE,
        # Security (suppress — do not auto-heal)
        "AUTH_FAILURE": ActionType.ALARM_SUPPRESS,
    }

# Risk level string to enum mapping
RISK_LEVEL_MAP: Dict[str, 'RiskLevel'] = {}
if SELF_HEALING_AVAILABLE:
    RISK_LEVEL_MAP = {
        "low": RiskLevel.LOW,
        "medium": RiskLevel.MEDIUM,
        "high": RiskLevel.HIGH,
        "critical": RiskLevel.CRITICAL,
    }


def create_healing_action(
    problem: 'Problem',
    solution: 'Solution'
) -> Optional['HealingAction']:
    """
    Create a HealingAction from an AI-generated solution.

    Args:
        problem: The diagnosed problem
        solution: The AI-generated solution

    Returns:
        HealingAction if self-healing is available, None otherwise
    """
    if not SELF_HEALING_AVAILABLE:
        return None

    action_type = PROBLEM_ACTION_MAP.get(
        problem.code,
        ActionType.PARAMETER_CHANGE
    )
    risk_level = RISK_LEVEL_MAP.get(
        solution.risk_level.lower(),
        RiskLevel.MEDIUM
    )

    # Auto-execute only for low/medium risk with high confidence
    auto_execute = (
        risk_level in [RiskLevel.LOW, RiskLevel.MEDIUM] and
        solution.confidence >= 0.8
    )

    # Extract metric verification info from problem snapshot
    # problem.metrics format: {"power_consumption": 775.5, "threshold": 700.0}
    metric_type = None
    threshold = problem.metrics.get("threshold")
    for key, val in problem.metrics.items():
        if key != "threshold" and isinstance(val, (int, float)):
            metric_type = key.upper()
            break

    higher_is_worse = None
    if metric_type and threshold is not None:
        detection_value = problem.metrics.get(metric_type.lower())
        if detection_value is not None:
            higher_is_worse = detection_value > threshold

    return HealingAction(
        id=f"heal-ai-{problem.id}-{int(time.time())}",
        station_id=problem.station_id,
        action_type=action_type,
        parameters={
            "problem_code": problem.code,
            "commands": solution.commands,
            "expected_outcome": solution.expected_outcome,
            "metric_type": metric_type,
            "threshold": threshold,
            "higher_is_worse": higher_is_worse,
        },
        description=solution.action,
        risk_level=risk_level,
        source="ai-diagnostic",
        source_id=problem.id,
        auto_execute=auto_execute,
        timeout_seconds=300,
    )


_monitoring_callback: Optional[Callable] = None


def _generate_internal_auth(username: str, role: str, secret: str) -> str:
    """Generate X-Internal-Auth HMAC header matching Java InternalAuthFilter."""
    import hmac as hmac_mod
    import hashlib
    timestamp_ms = int(time.time() * 1000)
    payload = f"{username}:{role}:{timestamp_ms}"
    signature = hmac_mod.new(
        secret.encode(), payload.encode(), hashlib.sha256
    ).hexdigest()
    return f"{signature}.{payload}"


def _get_monitoring_callback() -> Optional[Callable]:
    """Create callback that reports healing results to monitoring service."""
    global _monitoring_callback
    if _monitoring_callback is not None:
        return _monitoring_callback

    monitoring_url = os.environ.get(
        "MONITORING_SERVICE_URL", "http://monitoring-service:8082"
    )
    internal_secret = os.environ.get("SECURITY_INTERNAL_SECRET", "")

    def callback(problem_id: str, success: bool, output: str) -> None:
        try:
            import requests as req
            url = f"{monitoring_url}/api/v1/diagnostics/by-problem/{problem_id}/healing-result"
            headers = {"Content-Type": "application/json"}
            if internal_secret:
                headers["X-Internal-Auth"] = _generate_internal_auth(
                    "system-healing", "SERVICE", internal_secret
                )
                headers["X-User-Name"] = "system-healing"
                headers["X-User-Role"] = "SERVICE"
            resp = req.post(url, json={
                "success": success,
                "actionId": problem_id,
                "output": output[:500],
            }, headers=headers, timeout=10)
            logger.info(
                "Reported healing result for %s: success=%s (HTTP %s)",
                problem_id, success, resp.status_code
            )
        except Exception as e:
            logger.warning("Failed to report healing result for %s: %s", problem_id, e)

    _monitoring_callback = callback
    return _monitoring_callback


def submit_healing_action(
    problem: 'Problem',
    solution: 'Solution',
    min_confidence: float = 0.7
) -> Optional[Dict]:
    """
    Submit a healing action for automatic execution.

    Args:
        problem: The diagnosed problem
        solution: The AI-generated solution
        min_confidence: Minimum confidence required for auto-healing

    Returns:
        Healing status dict or None if not submitted
    """
    if not SELF_HEALING_AVAILABLE:
        return None

    if solution.confidence < min_confidence:
        return None

    healing_action = create_healing_action(problem, solution)
    if not healing_action:
        return None

    try:
        healing_service = get_self_healing_service(
            diagnostic_callback=_get_monitoring_callback()
        )
        submit_result = healing_service.submit_action(healing_action)

        logger.info(
            f"Auto-healing action {healing_action.id} "
            f"submitted for {problem.code}"
        )

        return {
            'action_id': healing_action.id,
            'auto_execute': healing_action.auto_execute,
            'status': submit_result.get('status'),
            'risk_level': healing_action.risk_level.value
        }
    except Exception as e:
        logger.warning(f"Failed to submit healing action: {e}")
        return {'error': str(e)}
