"""
Healing Integration Module

Bridges AI diagnostic solutions with the self-healing service.
Converts solutions to healing actions for automatic execution.
"""

import time
import logging
from typing import Optional, Dict, TYPE_CHECKING

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
        "CPU_OVERHEAT": ActionType.PARAMETER_CHANGE,
        "CPU_HIGH_USAGE": ActionType.PARAMETER_CHANGE,
        "MEMORY_PRESSURE": ActionType.SERVICE_RESTART,
        "SIGNAL_DEGRADATION": ActionType.PARAMETER_CHANGE,
        "BACKHAUL_LATENCY": ActionType.PARAMETER_CHANGE,
        "POWER_FLUCTUATION": ActionType.PARAMETER_CHANGE,
        "HIGH_INTERFERENCE": ActionType.PARAMETER_CHANGE,
        "PROCESS_CRASH": ActionType.SERVICE_RESTART,
        "CONFIG_MISMATCH": ActionType.PARAMETER_CHANGE,
        "FAN_FAILURE": ActionType.SERVICE_RESTART,
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

    return HealingAction(
        id=f"heal-ai-{problem.id}-{int(time.time())}",
        station_id=problem.station_id,
        action_type=action_type,
        parameters={
            "problem_code": problem.code,
            "commands": solution.commands,
            "expected_outcome": solution.expected_outcome,
        },
        description=solution.action,
        risk_level=risk_level,
        source="ai-diagnostic",
        source_id=problem.id,
        auto_execute=auto_execute,
        timeout_seconds=300,
    )


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
        healing_service = get_self_healing_service()
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
