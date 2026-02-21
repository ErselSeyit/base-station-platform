"""
AI Diagnostic Service Utilities

Shared enums, constants, and utility functions.
"""

from .enums import HealthStatus, PredictionConfidence, Severity
from .health import (
    calculate_combined_health,
    determine_health_status,
    health_to_probability,
    assess_metric_with_issue,
)
from .thresholds import (
    FanThresholds,
    TemperatureThresholds,
    BatteryThresholds,
    FiberThresholds,
    SignalThresholds,
)
from .rng import get_rng, reset_rng
from .singleton import SingletonMeta, singleton_factory, clear_singleton
from .serialization import serialize_value, dataclass_to_dict, SerializableMixin
from .confidence import (
    CONFIDENCE_AUTO_APPLY,
    CONFIDENCE_SUGGEST_APPLY,
    CONFIDENCE_MANUAL_REVIEW,
    CONFIDENCE_LOW,
    CONFIDENCE_AUTO_APPLY_LOW_RISK,
    CONFIDENCE_AUTO_APPLY_MEDIUM_RISK,
    RiskLevel,
    get_automation_action,
    adjust_confidence_from_feedback,
)
from .validation import (
    ValidationResult,
    validate_diagnostic_request,
    validate_diagnostic_response,
    validate_health_status,
    validate_risk_level,
    normalize_severity,
    normalize_health_status,
)
from .threshold_client import (
    ThresholdConfigClient,
    get_threshold_client,
    reset_threshold_client,
)

__all__ = [
    # Enums
    "HealthStatus",
    "PredictionConfidence",
    "Severity",
    # Health utilities
    "calculate_combined_health",
    "determine_health_status",
    "health_to_probability",
    "assess_metric_with_issue",
    # Threshold classes
    "FanThresholds",
    "TemperatureThresholds",
    "BatteryThresholds",
    "FiberThresholds",
    "SignalThresholds",
    # RNG utilities
    "get_rng",
    "reset_rng",
    # Singleton utilities
    "SingletonMeta",
    "singleton_factory",
    "clear_singleton",
    # Serialization utilities
    "serialize_value",
    "dataclass_to_dict",
    "SerializableMixin",
    # Confidence utilities
    "CONFIDENCE_AUTO_APPLY",
    "CONFIDENCE_SUGGEST_APPLY",
    "CONFIDENCE_MANUAL_REVIEW",
    "CONFIDENCE_LOW",
    "CONFIDENCE_AUTO_APPLY_LOW_RISK",
    "CONFIDENCE_AUTO_APPLY_MEDIUM_RISK",
    "RiskLevel",
    "get_automation_action",
    "adjust_confidence_from_feedback",
    # Validation utilities
    "ValidationResult",
    "validate_diagnostic_request",
    "validate_diagnostic_response",
    "validate_health_status",
    "validate_risk_level",
    "normalize_severity",
    "normalize_health_status",
    # Threshold config client
    "ThresholdConfigClient",
    "get_threshold_client",
    "reset_threshold_client",
]
