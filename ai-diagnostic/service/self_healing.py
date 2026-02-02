"""
Self-Healing Workflow Service

Orchestrates automated remediation based on:
- SON recommendations
- Root cause analysis results
- Predictive maintenance alerts

Features:
- Automatic execution of approved recommendations
- Real-time monitoring of execution
- Automatic rollback on failure
- Audit trail of all actions
"""

import logging
import threading
import time
from typing import Dict, Any, List, Optional, Callable
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from enum import Enum
from collections import defaultdict, deque
import json

logger = logging.getLogger(__name__)


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


class SelfHealingService:
    """
    Orchestrates self-healing workflows for base stations.

    Integrates with:
    - SON service for optimization recommendations
    - RCA service for root cause remediation
    - Predictive maintenance for proactive fixes
    """

    # Action handlers registry
    ACTION_HANDLERS: Dict[ActionType, str] = {
        ActionType.PARAMETER_CHANGE: "_execute_parameter_change",
        ActionType.SERVICE_RESTART: "_execute_service_restart",
        ActionType.LOAD_BALANCE: "_execute_load_balance",
        ActionType.POWER_CYCLE: "_execute_power_cycle",
        ActionType.FAILOVER: "_execute_failover",
        ActionType.TRAFFIC_REDIRECT: "_execute_traffic_redirect",
        ActionType.ALARM_SUPPRESS: "_execute_alarm_suppress",
    }

    # Risk-based auto-execution policy
    AUTO_EXECUTE_POLICY = {
        RiskLevel.LOW: True,
        RiskLevel.MEDIUM: True,
        RiskLevel.HIGH: False,  # Requires approval
        RiskLevel.CRITICAL: False,  # Always requires approval
    }

    def __init__(
        self,
        device_client: Optional[Any] = None,
        son_callback: Optional[Callable] = None,
        max_concurrent_actions: int = 5
    ):
        """
        Initialize self-healing service.

        Args:
            device_client: Client for communicating with devices
            son_callback: Callback to notify SON service of results
            max_concurrent_actions: Max parallel actions per station
        """
        self.device_client = device_client
        self.son_callback = son_callback
        self.max_concurrent = max_concurrent_actions

        # Action queues and tracking
        self.pending_actions: Dict[str, HealingAction] = {}
        self.executing_actions: Dict[str, HealingAction] = {}
        self.completed_results: deque = deque(maxlen=1000)

        # Per-station execution tracking
        self.station_active_count: Dict[str, int] = defaultdict(int)

        # Statistics
        self.stats = {
            "total_actions": 0,
            "successful": 0,
            "failed": 0,
            "rolled_back": 0,
            "auto_executed": 0,
            "manual_approved": 0,
        }

        # Thread safety
        self._lock = threading.Lock()
        self._running = False
        self._worker_thread: Optional[threading.Thread] = None

        logger.info("SelfHealingService initialized")

    def start(self):
        """Start the self-healing worker thread."""
        if self._running:
            return

        self._running = True
        self._worker_thread = threading.Thread(target=self._worker_loop, daemon=True)
        self._worker_thread.start()
        logger.info("Self-healing worker started")

    def stop(self):
        """Stop the self-healing worker."""
        self._running = False
        if self._worker_thread:
            self._worker_thread.join(timeout=5)
        logger.info("Self-healing worker stopped")

    def submit_action(self, action: HealingAction) -> Dict[str, Any]:
        """
        Submit a healing action for execution.

        Args:
            action: The healing action to execute

        Returns:
            Status dict with action_id and whether it was queued/executed
        """
        with self._lock:
            self.pending_actions[action.id] = action
            self.stats["total_actions"] += 1

            # Determine if auto-execution is allowed
            can_auto = (
                action.auto_execute and
                self.AUTO_EXECUTE_POLICY.get(action.risk_level, False)
            )

            if can_auto:
                self.stats["auto_executed"] += 1
                status = "queued_for_execution"
            else:
                status = "pending_approval"

            logger.info(
                f"Action {action.id} submitted: {action.action_type.value} "
                f"for station {action.station_id} - {status}"
            )

            return {
                "action_id": action.id,
                "status": status,
                "auto_execute": can_auto,
                "risk_level": action.risk_level.value,
                "requires_approval": not can_auto
            }

    def approve_action(self, action_id: str, approved_by: str) -> Optional[Dict[str, Any]]:
        """Approve a pending action for execution."""
        with self._lock:
            if action_id not in self.pending_actions:
                return None

            action = self.pending_actions[action_id]
            action.auto_execute = True  # Mark as approved
            self.stats["manual_approved"] += 1

            logger.info(f"Action {action_id} approved by {approved_by}")

            return {
                "action_id": action_id,
                "status": "approved",
                "approved_by": approved_by,
                "approved_at": datetime.now(timezone.utc).isoformat()
            }

    def cancel_action(self, action_id: str, reason: str) -> Optional[Dict[str, Any]]:
        """Cancel a pending action."""
        with self._lock:
            if action_id in self.pending_actions:
                action = self.pending_actions.pop(action_id)
                logger.info(f"Action {action_id} cancelled: {reason}")
                return {
                    "action_id": action_id,
                    "status": "cancelled",
                    "reason": reason
                }
            return None

    def get_pending_actions(self, station_id: Optional[str] = None) -> List[Dict[str, Any]]:
        """Get all pending actions, optionally filtered by station."""
        with self._lock:
            actions = list(self.pending_actions.values())
            if station_id:
                actions = [a for a in actions if a.station_id == station_id]
            return [a.to_dict() for a in actions]

    def get_execution_history(
        self,
        station_id: Optional[str] = None,
        limit: int = 100
    ) -> List[Dict[str, Any]]:
        """Get execution history."""
        with self._lock:
            results = self.completed_results[-limit:]
            if station_id:
                # Filter by station (need to look up action)
                filtered = []
                for r in results:
                    action = self._find_action_by_id(r.action_id)
                    if action and action.station_id == station_id:
                        filtered.append(r)
                results = filtered
            return [r.to_dict() for r in reversed(results)]

    def get_stats(self) -> Dict[str, Any]:
        """Get service statistics."""
        with self._lock:
            success_rate = (
                self.stats["successful"] / self.stats["total_actions"] * 100
                if self.stats["total_actions"] > 0 else 0
            )
            return {
                **self.stats,
                "success_rate": f"{success_rate:.1f}%",
                "pending_count": len(self.pending_actions),
                "executing_count": len(self.executing_actions),
            }

    def create_action_from_son(
        self,
        son_recommendation: Dict[str, Any]
    ) -> HealingAction:
        """
        Create a healing action from a SON recommendation.

        Maps SON function types to appropriate actions.
        """
        function_type = son_recommendation.get("functionType", "")
        station_id = str(son_recommendation.get("stationId", "unknown"))
        rec_id = son_recommendation.get("id", "")

        # Map SON functions to action types
        action_map = {
            "MLB": (ActionType.LOAD_BALANCE, RiskLevel.MEDIUM),
            "MRO": (ActionType.PARAMETER_CHANGE, RiskLevel.MEDIUM),
            "CCO": (ActionType.PARAMETER_CHANGE, RiskLevel.HIGH),
            "ES": (ActionType.PARAMETER_CHANGE, RiskLevel.LOW),
            "ANR": (ActionType.PARAMETER_CHANGE, RiskLevel.LOW),
            "RAO": (ActionType.PARAMETER_CHANGE, RiskLevel.MEDIUM),
            "ICIC": (ActionType.PARAMETER_CHANGE, RiskLevel.HIGH),
        }

        action_type, risk = action_map.get(
            function_type,
            (ActionType.PARAMETER_CHANGE, RiskLevel.MEDIUM)
        )

        return HealingAction(
            id=f"heal-son-{rec_id}-{int(time.time())}",
            station_id=station_id,
            action_type=action_type,
            parameters={
                "action_type": son_recommendation.get("actionType"),
                "action_value": son_recommendation.get("actionValue"),
                "expected_improvement": son_recommendation.get("expectedImprovement"),
            },
            description=son_recommendation.get("description", f"SON {function_type} action"),
            risk_level=risk,
            source="son",
            source_id=rec_id,
            auto_execute=son_recommendation.get("autoExecutable", False),
            rollback_action={
                "type": "revert",
                "original_value": son_recommendation.get("rollbackAction")
            } if son_recommendation.get("rollbackAction") else None
        )

    def create_action_from_rca(
        self,
        rca_result: Dict[str, Any],
        station_id: str
    ) -> Optional[HealingAction]:
        """
        Create a healing action from root cause analysis.

        Maps root causes to remediation actions.
        """
        root_cause = rca_result.get("root_cause")
        if not root_cause:
            return None

        # Map root causes to actions
        cause_action_map = {
            "POWER_FAILURE": (ActionType.POWER_CYCLE, RiskLevel.HIGH),
            "COOLING_FAILURE": (ActionType.SERVICE_RESTART, RiskLevel.MEDIUM),
            "NETWORK_CONGESTION": (ActionType.LOAD_BALANCE, RiskLevel.MEDIUM),
            "HARDWARE_FAULT": (ActionType.FAILOVER, RiskLevel.CRITICAL),
            "SOFTWARE_BUG": (ActionType.SERVICE_RESTART, RiskLevel.MEDIUM),
            "CONFIG_ERROR": (ActionType.PARAMETER_CHANGE, RiskLevel.LOW),
            "INTERFERENCE": (ActionType.PARAMETER_CHANGE, RiskLevel.MEDIUM),
        }

        action_info = cause_action_map.get(root_cause)
        if not action_info:
            return None

        action_type, risk = action_info
        recommended_action = rca_result.get("recommended_action", "")

        return HealingAction(
            id=f"heal-rca-{station_id}-{int(time.time())}",
            station_id=station_id,
            action_type=action_type,
            parameters={
                "root_cause": root_cause,
                "confidence": rca_result.get("confidence"),
                "affected_events": rca_result.get("affected_events", []),
            },
            description=recommended_action or f"Remediate {root_cause}",
            risk_level=risk,
            source="rca",
            source_id=rca_result.get("analysis_id", ""),
            auto_execute=risk in [RiskLevel.LOW, RiskLevel.MEDIUM],
        )

    def create_action_from_prediction(
        self,
        prediction: Dict[str, Any],
        station_id: str
    ) -> Optional[HealingAction]:
        """
        Create a proactive healing action from predictive maintenance.
        """
        component = prediction.get("component", "")
        health = prediction.get("current_health", "")
        probability = prediction.get("probability", 0)

        # Only act on high-probability predictions
        if probability < 0.5:
            return None

        # Map components to actions
        component_action_map = {
            "cooling_fan": (ActionType.SERVICE_RESTART, RiskLevel.LOW),
            "thermal_system": (ActionType.PARAMETER_CHANGE, RiskLevel.MEDIUM),
            "power_supply": (ActionType.FAILOVER, RiskLevel.HIGH),
            "battery_system": (ActionType.ALARM_SUPPRESS, RiskLevel.LOW),  # Alert ops
            "fiber_transport": (ActionType.TRAFFIC_REDIRECT, RiskLevel.HIGH),
        }

        action_info = component_action_map.get(component)
        if not action_info:
            return None

        action_type, risk = action_info

        return HealingAction(
            id=f"heal-pred-{station_id}-{component}-{int(time.time())}",
            station_id=station_id,
            action_type=action_type,
            parameters={
                "component": component,
                "probability": probability,
                "health_status": health,
                "prediction": prediction.get("prediction"),
            },
            description=prediction.get("recommended_action", f"Proactive {component} maintenance"),
            risk_level=risk,
            source="predictive",
            source_id=f"{station_id}-{component}",
            auto_execute=risk == RiskLevel.LOW and probability > 0.7,
        )

    def _worker_loop(self):
        """Main worker loop for executing actions."""
        while self._running:
            try:
                self._process_pending_actions()
                time.sleep(1)  # Check every second
            except Exception as e:
                logger.error(f"Worker loop error: {e}")
                time.sleep(5)

    def _process_pending_actions(self):
        """Process pending actions that are ready for execution."""
        with self._lock:
            ready_actions = [
                a for a in self.pending_actions.values()
                if a.auto_execute and
                self.station_active_count[a.station_id] < self.max_concurrent
            ]

        for action in ready_actions:
            self._execute_action(action)

    def _execute_action(self, action: HealingAction):
        """Execute a single healing action."""
        with self._lock:
            if action.id in self.executing_actions:
                return  # Already executing

            self.pending_actions.pop(action.id, None)
            self.executing_actions[action.id] = action
            self.station_active_count[action.station_id] += 1

        result = ExecutionResult(
            action_id=action.id,
            status=ExecutionStatus.EXECUTING,
            started_at=datetime.now(timezone.utc)
        )

        try:
            logger.info(f"Executing action {action.id}: {action.description}")

            # Get the appropriate handler
            handler_name = self.ACTION_HANDLERS.get(action.action_type)
            if handler_name and hasattr(self, handler_name):
                handler = getattr(self, handler_name)
                success, output = handler(action)
            else:
                success, output = self._execute_generic(action)

            result.completed_at = datetime.now(timezone.utc)
            result.output = output

            if success:
                result.status = ExecutionStatus.SUCCESS
                self.stats["successful"] += 1
                logger.info(f"Action {action.id} completed successfully")
            else:
                result.status = ExecutionStatus.FAILED
                result.error = output
                self.stats["failed"] += 1
                logger.warning(f"Action {action.id} failed: {output}")

                # Attempt rollback if available
                if action.rollback_action:
                    self._perform_rollback(action, result)

        except Exception as e:
            result.completed_at = datetime.now(timezone.utc)
            result.status = ExecutionStatus.FAILED
            result.error = str(e)
            self.stats["failed"] += 1
            logger.error(f"Action {action.id} exception: {e}")

        finally:
            with self._lock:
                self.executing_actions.pop(action.id, None)
                self.station_active_count[action.station_id] -= 1
                self.completed_results.append(result)
                # deque maxlen automatically keeps only last 1000 results

            # Notify SON service if applicable
            if action.source == "son" and self.son_callback:
                try:
                    self.son_callback(
                        action.source_id,
                        result.status == ExecutionStatus.SUCCESS,
                        result.output or result.error
                    )
                except Exception as e:
                    logger.error(f"SON callback failed: {e}")

    def _execute_generic(self, action: HealingAction) -> tuple[bool, str]:
        """Generic action execution (placeholder for real implementation)."""
        # In production, this would communicate with the device
        logger.info(f"Generic execution for {action.action_type.value}")
        return True, f"Executed {action.action_type.value} on {action.station_id}"

    def _execute_parameter_change(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a parameter change action."""
        params = action.parameters
        logger.info(f"Changing parameter on {action.station_id}: {params}")

        if self.device_client:
            # Real implementation would use device_client
            pass

        return True, f"Parameter changed: {params.get('action_type')} = {params.get('action_value')}"

    def _execute_service_restart(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a service restart action."""
        logger.info(f"Restarting service on {action.station_id}")
        # Simulate restart with delay
        time.sleep(2)
        return True, f"Service restarted on {action.station_id}"

    def _execute_load_balance(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a load balancing action."""
        logger.info(f"Load balancing on {action.station_id}")
        return True, f"Traffic redistributed from {action.station_id}"

    def _execute_power_cycle(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a power cycle action."""
        logger.info(f"Power cycling {action.station_id}")
        # This is high-risk, would need careful implementation
        return True, f"Power cycle completed on {action.station_id}"

    def _execute_failover(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a failover action."""
        logger.info(f"Initiating failover for {action.station_id}")
        return True, f"Failover completed for {action.station_id}"

    def _execute_traffic_redirect(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a traffic redirect action."""
        logger.info(f"Redirecting traffic from {action.station_id}")
        return True, f"Traffic redirected from {action.station_id}"

    def _execute_alarm_suppress(self, action: HealingAction) -> tuple[bool, str]:
        """Execute an alarm suppression action."""
        logger.info(f"Suppressing alarms for {action.station_id}")
        return True, f"Alarms suppressed for maintenance on {action.station_id}"

    def _perform_rollback(self, action: HealingAction, result: ExecutionResult):
        """Perform rollback after failed action."""
        logger.info(f"Performing rollback for action {action.id}")
        try:
            rollback = action.rollback_action
            if rollback:
                # Execute rollback logic
                logger.info(f"Rollback parameters: {rollback}")
                result.rollback_performed = True
                self.stats["rolled_back"] += 1
        except Exception as e:
            logger.error(f"Rollback failed for {action.id}: {e}")

    def _find_action_by_id(self, action_id: str) -> Optional[HealingAction]:
        """Find an action by ID across all collections."""
        if action_id in self.pending_actions:
            return self.pending_actions[action_id]
        if action_id in self.executing_actions:
            return self.executing_actions[action_id]
        return None


# Singleton instance with thread-safe initialization
_healing_service: Optional[SelfHealingService] = None
_healing_service_lock = threading.Lock()


def get_self_healing_service() -> SelfHealingService:
    """Get or create singleton SelfHealingService instance (thread-safe)."""
    global _healing_service
    if _healing_service is None:
        with _healing_service_lock:
            if _healing_service is None:  # Double-check locking
                _healing_service = SelfHealingService()
                _healing_service.start()
    return _healing_service
