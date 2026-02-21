"""
Tests for the validation utilities.

Covers validate_diagnostic_request, validate_diagnostic_response,
validate_health_status, validate_risk_level, normalize_severity,
normalize_health_status, and the ValidationResult dataclass.
"""

import pytest

from service.utils.validation import (
    ValidationResult,
    validate_diagnostic_request,
    validate_diagnostic_response,
    validate_health_status,
    validate_risk_level,
    normalize_severity,
    normalize_health_status,
)


# ---- ValidationResult tests ----

class TestValidationResult:

    def test_success_factory(self):
        result = ValidationResult.success()
        assert result.valid is True
        assert result.errors == []
        assert result.warnings == []

    def test_failure_factory(self):
        result = ValidationResult.failure(
            errors=["missing field"],
            warnings=["non-standard format"],
        )
        assert result.valid is False
        assert result.errors == ["missing field"]
        assert result.warnings == ["non-standard format"]

    def test_failure_factory_no_warnings(self):
        result = ValidationResult.failure(errors=["err"])
        assert result.valid is False
        assert result.warnings == []


# ---- validate_diagnostic_request tests ----

class TestValidateDiagnosticRequest:

    def test_valid_request(self):
        request = {
            "station_id": "STATION-1",
            "problem_id": "PRB-001",
            "alert_type": "HIGH_TEMP",
            "severity": "CRITICAL",
            "metrics": {"temperature": 85.0},
        }
        result = validate_diagnostic_request(request)
        assert result.valid is True
        assert result.errors == []

    def test_missing_station_id(self):
        request = {
            "problem_id": "PRB-001",
            "alert_type": "HIGH_TEMP",
            "severity": "WARNING",
        }
        result = validate_diagnostic_request(request)
        assert result.valid is False
        assert any("station_id" in e for e in result.errors)

    def test_missing_problem_id(self):
        request = {
            "station_id": "S1",
            "alert_type": "HIGH_TEMP",
            "severity": "INFO",
        }
        result = validate_diagnostic_request(request)
        assert result.valid is False
        assert any("problem_id" in e for e in result.errors)

    def test_missing_alert_type(self):
        request = {
            "station_id": "S1",
            "problem_id": "PRB-002",
            "severity": "INFO",
        }
        result = validate_diagnostic_request(request)
        assert result.valid is False
        assert any("alert_type" in e for e in result.errors)

    def test_missing_severity(self):
        request = {
            "station_id": "S1",
            "problem_id": "PRB-003",
            "alert_type": "LOW_SIGNAL",
        }
        result = validate_diagnostic_request(request)
        assert result.valid is False
        assert any("severity" in e for e in result.errors)

    def test_invalid_severity(self):
        request = {
            "station_id": "S1",
            "problem_id": "PRB-004",
            "alert_type": "HIGH_TEMP",
            "severity": "EXTREME",
        }
        result = validate_diagnostic_request(request)
        assert result.valid is False
        assert any("Invalid severity" in e for e in result.errors)

    def test_problem_id_format_warning(self):
        request = {
            "station_id": "S1",
            "problem_id": "ISSUE-100",
            "alert_type": "HIGH_TEMP",
            "severity": "WARNING",
        }
        result = validate_diagnostic_request(request)
        # Non-PRB prefix is a warning, not an error
        assert result.valid is True
        assert any("PRB-" in w for w in result.warnings)

    def test_non_numeric_metric_warning(self):
        request = {
            "station_id": "S1",
            "problem_id": "PRB-005",
            "alert_type": "HIGH_TEMP",
            "severity": "INFO",
            "metrics": {"status": "active"},
        }
        result = validate_diagnostic_request(request)
        assert result.valid is True
        assert any("non-numeric" in w for w in result.warnings)

    def test_multiple_missing_fields(self):
        result = validate_diagnostic_request({})
        assert result.valid is False
        assert len(result.errors) >= 4


# ---- validate_diagnostic_response tests ----

class TestValidateDiagnosticResponse:

    def test_valid_response(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Cooling system failure",
            "confidence": 0.92,
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is True

    def test_missing_problem_id(self):
        response = {
            "diagnosis": "Test",
            "confidence": 0.9,
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("problem_id" in e for e in result.errors)

    def test_missing_diagnosis(self):
        response = {
            "problem_id": "PRB-001",
            "confidence": 0.9,
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("diagnosis" in e for e in result.errors)

    def test_missing_confidence(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("confidence" in e for e in result.errors)

    def test_confidence_out_of_range_high(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": 1.5,
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("between 0.0 and 1.0" in e for e in result.errors)

    def test_confidence_out_of_range_negative(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": -0.1,
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False

    def test_confidence_non_numeric(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": "high",
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("numeric" in e for e in result.errors)

    def test_invalid_risk_level(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": 0.9,
            "risk_level": "EXTREME",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("Invalid risk_level" in e for e in result.errors)

    def test_missing_risk_level(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": 0.9,
        }
        result = validate_diagnostic_response(response)
        assert result.valid is False
        assert any("risk_level" in e for e in result.errors)

    def test_low_confidence_warning(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": 0.55,
            "risk_level": "LOW",
        }
        result = validate_diagnostic_response(response)
        assert result.valid is True
        assert any("Low confidence" in w for w in result.warnings)

    def test_solution_missing_action_warning(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": 0.95,
            "risk_level": "LOW",
            "solution": {"description": "Do something"},
        }
        result = validate_diagnostic_response(response)
        assert result.valid is True
        assert any("action" in w for w in result.warnings)

    def test_solution_missing_description_warning(self):
        response = {
            "problem_id": "PRB-001",
            "diagnosis": "Test",
            "confidence": 0.95,
            "risk_level": "LOW",
            "solution": {"action": "restart"},
        }
        result = validate_diagnostic_response(response)
        assert result.valid is True
        assert any("description" in w for w in result.warnings)


# ---- Simple validation function tests ----

class TestValidateHealthStatus:

    def test_valid_statuses(self):
        for status in ["healthy", "degraded", "warning", "critical", "failed"]:
            assert validate_health_status(status) is True

    def test_case_insensitive(self):
        assert validate_health_status("HEALTHY") is True
        assert validate_health_status("Critical") is True

    def test_invalid_status(self):
        assert validate_health_status("unknown") is False
        assert validate_health_status("") is False


class TestValidateRiskLevel:

    def test_valid_risk_levels(self):
        for level in ["LOW", "MEDIUM", "HIGH"]:
            assert validate_risk_level(level) is True

    def test_case_insensitive(self):
        assert validate_risk_level("low") is True
        assert validate_risk_level("Medium") is True

    def test_invalid_risk_level(self):
        assert validate_risk_level("CRITICAL") is False
        assert validate_risk_level("EXTREME") is False


class TestNormalizeSeverity:

    def test_standard_mappings(self):
        assert normalize_severity("critical") == "CRITICAL"
        assert normalize_severity("high") == "CRITICAL"
        assert normalize_severity("warning") == "WARNING"
        assert normalize_severity("medium") == "WARNING"
        assert normalize_severity("low") == "INFO"
        assert normalize_severity("info") == "INFO"

    def test_case_insensitive(self):
        assert normalize_severity("CRITICAL") == "CRITICAL"
        assert normalize_severity("Warning") == "WARNING"

    def test_unknown_defaults_to_info(self):
        assert normalize_severity("unknown") == "INFO"


class TestNormalizeHealthStatus:

    def test_normalizes_to_lowercase(self):
        assert normalize_health_status("HEALTHY") == "healthy"
        assert normalize_health_status("Critical") == "critical"

    def test_already_lowercase(self):
        assert normalize_health_status("degraded") == "degraded"

    def test_empty_defaults_to_healthy(self):
        assert normalize_health_status("") == "healthy"

    def test_none_defaults_to_healthy(self):
        assert normalize_health_status(None) == "healthy"
