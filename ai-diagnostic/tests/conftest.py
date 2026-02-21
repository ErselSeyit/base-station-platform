"""
Shared fixtures for AI diagnostic tests.
"""

import pytest

from service.utils.threshold_client import reset_threshold_client


@pytest.fixture
def sample_problem():
    """A dict representing a diagnostic problem."""
    return {
        "id": "PRB-001",
        "station_id": "STATION-1",
        "category": "HARDWARE",
        "severity": "CRITICAL",
        "code": "TEMP_HIGH",
        "message": "Temperature exceeds safe operating limit",
        "metrics": {
            "temperature": 85.0,
            "cpu_usage": 92.5,
            "fan_speed": 1200,
        },
    }


@pytest.fixture
def sample_solution():
    """A dict representing a diagnostic solution."""
    return {
        "confidence": 0.92,
        "actions": [
            {"type": "parameter_change", "target": "fan_speed", "value": 3000},
            {"type": "service_restart", "target": "cooling_controller"},
        ],
        "root_cause": "Cooling system degradation causing thermal throttling",
    }


@pytest.fixture(autouse=True)
def reset_singletons():
    """Reset singleton instances between tests to ensure isolation."""
    reset_threshold_client()
    yield
    reset_threshold_client()
