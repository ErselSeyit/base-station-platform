"""
Validation utilities for Python-Java integration.

Validates requests and responses to ensure consistency between
the Python AI diagnostic service and Java backend.
"""

import logging
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from .confidence import (
    CONFIDENCE_AUTO_APPLY,
    CONFIDENCE_SUGGEST_APPLY,
    CONFIDENCE_MANUAL_REVIEW,
    CONFIDENCE_LOW,
    RiskLevel,
)
from .enums import HealthStatus, Severity

logger = logging.getLogger(__name__)


# ========================================
# VALID VALUES (must match Java enums)
# ========================================

VALID_ALERT_SEVERITIES = {"INFO", "WARNING", "CRITICAL"}
VALID_RISK_LEVELS = {"LOW", "MEDIUM", "HIGH"}
VALID_DIAGNOSTIC_STATUSES = {
    "DETECTED", "DIAGNOSED", "APPLIED", "PENDING_CONFIRMATION",
    "RESOLVED", "FAILED", "CANCELLED"
}
VALID_HEALTH_STATUSES = {"healthy", "degraded", "warning", "critical", "failed"}


@dataclass
class ValidationResult:
    """Result of a validation check."""
    valid: bool
    errors: List[str]
    warnings: List[str]

    @classmethod
    def success(cls) -> "ValidationResult":
        return cls(valid=True, errors=[], warnings=[])

    @classmethod
    def failure(cls, errors: List[str], warnings: Optional[List[str]] = None) -> "ValidationResult":
        return cls(valid=False, errors=errors, warnings=warnings or [])


def validate_diagnostic_request(request: Dict[str, Any]) -> ValidationResult:
    """
    Validate a diagnostic request from Java backend.

    Expected fields:
    - station_id: str (required)
    - problem_id: str (required, format: PRB-*)
    - alert_type: str (required)
    - severity: str (required, one of INFO/WARNING/CRITICAL)
    - metrics: dict (optional)

    Args:
        request: Request dictionary from Java

    Returns:
        ValidationResult with errors/warnings
    """
    errors = []
    warnings = []

    # Required fields
    if not request.get("station_id"):
        errors.append("Missing required field: station_id")

    if not request.get("problem_id"):
        errors.append("Missing required field: problem_id")
    elif not request["problem_id"].startswith("PRB-"):
        warnings.append(f"problem_id should start with 'PRB-': {request['problem_id']}")

    if not request.get("alert_type"):
        errors.append("Missing required field: alert_type")

    severity = request.get("severity")
    if not severity:
        errors.append("Missing required field: severity")
    elif severity.upper() not in VALID_ALERT_SEVERITIES:
        errors.append(f"Invalid severity: {severity}. Must be one of {VALID_ALERT_SEVERITIES}")

    # Validate metrics if present
    metrics = request.get("metrics", {})
    if metrics:
        for key, value in metrics.items():
            if not isinstance(value, (int, float)):
                warnings.append(f"Metric '{key}' has non-numeric value: {value}")

    if errors:
        return ValidationResult.failure(errors, warnings)
    return ValidationResult(valid=True, errors=[], warnings=warnings)


def validate_diagnostic_response(response: Dict[str, Any]) -> ValidationResult:
    """
    Validate a diagnostic response before sending to Java backend.

    Expected fields:
    - problem_id: str (required)
    - diagnosis: str (required)
    - confidence: float (required, 0.0-1.0)
    - risk_level: str (required, one of LOW/MEDIUM/HIGH)
    - solution: dict (optional)

    Args:
        response: Response dictionary to send to Java

    Returns:
        ValidationResult with errors/warnings
    """
    errors = []
    warnings = []

    # Required fields
    if not response.get("problem_id"):
        errors.append("Missing required field: problem_id")

    if not response.get("diagnosis"):
        errors.append("Missing required field: diagnosis")

    confidence = response.get("confidence")
    if confidence is None:
        errors.append("Missing required field: confidence")
    elif not isinstance(confidence, (int, float)):
        errors.append(f"Confidence must be numeric: {confidence}")
    elif not 0.0 <= confidence <= 1.0:
        errors.append(f"Confidence must be between 0.0 and 1.0: {confidence}")
    else:
        # Check confidence threshold alignment
        if confidence >= CONFIDENCE_AUTO_APPLY:
            pass  # OK for auto-apply
        elif confidence < CONFIDENCE_LOW:
            warnings.append(f"Low confidence ({confidence:.2f}) may result in manual review")

    risk_level = response.get("risk_level")
    if not risk_level:
        errors.append("Missing required field: risk_level")
    elif risk_level.upper() not in VALID_RISK_LEVELS:
        errors.append(f"Invalid risk_level: {risk_level}. Must be one of {VALID_RISK_LEVELS}")

    # Validate solution if present
    solution = response.get("solution")
    if solution:
        if not solution.get("action"):
            warnings.append("Solution missing 'action' field")
        if not solution.get("description"):
            warnings.append("Solution missing 'description' field")

    if errors:
        return ValidationResult.failure(errors, warnings)
    return ValidationResult(valid=True, errors=[], warnings=warnings)


def validate_health_status(status: str) -> bool:
    """Check if health status is valid."""
    return status.lower() in VALID_HEALTH_STATUSES


def validate_risk_level(risk_level: str) -> bool:
    """Check if risk level is valid."""
    return risk_level.upper() in VALID_RISK_LEVELS


def normalize_severity(severity: str) -> str:
    """
    Normalize severity string to match Java enum.

    Python uses lowercase, Java uses uppercase.
    """
    mapping = {
        "critical": "CRITICAL",
        "high": "CRITICAL",  # Map Python HIGH to Java CRITICAL
        "warning": "WARNING",
        "medium": "WARNING",  # Map Python MEDIUM to Java WARNING
        "low": "INFO",
        "info": "INFO",
    }
    return mapping.get(severity.lower(), "INFO")


def normalize_health_status(status: str) -> str:
    """
    Normalize health status string.

    Ensures lowercase for consistency with Python enums.
    """
    return status.lower() if status else "healthy"


def log_validation_result(result: ValidationResult, context: str) -> None:
    """Log validation result with appropriate severity."""
    if not result.valid:
        logger.error(f"Validation failed for {context}: {result.errors}")
    if result.warnings:
        logger.warning(f"Validation warnings for {context}: {result.warnings}")
