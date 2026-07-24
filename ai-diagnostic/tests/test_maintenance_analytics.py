"""
Characterisation tests for the pure predictive-maintenance analytics extracted
from PredictiveMaintenanceService (trend/regression, fan failure probability and
status, prediction confidence, recommendation text).

The service had no coverage over this numeric logic; these tests lock it so the
thresholds and regression can be tuned without silently changing what the
platform predicts.
"""

from datetime import datetime, timedelta, timezone

import pytest

from service.maintenance_analytics import (
    analyze_trend,
    assess_fan_health_status,
    calculate_fan_failure_probability,
    determine_confidence,
    fan_recommendation,
    temperature_recommendation,
)
from service.maintenance_models import MetricDataPoint, TrendAnalysis
from service.utils import HealthStatus, PredictionConfidence


def _series(values, metric="fan_speed"):
    base = datetime(2026, 1, 1, tzinfo=timezone.utc)
    return [
        MetricDataPoint(base + timedelta(hours=i), v, "S1", metric)
        for i, v in enumerate(values)
    ]


class TestAnalyzeTrend:
    def test_empty_series_is_unknown(self):
        t = analyze_trend([])
        assert t.direction == "unknown"
        assert t.data_points == 0

    def test_steady_decline_is_decreasing_with_negative_slope(self):
        t = analyze_trend(_series([1000 - 10 * i for i in range(20)]))
        assert t.direction == "decreasing"
        assert t.slope == pytest.approx(-10.0, abs=1e-6)
        assert t.r_squared == pytest.approx(1.0, abs=1e-6)
        assert t.data_points == 20

    def test_flat_series_is_stable(self):
        t = analyze_trend(_series([3000] * 15))
        assert t.direction == "stable"
        assert t.slope == pytest.approx(0.0, abs=1e-9)

    def test_high_variation_is_erratic(self):
        # Large swings -> coefficient of variation over the erratic threshold.
        t = analyze_trend(_series([100, 4000, 200, 3800, 150, 4200, 300, 3600]))
        assert t.direction == "erratic"

    def test_r_squared_is_clamped_to_unit_interval(self):
        t = analyze_trend(_series([1000 + 5 * i for i in range(12)]))
        assert 0.0 <= t.r_squared <= 1.0


class TestFanFailureProbability:
    def test_critical_rpm_gives_high_probability(self):
        trend = analyze_trend(_series([500] * 12))  # well below any healthy floor
        prob, _ttf = calculate_fan_failure_probability(500, trend)
        assert prob >= 0.9

    def test_declining_toward_critical_estimates_time_to_failure(self):
        trend = analyze_trend(_series([3000 - 50 * i for i in range(20)]))
        prob, ttf = calculate_fan_failure_probability(2050, trend)
        assert ttf is not None
        assert ttf.total_seconds() > 0
        assert 0.0 <= prob <= 1.0


class TestFanHealthStatus:
    def test_healthy_when_rpm_high_and_trend_stable(self):
        trend = analyze_trend(_series([3000] * 12))
        assert assess_fan_health_status(3000, trend) == HealthStatus.HEALTHY

    def test_critical_when_rpm_below_floor(self):
        trend = analyze_trend(_series([500] * 12))
        assert assess_fan_health_status(500, trend) == HealthStatus.CRITICAL


class TestDetermineConfidence:
    def test_high_needs_enough_points_and_strong_fit(self):
        assert determine_confidence(120, 0.8, 10, 100) == PredictionConfidence.HIGH

    def test_medium_with_moderate_data_and_fit(self):
        assert determine_confidence(20, 0.5, 10, 100) == PredictionConfidence.MEDIUM

    def test_low_with_sparse_data(self):
        assert determine_confidence(3, 0.9, 10, 100) == PredictionConfidence.LOW


class TestRecommendations:
    def test_critical_fan_is_urgent(self):
        trend = analyze_trend(_series([500] * 12))
        assert fan_recommendation(HealthStatus.CRITICAL, trend, 0.95).startswith("URGENT")

    def test_healthy_temperature_is_normal(self):
        trend = analyze_trend(_series([25.0] * 12))
        assert "normal" in temperature_recommendation(HealthStatus.HEALTHY, trend).lower()
