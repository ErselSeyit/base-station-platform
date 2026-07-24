"""
Tests for the confidence/automation utilities.

Covers get_automation_action across the risk x confidence matrix and
adjust_confidence_from_feedback (insufficient data, boost, penalty, neutral
band, and clamping).
"""

import pytest

from service.utils.confidence import (
    RiskLevel,
    get_automation_action,
    adjust_confidence_from_feedback,
    MIN_FEEDBACK_FOR_CONFIDENCE_ADJUSTMENT,
)


class TestGetAutomationActionHighRisk:
    def test_high_risk_never_auto_applies(self):
        # Even at full confidence, HIGH risk requires confirmation.
        action, requires_confirmation = get_automation_action(1.0, RiskLevel.HIGH)
        assert action == "suggest"
        assert requires_confirmation is True

    def test_high_risk_manual_review_band(self):
        assert get_automation_action(0.72, RiskLevel.HIGH) == ("manual_review", True)

    def test_high_risk_low_confidence(self):
        assert get_automation_action(0.50, RiskLevel.HIGH) == ("low_confidence", True)


class TestGetAutomationActionMediumRisk:
    def test_medium_risk_auto_applies_at_95(self):
        assert get_automation_action(0.95, RiskLevel.MEDIUM) == ("auto_apply", False)

    def test_medium_risk_suggests_below_auto(self):
        assert get_automation_action(0.90, RiskLevel.MEDIUM) == ("suggest", True)

    def test_medium_risk_manual_review(self):
        assert get_automation_action(0.70, RiskLevel.MEDIUM) == ("manual_review", True)

    def test_medium_risk_low_confidence(self):
        assert get_automation_action(0.60, RiskLevel.MEDIUM) == ("low_confidence", True)


class TestGetAutomationActionLowRisk:
    def test_low_risk_auto_applies_at_90(self):
        assert get_automation_action(0.90, RiskLevel.LOW) == ("auto_apply", False)

    def test_low_risk_suggests(self):
        assert get_automation_action(0.85, RiskLevel.LOW) == ("suggest", True)

    def test_low_risk_low_confidence(self):
        assert get_automation_action(0.50, RiskLevel.LOW) == ("low_confidence", True)


class TestAdjustConfidenceFromFeedback:
    def test_insufficient_feedback_returns_base(self):
        total = MIN_FEEDBACK_FOR_CONFIDENCE_ADJUSTMENT - 1
        assert adjust_confidence_from_feedback(0.8, total, 0) == 0.8

    def test_high_success_rate_boosts_confidence(self):
        adjusted = adjust_confidence_from_feedback(0.80, 10, 0)
        assert adjusted > 0.80
        assert adjusted <= 1.0

    def test_low_success_rate_penalizes_confidence(self):
        adjusted = adjust_confidence_from_feedback(0.80, 0, 10)
        assert adjusted < 0.80
        assert adjusted >= 0.0

    def test_neutral_success_rate_leaves_confidence_unchanged(self):
        # 65% success is between the low (50%) and high (80%) bands.
        assert adjust_confidence_from_feedback(0.80, 13, 7) == 0.80

    def test_boost_is_clamped_to_one(self):
        assert adjust_confidence_from_feedback(0.99, 100, 0) == pytest.approx(1.0)
