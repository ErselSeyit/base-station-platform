"""
Shared enums for AI diagnostic services.
"""

from enum import Enum


class HealthStatus(Enum):
    """Component health status levels."""
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    WARNING = "warning"
    CRITICAL = "critical"
    FAILED = "failed"


class PredictionConfidence(Enum):
    """Confidence level of predictions."""
    HIGH = "high"        # >80% confidence
    MEDIUM = "medium"    # 50-80% confidence
    LOW = "low"          # <50% confidence


class Severity(Enum):
    """Generic severity levels used across services."""
    CRITICAL = "critical"
    HIGH = "high"
    MEDIUM = "medium"
    LOW = "low"
    INFO = "info"
