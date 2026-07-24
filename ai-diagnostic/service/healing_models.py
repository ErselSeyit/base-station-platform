"""
Value types for the self-healing workflow.

The action/status/risk enums and the HealingAction / ExecutionResult records,
extracted from self_healing.py so they can be imported without the executor,
worker thread and HTTP machinery.
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, Optional


class ActionType(Enum):
    """Types of self-healing actions."""
    PARAMETER_CHANGE = "parameter_change"
    SERVICE_RESTART = "service_restart"
    LOAD_BALANCE = "load_balance"
    POWER_CYCLE = "power_cycle"
    FAILOVER = "failover"
    TRAFFIC_REDIRECT = "traffic_redirect"
    ALARM_SUPPRESS = "alarm_suppress"


class ExecutionStatus(Enum):
    """Status of action execution."""
    PENDING = "pending"
    EXECUTING = "executing"
    SUCCESS = "success"
    FAILED = "failed"
    ROLLED_BACK = "rolled_back"
    TIMEOUT = "timeout"


class RiskLevel(Enum):
    """Risk level of an action."""
    LOW = "low"          # No service impact
    MEDIUM = "medium"    # Minor service impact
    HIGH = "high"        # Potential service disruption
    CRITICAL = "critical"  # Requires manual approval


@dataclass
class HealingAction:
    """A self-healing action to be executed."""
    id: str
    station_id: str
    action_type: ActionType
    parameters: Dict[str, Any]
    description: str
    risk_level: RiskLevel
    source: str  # 'son', 'rca', 'predictive'
    source_id: str  # ID of the source recommendation/analysis
    auto_execute: bool
    timeout_seconds: int = 300
    rollback_action: Optional[Dict[str, Any]] = None
    pre_check: Optional[str] = None  # Command to run before execution
    post_check: Optional[str] = None  # Command to verify success
    created_at: datetime = field(default_factory=datetime.utcnow)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "station_id": self.station_id,
            "action_type": self.action_type.value,
            "parameters": self.parameters,
            "description": self.description,
            "risk_level": self.risk_level.value,
            "source": self.source,
            "source_id": self.source_id,
            "auto_execute": self.auto_execute,
            "timeout_seconds": self.timeout_seconds,
            "rollback_action": self.rollback_action,
            "created_at": self.created_at.isoformat()
        }


@dataclass
class ExecutionResult:
    """Result of executing a healing action."""
    action_id: str
    status: ExecutionStatus
    started_at: datetime
    completed_at: Optional[datetime] = None
    output: str = ""
    error: Optional[str] = None
    metrics_before: Optional[Dict[str, float]] = None
    metrics_after: Optional[Dict[str, float]] = None
    rollback_performed: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "action_id": self.action_id,
            "status": self.status.value,
            "started_at": self.started_at.isoformat(),
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "output": self.output,
            "error": self.error,
            "metrics_before": self.metrics_before,
            "metrics_after": self.metrics_after,
            "rollback_performed": self.rollback_performed,
            "duration_seconds": (
                (self.completed_at - self.started_at).total_seconds()
                if self.completed_at else None
            )
        }
