"""
Characterisation tests for the core, deterministic pieces of
diagnostic_service.py: the data models, the rule-based backend, the in-memory
learning engine, and the cloud client's pure mapping helpers.

diagnostic_service.py is a ~2800-line monolith with no direct test coverage.
These tests lock the observable behaviour of its pure seams so those seams can
be extracted into their own modules without silently changing what the service
diagnoses, learns, or posts (Khorikov / GOOS: refactor under a green suite).
"""

from service.diagnostic_service import (
    CloudClient,
    LearnedPattern,
    LearningEngine,
    Problem,
    RuleBasedBackend,
    Solution,
)


def _problem(code: str, station_id: str = "STATION-1") -> Problem:
    return Problem(
        id="PRB-1",
        timestamp="2026-01-01T00:00:00",
        station_id=station_id,
        category="hardware",
        severity="critical",
        code=code,
        message="msg",
        metrics={},
    )


class TestDataModels:
    def test_learned_pattern_success_rate_is_zero_without_data(self):
        assert LearnedPattern("CPU_OVERHEAT", "hardware").success_rate() == 0.0

    def test_learned_pattern_success_rate_is_a_percentage(self):
        p = LearnedPattern("CPU_OVERHEAT", "hardware", resolved_count=3, failed_count=1)
        assert p.success_rate() == 75.0

    def test_solution_defaults(self):
        s = Solution(problem_id="PRB-1", action="a", commands=[], expected_outcome="o", risk_level="low")
        assert s.confidence == 0.0
        assert s.reasoning == ""


class TestRuleBasedBackend:
    def test_is_always_available(self):
        assert RuleBasedBackend().is_available() is True

    def test_known_code_yields_high_confidence_solution(self):
        s = RuleBasedBackend().diagnose(_problem("CPU_OVERHEAT"))
        # 0.92 is above the 0.90 auto-apply threshold.
        assert s.confidence == 0.92
        assert s.risk_level == "low"
        assert s.problem_id == "PRB-1"
        assert any("thermal" in c.lower() or "fan" in c.lower() for c in s.commands)

    def test_unknown_code_falls_back_with_low_confidence(self):
        s = RuleBasedBackend().diagnose(_problem("NONEXISTENT_CODE"))
        assert s.confidence == 0.3
        assert s.risk_level == "unknown"
        assert "Manual investigation required" in s.action

    def test_high_risk_rule_is_preserved(self):
        # POWER_FLUCTUATION is a high-risk rule; risk level must not be softened.
        assert RuleBasedBackend().diagnose(_problem("POWER_FLUCTUATION")).risk_level == "high"


class TestLearningEngine:
    def test_unknown_pattern_is_none(self):
        assert LearningEngine().get_pattern("X") is None

    def test_effective_feedback_increments_resolved_and_records_action(self):
        e = LearningEngine()
        e.update_pattern("CPU_OVERHEAT", "hardware", was_effective=True, action="restart_fan")
        p = e.get_pattern("CPU_OVERHEAT")
        assert p.resolved_count == 1
        assert p.failed_count == 0
        assert p.successful_actions == ["restart_fan"]

    def test_ineffective_feedback_increments_failed(self):
        e = LearningEngine()
        e.update_pattern("CPU_OVERHEAT", "hardware", was_effective=False, action="bad")
        p = e.get_pattern("CPU_OVERHEAT")
        assert p.failed_count == 1
        assert p.failed_actions == ["bad"]

    def test_duplicate_actions_are_not_repeated(self):
        e = LearningEngine()
        e.update_pattern("C", "cat", True, "same")
        e.update_pattern("C", "cat", True, "same")
        assert e.get_pattern("C").successful_actions == ["same"]

    def test_adjusted_confidence_uses_base_until_three_cases(self):
        e = LearningEngine()
        e.update_pattern("C", "cat", True, "a")
        e.update_pattern("C", "cat", True, "b")
        # Only 2 cases -> still the caller's base confidence.
        assert e.get_adjusted_confidence("C", 0.5) == 0.5

    def test_adjusted_confidence_is_learned_after_three_cases(self):
        e = LearningEngine()
        for _ in range(3):
            e.update_pattern("C", "cat", True, "a")
        # 0.3 * 0.85 + 0.7 * 1.0 = 0.955
        assert e.get_adjusted_confidence("C", 0.5) == 0.955

    def test_stats_aggregate_across_patterns(self):
        e = LearningEngine()
        e.update_pattern("A", "cat", True, "x")
        e.update_pattern("A", "cat", False, "y")
        e.update_pattern("B", "cat", True, "z")
        stats = e.get_stats()
        assert stats["total_patterns"] == 2
        assert stats["total_feedback"] == 3
        assert stats["total_resolved"] == 2
        assert stats["total_failed"] == 1
        assert stats["overall_success_rate"] == 2 / 3 * 100


class TestCloudClientPureHelpers:
    def _client(self) -> CloudClient:
        # base_url empty -> disabled, no credentials required, no network.
        return CloudClient(base_url="", username=None, password=None)

    def test_extract_numeric_station_id(self):
        assert self._client()._extract_station_id("27") == 27

    def test_extract_trailing_number_from_label(self):
        assert self._client()._extract_station_id("MIPS-BS-007") == 7

    def test_extract_defaults_to_one_when_no_digits(self):
        assert self._client()._extract_station_id("NO-NUMBER") == 1

    def test_command_type_mapping_for_known_codes(self):
        c = self._client()
        sol = Solution("PRB-1", "a", [], "o", "low")
        assert c._map_to_command_type("CPU_OVERHEAT", sol) == "THERMAL_CONTROL"
        assert c._map_to_command_type("SIGNAL_DEGRADATION", sol) == "RF_CALIBRATION"

    def test_command_type_mapping_falls_back_to_generic(self):
        assert self._client()._map_to_command_type("UNMAPPED", Solution("PRB-1", "a", [], "o", "low")) == "GENERIC_FIX"

    def test_missing_credentials_with_base_url_is_rejected(self):
        import pytest
        with pytest.raises(ValueError):
            CloudClient(base_url="http://api-gateway:8080", username=None, password=None)
