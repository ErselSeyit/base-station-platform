"""
Tests for the self-healing service.

Covers HealingAction, ExecutionResult dataclasses and
the SelfHealingService submit/approve/cancel/stats workflow.
"""

from datetime import datetime, timezone
from unittest.mock import MagicMock

import pytest

from service.self_healing import (
    ActionType,
    ExecutionStatus,
    HealingAction,
    ExecutionResult,
    RiskLevel,
    SelfHealingService,
)


# ---- Helpers ----

def _make_action(
    action_id: str = "heal-001",
    station_id: str = "STATION-1",
    action_type: ActionType = ActionType.PARAMETER_CHANGE,
    risk_level: RiskLevel = RiskLevel.LOW,
    auto_execute: bool = False,
    source: str = "rca",
    source_id: str = "src-001",
) -> HealingAction:
    """Create a HealingAction with sensible defaults for testing."""
    return HealingAction(
        id=action_id,
        station_id=station_id,
        action_type=action_type,
        parameters={"key": "value"},
        description="Test healing action",
        risk_level=risk_level,
        source=source,
        source_id=source_id,
        auto_execute=auto_execute,
    )


# ---- HealingAction dataclass tests ----

class TestHealingActionDefaults:

    def test_healing_action_defaults(self):
        action = _make_action()
        assert action.timeout_seconds == 300
        assert action.rollback_action is None
        assert action.pre_check is None
        assert action.post_check is None
        assert isinstance(action.created_at, datetime)

    def test_healing_action_to_dict(self):
        action = _make_action(action_id="heal-dict")
        d = action.to_dict()
        assert d["id"] == "heal-dict"
        assert d["action_type"] == ActionType.PARAMETER_CHANGE.value
        assert d["risk_level"] == RiskLevel.LOW.value
        assert d["timeout_seconds"] == 300
        assert d["rollback_action"] is None


# ---- ExecutionResult dataclass tests ----

class TestExecutionResult:

    def test_execution_result_creation(self):
        now = datetime.now(timezone.utc)
        result = ExecutionResult(
            action_id="heal-001",
            status=ExecutionStatus.SUCCESS,
            started_at=now,
            completed_at=now,
            output="All good",
        )
        assert result.action_id == "heal-001"
        assert result.status == ExecutionStatus.SUCCESS
        assert result.output == "All good"
        assert result.error is None
        assert result.rollback_performed is False

    def test_execution_result_to_dict_includes_duration(self):
        start = datetime(2025, 1, 1, 0, 0, 0, tzinfo=timezone.utc)
        end = datetime(2025, 1, 1, 0, 0, 10, tzinfo=timezone.utc)
        result = ExecutionResult(
            action_id="heal-002",
            status=ExecutionStatus.SUCCESS,
            started_at=start,
            completed_at=end,
        )
        d = result.to_dict()
        assert d["duration_seconds"] == 10.0

    def test_execution_result_to_dict_no_completed_at(self):
        now = datetime.now(timezone.utc)
        result = ExecutionResult(
            action_id="heal-003",
            status=ExecutionStatus.EXECUTING,
            started_at=now,
        )
        d = result.to_dict()
        assert d["completed_at"] is None
        assert d["duration_seconds"] is None


# ---- Enum tests ----

class TestEnums:

    def test_action_type_enum_values(self):
        assert ActionType.PARAMETER_CHANGE.value == "parameter_change"
        assert ActionType.SERVICE_RESTART.value == "service_restart"
        assert ActionType.LOAD_BALANCE.value == "load_balance"
        assert ActionType.POWER_CYCLE.value == "power_cycle"
        assert ActionType.FAILOVER.value == "failover"
        assert ActionType.TRAFFIC_REDIRECT.value == "traffic_redirect"
        assert ActionType.ALARM_SUPPRESS.value == "alarm_suppress"

    def test_execution_status_values(self):
        assert ExecutionStatus.PENDING.value == "pending"
        assert ExecutionStatus.EXECUTING.value == "executing"
        assert ExecutionStatus.SUCCESS.value == "success"
        assert ExecutionStatus.FAILED.value == "failed"
        assert ExecutionStatus.ROLLED_BACK.value == "rolled_back"
        assert ExecutionStatus.TIMEOUT.value == "timeout"

    def test_risk_level_ordering(self):
        """Verify risk levels have the expected semantic ordering."""
        levels = [RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL]
        values = [l.value for l in levels]
        assert values == ["low", "medium", "high", "critical"]


# ---- SelfHealingService tests ----

class TestSelfHealingServiceSubmit:

    def test_submit_action_returns_action_id(self):
        service = SelfHealingService()
        action = _make_action(action_id="heal-100")
        result = service.submit_action(action)
        assert result["action_id"] == "heal-100"

    def test_submit_action_adds_to_pending(self):
        service = SelfHealingService()
        action = _make_action(action_id="heal-101")
        service.submit_action(action)
        assert "heal-101" in service.pending_actions

    def test_submit_action_increments_total_actions(self):
        service = SelfHealingService()
        action = _make_action()
        service.submit_action(action)
        assert service.stats["total_actions"] == 1

    def test_submit_action_pending_approval_for_high_risk(self):
        service = SelfHealingService()
        action = _make_action(risk_level=RiskLevel.HIGH, auto_execute=True)
        result = service.submit_action(action)
        assert result["status"] == "pending_approval"
        assert result["requires_approval"] is True

    def test_submit_action_queued_for_low_risk_auto(self):
        service = SelfHealingService()
        action = _make_action(risk_level=RiskLevel.LOW, auto_execute=True)
        result = service.submit_action(action)
        assert result["status"] == "queued_for_execution"
        assert result["auto_execute"] is True
        assert result["requires_approval"] is False

    def test_submit_duplicate_action_is_skipped(self):
        service = SelfHealingService()
        action1 = _make_action(action_id="heal-a", source_id="src-dup", station_id="S1")
        action2 = _make_action(action_id="heal-b", source_id="src-dup", station_id="S1")
        service.submit_action(action1)
        result = service.submit_action(action2)
        assert result["status"] == "skipped_duplicate"
        assert result["existing_action_id"] == "heal-a"


class TestSelfHealingServiceCancel:

    def test_cancel_action_removes_from_pending(self):
        service = SelfHealingService()
        action = _make_action(action_id="heal-cancel")
        service.submit_action(action)
        result = service.cancel_action("heal-cancel", "no longer needed")
        assert result is not None
        assert result["status"] == "cancelled"
        assert result["reason"] == "no longer needed"
        assert "heal-cancel" not in service.pending_actions

    def test_cancel_action_nonexistent_returns_none(self):
        service = SelfHealingService()
        result = service.cancel_action("nonexistent-id", "test")
        assert result is None


class TestSelfHealingServiceApprove:

    def test_approve_action_marks_auto_execute(self):
        service = SelfHealingService()
        action = _make_action(
            action_id="heal-approve",
            risk_level=RiskLevel.CRITICAL,
            auto_execute=False,
        )
        service.submit_action(action)
        result = service.approve_action("heal-approve", approved_by="admin")
        assert result is not None
        assert result["status"] == "approved"
        assert result["approved_by"] == "admin"
        assert service.stats["manual_approved"] == 1

    def test_approve_nonexistent_returns_none(self):
        service = SelfHealingService()
        result = service.approve_action("nonexistent", approved_by="admin")
        assert result is None


class TestSelfHealingServiceStats:

    def test_get_stats_initial_values(self):
        service = SelfHealingService()
        stats = service.get_stats()
        assert stats["total_actions"] == 0
        assert stats["successful"] == 0
        assert stats["failed"] == 0
        assert stats["rolled_back"] == 0
        assert stats["auto_executed"] == 0
        assert stats["manual_approved"] == 0
        assert stats["success_rate"] == "0.0%"
        assert stats["pending_count"] == 0
        assert stats["executing_count"] == 0

    def test_get_stats_after_submissions(self):
        service = SelfHealingService()
        service.submit_action(_make_action(action_id="s1", source_id="src-1"))
        service.submit_action(_make_action(action_id="s2", source_id="src-2"))
        stats = service.get_stats()
        assert stats["total_actions"] == 2
        assert stats["pending_count"] == 2


class TestSelfHealingServicePendingActions:

    def test_get_pending_actions_returns_all(self):
        service = SelfHealingService()
        service.submit_action(_make_action(action_id="p1", source_id="src-1", station_id="S1"))
        service.submit_action(_make_action(action_id="p2", source_id="src-2", station_id="S2"))
        pending = service.get_pending_actions()
        assert len(pending) == 2

    def test_get_pending_actions_filtered_by_station(self):
        service = SelfHealingService()
        service.submit_action(_make_action(action_id="p1", source_id="src-1", station_id="S1"))
        service.submit_action(_make_action(action_id="p2", source_id="src-2", station_id="S2"))
        pending = service.get_pending_actions(station_id="S1")
        assert len(pending) == 1
        assert pending[0]["station_id"] == "S1"
