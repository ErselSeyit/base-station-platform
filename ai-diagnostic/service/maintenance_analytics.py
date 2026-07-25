"""
Pure predictive-maintenance analytics.

Statistics and threshold logic with no I/O or state: trend/regression over a
metric series, fan failure probability and status, prediction confidence, and
the per-component recommendation text. Extracted from
PredictiveMaintenanceService so the numeric logic can be unit-tested directly;
the service delegates to these functions.
"""

import statistics
from datetime import timedelta
from typing import List, Optional, Tuple

import numpy as np

from .maintenance_models import MetricDataPoint, TrendAnalysis
from .utils import HealthStatus, PredictionConfidence
from .utils.thresholds import BatteryThresholds, FanThresholds

# Coefficient-of-variation above which a series is called "erratic".
_ERRATIC_CV = 0.2
# Absolute slope below which a series is called "stable" (units/hour).
_STABLE_SLOPE = 0.1


def analyze_trend(data_points: List[MetricDataPoint]) -> TrendAnalysis:
    """Perform trend analysis (linear regression) on a metric series."""
    if not data_points:
        return TrendAnalysis(
            direction="unknown", slope=0, r_squared=0,
            mean=0, std_dev=0, min_value=0, max_value=0, data_points=0
        )

    values = [dp.value for dp in data_points]
    timestamps = [(dp.timestamp - data_points[0].timestamp).total_seconds() / 3600
                  for dp in data_points]  # Hours since first point

    mean_val = statistics.mean(values)
    std_dev = statistics.stdev(values) if len(values) > 1 else 0

    # Linear regression for trend
    if len(values) > 1:
        coeffs = np.polyfit(timestamps, values, 1)
        slope = coeffs[0]

        # Calculate R-squared
        predicted = np.polyval(coeffs, timestamps)
        ss_res = np.sum((np.array(values) - predicted) ** 2)
        ss_tot = np.sum((np.array(values) - mean_val) ** 2)
        r_squared = 1 - (ss_res / ss_tot) if ss_tot > 0 else 0
    else:
        slope = 0
        r_squared = 0

    # Determine direction
    cv = std_dev / mean_val if mean_val > 0 else 0
    if cv > _ERRATIC_CV:
        direction = "erratic"
    elif abs(slope) < _STABLE_SLOPE:
        direction = "stable"
    elif slope > 0:
        direction = "increasing"
    else:
        direction = "decreasing"

    return TrendAnalysis(
        direction=direction,
        slope=slope,
        r_squared=float(max(0, min(1, r_squared))),
        mean=mean_val,
        std_dev=std_dev,
        min_value=min(values),
        max_value=max(values),
        data_points=len(values)
    )


def assess_fan_health_status(current_rpm: float, trend: TrendAnalysis) -> HealthStatus:
    """Assess fan health status based on RPM and trend."""
    if current_rpm < FanThresholds.CRITICAL_RPM_MIN:
        return HealthStatus.CRITICAL
    elif current_rpm < FanThresholds.WARNING_RPM_MIN:
        return HealthStatus.WARNING
    elif current_rpm < FanThresholds.HEALTHY_RPM_MIN:
        return HealthStatus.DEGRADED
    elif trend.direction == "erratic":
        return HealthStatus.WARNING
    elif trend.direction == "decreasing" and trend.slope < FanThresholds.DEGRADATION_SLOPE:
        return HealthStatus.DEGRADED
    else:
        return HealthStatus.HEALTHY


def calculate_fan_failure_probability(
    current_rpm: float,
    trend: TrendAnalysis
) -> Tuple[float, Optional[timedelta]]:
    """Calculate probability of fan failure and estimated time to failure."""
    probability = 0.0
    ttf = None

    # Base probability from current RPM
    if current_rpm < FanThresholds.CRITICAL_RPM_MIN:
        probability = 0.9
    elif current_rpm < FanThresholds.WARNING_RPM_MIN:
        probability = 0.6
    elif current_rpm < FanThresholds.HEALTHY_RPM_MIN:
        probability = 0.3

    # Adjust for trend
    if trend.direction == "decreasing" and trend.slope < 0:
        # Calculate hours until critical RPM
        hours_to_critical = (current_rpm - FanThresholds.CRITICAL_RPM_MIN) / abs(trend.slope)
        if hours_to_critical > 0:
            ttf = timedelta(hours=hours_to_critical)
            # Higher probability if failure imminent
            if hours_to_critical < 24:
                probability = max(probability, 0.8)
            elif hours_to_critical < 72:
                probability = max(probability, 0.5)

    # Adjust for erratic behavior
    if trend.direction == "erratic":
        probability = max(probability, 0.5)

    # High variation is concerning
    cv = trend.std_dev / trend.mean if trend.mean > 0 else 0
    if cv > FanThresholds.VARIATION_THRESHOLD:
        probability = max(probability, 0.4)

    return min(1.0, probability), ttf


def determine_confidence(
    data_points: int,
    r_squared: float,
    min_data_points: int,
    preferred_data_points: int
) -> PredictionConfidence:
    """Determine prediction confidence based on data quality."""
    if data_points >= preferred_data_points and r_squared > 0.7:
        return PredictionConfidence.HIGH
    elif data_points >= min_data_points and r_squared > 0.4:
        return PredictionConfidence.MEDIUM
    else:
        return PredictionConfidence.LOW


def fan_prediction_text(
    status: HealthStatus,
    trend: TrendAnalysis,
    _probability: float  # Reserved for future use
) -> str:
    """Generate human-readable prediction text for fan status."""
    if status == HealthStatus.CRITICAL:
        return f"Fan operating at critical RPM ({trend.mean:.0f}), immediate attention required"
    elif status == HealthStatus.WARNING:
        return f"Fan degradation detected, RPM trend: {trend.direction} ({trend.slope:.1f}/hr)"
    elif trend.direction == "decreasing":
        return f"Fan RPM declining at {abs(trend.slope):.1f}/hr, monitor closely"
    elif trend.direction == "erratic":
        return f"Erratic fan behavior detected (±{trend.std_dev:.0f} RPM variation)"
    else:
        return f"Fan operating normally at {trend.mean:.0f} RPM"


def fan_recommendation(
    status: HealthStatus,
    _trend: TrendAnalysis,  # Reserved for trend-based recommendations
    probability: float
) -> str:
    """Get recommended action for fan issues."""
    if status == HealthStatus.CRITICAL or probability > 0.8:
        return "URGENT: Schedule immediate fan replacement to prevent thermal shutdown"
    elif status == HealthStatus.WARNING or probability > 0.5:
        return "Schedule fan replacement within 1 week"
    elif status == HealthStatus.DEGRADED:
        return "Monitor fan closely, schedule inspection within 2 weeks"
    else:
        return "Continue normal monitoring"


def temperature_recommendation(status: HealthStatus, trend: TrendAnalysis) -> str:
    """Get recommended action for temperature issues."""
    if status == HealthStatus.CRITICAL:
        return "CRITICAL: Temperature exceeds safe limits. Check cooling system immediately"
    elif status == HealthStatus.WARNING:
        return "High temperature warning. Verify cooling system operation"
    elif trend.direction == "increasing" and trend.slope > 0.5:
        return "Rising temperature trend. Check airflow and cooling capacity"
    else:
        return "Temperature within normal range"


def battery_recommendation(
    status: HealthStatus,
    _issues: List[str],  # Reserved for issue-specific recommendations
    cycle_count: float
) -> str:
    """Get recommended action for battery issues."""
    if status == HealthStatus.CRITICAL:
        return "URGENT: Battery replacement required. Risk of power failure"
    elif status == HealthStatus.WARNING:
        if cycle_count > BatteryThresholds.CYCLE_WARNING_MAX:
            return "Schedule battery replacement within 30 days due to cycle degradation"
        return "Monitor battery closely. Schedule inspection within 1 week"
    elif status == HealthStatus.DEGRADED:
        return "Battery showing early degradation. Plan for replacement within 3 months"
    else:
        return "Battery operating normally. Continue standard maintenance"


def fiber_recommendation(
    status: HealthStatus,
    has_ber_issue: bool,
    rx_trend: TrendAnalysis
) -> str:
    """Get recommended action for fiber transport issues."""
    if status == HealthStatus.CRITICAL:
        return "URGENT: Fiber link at risk of failure. Inspect connectors, check for fiber damage"
    elif status == HealthStatus.WARNING:
        if has_ber_issue:
            return "High error rate detected. Clean connectors, verify SFP modules"
        return "Signal degradation detected. Schedule OTDR test within 1 week"
    elif status == HealthStatus.DEGRADED:
        if rx_trend.direction == "decreasing":
            return "Gradual signal loss. Check for connector contamination or bend loss"
        return "Monitor fiber link. Schedule preventive inspection"
    else:
        return "Fiber transport operating normally"
