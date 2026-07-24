"""
Tests for the health calculation utilities.

Covers calculate_combined_health, determine_health_status,
health_to_probability, assess_metric_health, and assess_metric_with_issue,
with emphasis on the threshold boundaries and both score directions.
"""

import pytest

from service.utils.enums import HealthStatus
from service.utils.health import (
    calculate_combined_health,
    determine_health_status,
    health_to_probability,
    assess_metric_health,
    assess_metric_with_issue,
)


class TestCalculateCombinedHealth:
    def test_empty_factors_default_to_fully_healthy(self):
        assert calculate_combined_health([]) == 1.0

    def test_averages_the_factors(self):
        assert calculate_combined_health([0.2, 0.8]) == pytest.approx(0.5)

    def test_single_factor_returns_itself(self):
        assert calculate_combined_health([0.73]) == pytest.approx(0.73)


class TestDetermineHealthStatus:
    @pytest.mark.parametrize(
        "score, expected",
        [
            (0.0, HealthStatus.CRITICAL),
            (0.39, HealthStatus.CRITICAL),
            (0.4, HealthStatus.WARNING),   # boundary: not < 0.4
            (0.59, HealthStatus.WARNING),
            (0.6, HealthStatus.DEGRADED),  # boundary: not < 0.6
            (0.79, HealthStatus.DEGRADED),
            (0.8, HealthStatus.HEALTHY),   # boundary: not < 0.8
            (1.0, HealthStatus.HEALTHY),
        ],
    )
    def test_thresholds(self, score, expected):
        assert determine_health_status(score) == expected


def test_health_to_probability_is_complement():
    assert health_to_probability(0.7) == pytest.approx(0.3)
    assert health_to_probability(1.0) == pytest.approx(0.0)


class TestAssessMetricHealth:
    def test_higher_is_worse_bands(self):
        # critical=90, warning=75, healthy=60
        assert assess_metric_health(95, 90, 75, 60) == 0.2
        assert assess_metric_health(80, 90, 75, 60) == 0.5
        assert assess_metric_health(65, 90, 75, 60) == 0.8
        assert assess_metric_health(50, 90, 75, 60) == 1.0

    def test_lower_is_worse_bands(self):
        # e.g. battery SOC: critical=10, warning=30, healthy=50
        assert assess_metric_health(5, 10, 30, 50, higher_is_worse=False) == 0.2
        assert assess_metric_health(25, 10, 30, 50, higher_is_worse=False) == 0.5
        assert assess_metric_health(45, 10, 30, 50, higher_is_worse=False) == 0.8
        assert assess_metric_health(80, 10, 30, 50, higher_is_worse=False) == 1.0


class TestAssessMetricWithIssue:
    def test_critical_value_reports_critical_issue(self):
        health, issue = assess_metric_with_issue(
            95, 90, 75, 60, "CPU", unit="%", higher_is_worse=True
        )
        assert health == 0.2
        assert issue == "Critical CPU: 95.0%"

    def test_warning_value_reports_high_issue_when_higher_is_worse(self):
        health, issue = assess_metric_with_issue(80, 90, 75, 60, "CPU", unit="%")
        assert health == 0.5
        assert issue == "High CPU: 80.0%"

    def test_warning_value_reports_low_issue_when_lower_is_worse(self):
        health, issue = assess_metric_with_issue(
            25, 10, 30, 50, "Battery", unit="%", higher_is_worse=False
        )
        assert health == 0.5
        assert issue == "Low Battery: 25.0%"

    def test_healthy_value_reports_no_issue(self):
        health, issue = assess_metric_with_issue(50, 90, 75, 60, "CPU")
        assert health == 1.0
        assert issue is None
