"""
Tests for the ThresholdConfigClient.

Covers default thresholds, caching, cache invalidation,
and the singleton get/reset functions.
"""

import time
from unittest.mock import patch, MagicMock

import pytest

from service.utils.threshold_client import (
    DEFAULT_CONFIDENCE_THRESHOLDS,
    DEFAULT_HEALTH_THRESHOLDS,
    DEFAULT_LEARNING_THRESHOLDS,
    ThresholdConfigClient,
    get_threshold_client,
    reset_threshold_client,
)


# ---- Default threshold tests ----

class TestDefaultThresholds:

    def test_default_health_thresholds(self):
        """When API is unavailable, health thresholds should return defaults."""
        client = ThresholdConfigClient()
        # Force HTTP unavailable
        client._http_available = False
        thresholds = client.get_health_thresholds()
        assert thresholds["critical"] == 0.4
        assert thresholds["warning"] == 0.6
        assert thresholds["degraded"] == 0.8
        assert thresholds == DEFAULT_HEALTH_THRESHOLDS

    def test_default_confidence_thresholds(self):
        """When API is unavailable, confidence thresholds should return defaults."""
        client = ThresholdConfigClient()
        client._http_available = False
        thresholds = client.get_confidence_thresholds()
        assert thresholds["auto_apply"] == 0.95
        assert thresholds["suggest_apply"] == 0.85
        assert thresholds["manual_review"] == 0.70
        assert thresholds["low"] == 0.70
        assert thresholds["auto_apply_low_risk"] == 0.90
        assert thresholds["auto_apply_medium_risk"] == 0.95
        assert thresholds == DEFAULT_CONFIDENCE_THRESHOLDS

    def test_default_learning_thresholds(self):
        """When API is unavailable, learning thresholds should return defaults."""
        client = ThresholdConfigClient()
        client._http_available = False
        thresholds = client.get_learning_thresholds()
        assert thresholds["high_success_rate"] == 0.80
        assert thresholds["low_success_rate"] == 0.50
        assert thresholds["max_confidence_boost"] == 0.10
        assert thresholds["max_confidence_penalty"] == 0.20
        assert thresholds["min_feedback_for_adjustment"] == 5.0
        assert thresholds == DEFAULT_LEARNING_THRESHOLDS


# ---- Caching tests ----

class TestThresholdClientCaching:

    def test_cache_is_populated_after_first_call(self):
        client = ThresholdConfigClient()
        client._http_available = False
        client.get_all_thresholds()
        assert client._cache != {}
        assert client._cache_time > 0

    def test_cache_is_reused_within_ttl(self):
        client = ThresholdConfigClient(cache_ttl=300.0)
        client._http_available = False
        # First call populates cache
        first = client.get_all_thresholds()
        cache_time_first = client._cache_time

        # Second call should reuse cache
        second = client.get_all_thresholds()
        assert second == first
        assert client._cache_time == cache_time_first

    def test_cache_invalidation(self):
        client = ThresholdConfigClient()
        client._http_available = False
        client.get_all_thresholds()
        assert client._cache != {}

        client.invalidate_cache()
        assert client._cache == {}
        assert client._cache_time == 0

    def test_refresh_invalidates_and_reloads(self):
        client = ThresholdConfigClient()
        client._http_available = False
        client.get_all_thresholds()
        old_time = client._cache_time

        # Small delay to get a different timestamp
        client.invalidate_cache()
        result = client.refresh()

        assert result is not None
        assert "health" in result
        assert "confidence" in result
        assert "learning" in result


# ---- API fetch tests ----

class TestThresholdClientFetch:

    def test_successful_api_fetch(self):
        client = ThresholdConfigClient(base_url="http://test:8082")
        mock_requests = MagicMock()
        mock_response = MagicMock()
        mock_response.raise_for_status.return_value = None
        mock_response.json.return_value = {
            "health": {"critical": 0.3, "warning": 0.5, "degraded": 0.7},
            "confidence": {"auto_apply": 0.98},
            "learning": {"high_success_rate": 0.85},
        }
        mock_requests.get.return_value = mock_response
        client._requests = mock_requests
        client._http_available = True

        thresholds = client.get_all_thresholds()
        assert thresholds["health"]["critical"] == 0.3
        mock_requests.get.assert_called_once_with(
            "http://test:8082/api/v1/thresholds", timeout=5
        )

    def test_api_failure_falls_back_to_defaults(self):
        client = ThresholdConfigClient(base_url="http://unreachable:9999")
        mock_requests = MagicMock()
        mock_requests.get.side_effect = Exception("Connection refused")
        client._requests = mock_requests
        client._http_available = True

        thresholds = client.get_all_thresholds()
        assert thresholds["health"] == DEFAULT_HEALTH_THRESHOLDS
        assert thresholds["confidence"] == DEFAULT_CONFIDENCE_THRESHOLDS

    def test_get_equipment_thresholds_empty_when_not_configured(self):
        client = ThresholdConfigClient()
        client._http_available = False
        result = client.get_equipment_thresholds("temperature")
        assert result == {}

    def test_get_threshold_value_with_default(self):
        client = ThresholdConfigClient()
        client._http_available = False
        value = client.get_threshold_value("health", "critical")
        assert value == 0.4

    def test_get_threshold_value_missing_returns_default_arg(self):
        client = ThresholdConfigClient()
        client._http_available = False
        value = client.get_threshold_value("nonexistent", "key", default=42.0)
        assert value == 42.0


# ---- Singleton tests ----

class TestSingletonFunctions:

    def test_singleton_get_threshold_client(self):
        """get_threshold_client should return the same instance on repeated calls."""
        client1 = get_threshold_client()
        client2 = get_threshold_client()
        assert client1 is client2

    def test_reset_threshold_client(self):
        """reset_threshold_client should clear the singleton so a new one is created."""
        client_before = get_threshold_client()
        reset_threshold_client()
        client_after = get_threshold_client()
        assert client_before is not client_after

    def test_reset_then_get_returns_fresh_instance(self):
        """After reset, the new instance should have empty cache."""
        client = get_threshold_client()
        # Populate cache
        client._http_available = False
        client.get_all_thresholds()
        assert client._cache != {}

        reset_threshold_client()
        fresh = get_threshold_client()
        assert fresh._cache == {}
