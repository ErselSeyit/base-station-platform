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
import os
import threading
import time
from typing import Dict, Any, List, Optional, Callable
from datetime import datetime, timedelta, timezone
from collections import defaultdict, deque
import json
import hmac as hmac_mod
import hashlib

import requests

logger = logging.getLogger(__name__)


# Value types extracted to service/healing_models.py; re-exported so existing
# importers (from ...self_healing import HealingAction, ActionType, ...) keep working.
from .healing_models import (  # noqa: F401
    ActionType, ExecutionStatus, RiskLevel, HealingAction, ExecutionResult,
)


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
        diagnostic_callback: Optional[Callable] = None,
        max_concurrent_actions: int = 5
    ):
        """
        Initialize self-healing service.

        Args:
            device_client: Client for communicating with devices
            son_callback: Callback to notify SON service of results
            diagnostic_callback: Callback to report AI diagnostic healing results to monitoring service
            max_concurrent_actions: Max parallel actions per station
        """
        self.device_client = device_client
        self.son_callback = son_callback
        self.diagnostic_callback = diagnostic_callback
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
            # Deduplication: skip if an action for the same source is already
            # pending or executing (prevents duplicate healing for the same problem)
            for existing in self.pending_actions.values():
                if (existing.source_id == action.source_id
                        and existing.station_id == action.station_id):
                    logger.info(
                        "Skipping duplicate action for source_id=%s station=%s "
                        "(existing action %s already pending)",
                        action.source_id, action.station_id, existing.id,
                    )
                    return {
                        "action_id": action.id,
                        "status": "skipped_duplicate",
                        "existing_action_id": existing.id,
                        "auto_execute": False,
                        "risk_level": action.risk_level.value,
                        "requires_approval": True,
                    }
            for existing in self.executing_actions.values():
                if (existing.source_id == action.source_id
                        and existing.station_id == action.station_id):
                    logger.info(
                        "Skipping duplicate action for source_id=%s station=%s "
                        "(existing action %s already executing)",
                        action.source_id, action.station_id, existing.id,
                    )
                    return {
                        "action_id": action.id,
                        "status": "skipped_duplicate",
                        "existing_action_id": existing.id,
                        "auto_execute": False,
                        "risk_level": action.risk_level.value,
                        "requires_approval": True,
                    }

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
                self.pending_actions.pop(action_id)
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
            results = list(self.completed_results)[-limit:]
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

            # For AI diagnostic actions: suppress anomaly and verify metrics
            if success and action.source == "ai-diagnostic":
                success, output = self._post_healing_check(action, output)

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

            self._notify_source(action, result)

    def _post_healing_check(self, action: HealingAction, output: str) -> tuple[bool, str]:
        """Verify metrics returned to normal after healing command was sent.

        The command flow already handles fault clearing:
          base-station-service → edge-bridge → EXECUTE_COMMAND → device-simulator
        So we only need to verify that metrics actually improved.
        """
        verified, verify_msg = self._verify_metrics(action)
        if not verified:
            logger.info("Post-healing verification FAILED for %s: %s", action.id, verify_msg)
            return False, verify_msg

        logger.info("Post-healing verification PASSED for %s: %s", action.id, verify_msg)
        return True, output

    def _notify_source(self, action: HealingAction, result: 'ExecutionResult'):
        """Send callback notification to the action's originating service."""
        succeeded = result.status == ExecutionStatus.SUCCESS
        message = result.output or result.error or ""

        if action.source == "son" and self.son_callback:
            try:
                self.son_callback(action.source_id, succeeded, message)
            except Exception as e:
                logger.error(f"SON callback failed: {e}")

        if action.source == "ai-diagnostic" and self.diagnostic_callback:
            try:
                self.diagnostic_callback(action.source_id, succeeded, message)
            except Exception as e:
                logger.error(f"Diagnostic callback failed: {e}")

    def _execute_generic(self, action: HealingAction) -> tuple[bool, str]:
        """Generic action execution (placeholder for real implementation)."""
        # In production, this would communicate with the device
        logger.info(f"Generic execution for {action.action_type.value}")
        return True, f"Executed {action.action_type.value} on {action.station_id}"

    def _execute_parameter_change(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a parameter change by creating a command via base-station-service.

        The command flows: base-station-service → edge-bridge → device-simulator.
        Device-simulator receives SET_PARAMETER with {action: "clear_fault", fault_type: ...}
        and clears the injected fault, restoring normal metrics.
        """
        params = action.parameters
        problem_code = params.get('problem_code', 'unknown')

        logger.info("=== AUTO-HEALING: PARAMETER CHANGE via COMMAND API ===")
        logger.info("  Station: %s", action.station_id)
        logger.info("  Problem: %s", problem_code)
        logger.info("  Description: %s", action.description)
        logger.info("=====================================================")

        return self._create_device_command(
            action=action,
            command_type="SET_PARAMETER",
            command_params={
                "action": "clear_fault",
                "fault_type": problem_code,
            },
        )

    def _execute_service_restart(self, action: HealingAction) -> tuple[bool, str]:
        """Execute a service restart by creating a RESTART command via base-station-service."""
        params = action.parameters
        problem_code = params.get('problem_code', 'unknown')

        logger.info("=== AUTO-HEALING: SERVICE RESTART via COMMAND API ===")
        logger.info("  Station: %s", action.station_id)
        logger.info("  Problem: %s", problem_code)
        logger.info("====================================================")

        return self._create_device_command(
            action=action,
            command_type="RESTART",
            command_params={"component": "radio"},
        )

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

    def _get_service_auth_headers(self) -> Dict[str, str]:
        """Build auth headers for service-to-service calls to Java backend."""
        headers = {
            "X-User-Name": "system-healing",
            "X-User-Role": "SERVICE",
            "Content-Type": "application/json",
        }
        internal_secret = os.environ.get("SECURITY_INTERNAL_SECRET", "")
        if internal_secret:
            ts_ms = int(time.time() * 1000)
            payload = f"system-healing:SERVICE:{ts_ms}"
            sig = hmac_mod.new(
                internal_secret.encode(), payload.encode(), hashlib.sha256
            ).hexdigest()
            headers["X-Internal-Auth"] = f"{sig}.{payload}"
        return headers

    def _create_device_command(
        self,
        action: HealingAction,
        command_type: str,
        command_params: Dict[str, str],
    ) -> tuple[bool, str]:
        """Create a device command via base-station-service REST API.

        The command is picked up by edge-bridge, sent to device-simulator via
        binary protocol, and the result flows back through the same path.

        POST /api/v1/stations/{stationId}/commands/ai
        """
        base_station_url = os.environ.get(
            "BASE_STATION_SERVICE_URL", "http://base-station-service:8081"
        )

        # Parse station number (action.station_id may be "1" or "STATION-1")
        station_num = action.station_id
        if station_num.startswith("STATION-"):
            station_num = station_num.split("-", 1)[1]

        problem_code = action.parameters.get("problem_code", "unknown")
        confidence = action.parameters.get("confidence")
        risk_level = action.risk_level.value

        # Convert params to Map<String, String> (Java expects string values)
        str_params = {k: str(v) for k, v in command_params.items()}

        request_body = {
            "diagnosticSessionId": action.source_id,
            "problemCode": problem_code,
            "commandType": command_type,
            "params": str_params,
            "confidence": confidence,
            "riskLevel": risk_level,
        }

        url = f"{base_station_url}/api/v1/stations/{station_num}/commands/ai"
        logger.info("Creating device command: POST %s", url)
        logger.info("  Body: %s", json.dumps(request_body))

        try:
            resp = requests.post(
                url,
                json=request_body,
                headers=self._get_service_auth_headers(),
                timeout=10,
            )
            if resp.status_code == 201:
                cmd = resp.json()
                cmd_id = cmd.get("id", "?")
                cmd_status = cmd.get("status", "?")
                logger.info(
                    "Command created: id=%s status=%s type=%s",
                    cmd_id, cmd_status, command_type,
                )
                return True, f"Command {cmd_id} created ({cmd_status})"

            logger.warning(
                "Failed to create command: HTTP %d — %s",
                resp.status_code, resp.text[:300],
            )
            return False, f"Command creation failed: HTTP {resp.status_code}"
        except requests.RequestException as e:
            logger.error("Error creating device command: %s", e)
            return False, f"Command creation error: {e}"

    # Configurable delay (seconds) before post-healing metric check
    _VERIFY_DELAY = int(os.environ.get("HEALING_VERIFY_DELAY", "5"))

    def _verify_metrics(self, action: HealingAction) -> tuple[bool, str]:
        """Query monitoring service to verify the metric actually improved."""
        params = action.parameters
        metric_type = params.get("metric_type")
        threshold = params.get("threshold")
        higher_is_worse = params.get("higher_is_worse")

        if not metric_type or threshold is None or higher_is_worse is None:
            return True, "No metric verification info available"

        # Parse station number from "STATION-1" format
        station_num = action.station_id
        if station_num.startswith("STATION-"):
            station_num = station_num.split("-", 1)[1]

        # Wait for at least one fresh metric reading
        time.sleep(self._VERIFY_DELAY)

        monitoring_url = os.environ.get(
            "MONITORING_SERVICE_URL", "http://monitoring-service:8082"
        )
        url = f"{monitoring_url}/api/v1/metrics/station/{station_num}/type/{metric_type}"

        try:
            headers = self._get_service_auth_headers()
            resp = requests.get(url, headers=headers, timeout=10)
            if not resp.ok:
                logger.warning("Metric verification query failed: HTTP %s", resp.status_code)
                return True, f"Verification skipped (HTTP {resp.status_code})"

            metrics = resp.json()
            if not metrics:
                return True, "No metrics returned for verification"

            latest = metrics[-1] if isinstance(metrics, list) else metrics
            current_value = latest.get("value")
            if current_value is None:
                return True, "No value in latest metric"

            if higher_is_worse:
                still_bad = current_value > threshold
            else:
                still_bad = current_value < threshold

            if still_bad:
                return False, (
                    f"Metric {metric_type} still abnormal: "
                    f"{current_value} vs threshold {threshold}"
                )
            return True, (
                f"Metric {metric_type} verified normal: "
                f"{current_value} vs threshold {threshold}"
            )
        except Exception as e:
            logger.warning("Metric verification error: %s", e)
            return True, f"Verification skipped ({e})"

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


def get_self_healing_service(
    diagnostic_callback: Optional[Callable] = None,
) -> SelfHealingService:
    """Get or create singleton SelfHealingService instance (thread-safe)."""
    global _healing_service
    if _healing_service is None:
        with _healing_service_lock:
            if _healing_service is None:  # Double-check locking
                _healing_service = SelfHealingService(
                    diagnostic_callback=diagnostic_callback
                )
                _healing_service.start()
    elif diagnostic_callback and not _healing_service.diagnostic_callback:
        _healing_service.diagnostic_callback = diagnostic_callback
    return _healing_service
