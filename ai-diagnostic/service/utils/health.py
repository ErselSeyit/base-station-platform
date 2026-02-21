"""
Health calculation utilities.

Provides common functions for calculating and determining health status
from health factors across diagnostic services.
"""

from typing import List, Optional, Tuple

from .enums import HealthStatus


# Health status thresholds (combined health score -> status)
CRITICAL_THRESHOLD = 0.4
WARNING_THRESHOLD = 0.6
DEGRADED_THRESHOLD = 0.8


def calculate_combined_health(health_factors: List[float]) -> float:
    """
    Calculate combined health score from individual health factors.

    Args:
        health_factors: List of health scores (0.0-1.0, where 1.0 is healthy)

    Returns:
        Combined health score (0.0-1.0)
    """
    if not health_factors:
        return 1.0
    return sum(health_factors) / len(health_factors)


def determine_health_status(combined_health: float) -> HealthStatus:
    """
    Determine health status from combined health score.

    Args:
        combined_health: Combined health score (0.0-1.0)

    Returns:
        HealthStatus enum value

    Thresholds:
        < 0.4: CRITICAL
        < 0.6: WARNING
        < 0.8: DEGRADED
        >= 0.8: HEALTHY
    """
    if combined_health < CRITICAL_THRESHOLD:
        return HealthStatus.CRITICAL
    elif combined_health < WARNING_THRESHOLD:
        return HealthStatus.WARNING
    elif combined_health < DEGRADED_THRESHOLD:
        return HealthStatus.DEGRADED
    return HealthStatus.HEALTHY


def health_to_probability(combined_health: float) -> float:
    """
    Convert health score to failure probability.

    Args:
        combined_health: Combined health score (0.0-1.0)

    Returns:
        Probability of failure (0.0-1.0)
    """
    return 1.0 - combined_health


def assess_metric_with_issue(
    value: float,
    critical_threshold: float,
    warning_threshold: float,
    healthy_threshold: float,
    metric_name: str,
    unit: str = "",
    higher_is_worse: bool = True,
) -> Tuple[float, Optional[str]]:
    """
    Assess health and generate issue message for a metric.

    Args:
        value: Current metric value
        critical_threshold: Threshold for critical status
        warning_threshold: Threshold for warning status
        healthy_threshold: Threshold for healthy status
        metric_name: Human-readable metric name for issue messages
        unit: Unit suffix for value display (e.g., "%", "°C")
        higher_is_worse: If True, higher values = worse health

    Returns:
        Tuple of (health_factor, issue_message or None)
    """
    health = assess_metric_health(
        value, critical_threshold, warning_threshold, healthy_threshold, higher_is_worse
    )

    issue = None
    if health <= 0.2:
        issue = f"Critical {metric_name}: {value:.1f}{unit}"
    elif health <= 0.5:
        severity = "High" if higher_is_worse else "Low"
        issue = f"{severity} {metric_name}: {value:.1f}{unit}"

    return health, issue


def assess_metric_health(
    value: float,
    critical_threshold: float,
    warning_threshold: float,
    healthy_threshold: float,
    higher_is_worse: bool = True,
) -> float:
    """
    Assess health factor for a single metric.

    Args:
        value: Current metric value
        critical_threshold: Threshold for critical status
        warning_threshold: Threshold for warning status
        healthy_threshold: Threshold for healthy status
        higher_is_worse: If True, higher values indicate worse health

    Returns:
        Health factor (0.0-1.0, where 1.0 is healthy)
    """
    if higher_is_worse:
        if value >= critical_threshold:
            return 0.2
        elif value >= warning_threshold:
            return 0.5
        elif value >= healthy_threshold:
            return 0.8
        return 1.0
    else:
        # Lower is worse (e.g., battery SOC, signal strength)
        if value <= critical_threshold:
            return 0.2
        elif value <= warning_threshold:
            return 0.5
        elif value <= healthy_threshold:
            return 0.8
        return 1.0
