"""
Threshold Configuration Client.

Fetches threshold configurations from the Java monitoring service REST API.
Provides local caching to minimize network calls.

Usage:
    from utils.threshold_client import ThresholdConfigClient

    client = ThresholdConfigClient()
    health_thresholds = client.get_health_thresholds()
    confidence_thresholds = client.get_confidence_thresholds()
"""

import logging
import os
import time
import threading
from typing import Any, Dict, Optional

logger = logging.getLogger(__name__)

# Default values matching shared-thresholds.json
DEFAULT_HEALTH_THRESHOLDS = {
    "critical": 0.4,
    "warning": 0.6,
    "degraded": 0.8,
}

DEFAULT_CONFIDENCE_THRESHOLDS = {
    "auto_apply": 0.95,
    "suggest_apply": 0.85,
    "manual_review": 0.70,
    "low": 0.70,
    "auto_apply_low_risk": 0.90,
    "auto_apply_medium_risk": 0.95,
}

DEFAULT_LEARNING_THRESHOLDS = {
    "high_success_rate": 0.80,
    "low_success_rate": 0.50,
    "max_confidence_boost": 0.10,
    "max_confidence_penalty": 0.20,
    "min_feedback_for_adjustment": 5.0,
}


class ThresholdConfigClient:
    """
    Client for fetching threshold configurations from the monitoring service.

    Features:
    - Local in-memory cache with configurable TTL
    - Thread-safe caching
    - Graceful fallback to defaults if service is unavailable
    - Lazy loading of HTTP library (requests is optional)

    Environment Variables:
        MONITORING_SERVICE_URL: Base URL of monitoring service (default: http://monitoring-service:8082)
        THRESHOLD_CACHE_TTL: Cache TTL in seconds (default: 300)
    """

    def __init__(
        self,
        base_url: Optional[str] = None,
        cache_ttl: float = 300.0,
    ):
        """
        Initialize the threshold config client.

        Args:
            base_url: Base URL of the monitoring service API.
                      Defaults to MONITORING_SERVICE_URL env var or http://monitoring-service:8082
            cache_ttl: Cache time-to-live in seconds (default: 5 minutes)
        """
        self.base_url = base_url or os.environ.get(
            "MONITORING_SERVICE_URL", "http://monitoring-service:8082"
        )
        self._cache: Dict[str, Any] = {}
        self._cache_time: float = 0
        self._cache_ttl: float = float(
            os.environ.get("THRESHOLD_CACHE_TTL", str(cache_ttl))
        )
        self._lock = threading.Lock()
        self._http_available = False
        self._requests: Any = None

        # Lazy import requests
        try:
            import requests
            self._requests = requests
            self._http_available = True
        except ImportError:
            logger.warning("requests library not available, using default thresholds")

    def _is_cache_valid(self) -> bool:
        """Check if the cache is still valid."""
        return (
            self._cache
            and (time.time() - self._cache_time) < self._cache_ttl
        )

    def _fetch_all_thresholds(self) -> Optional[Dict[str, Any]]:
        """Fetch all thresholds from the monitoring service."""
        if not self._http_available or not self._requests:
            return None

        try:
            url = f"{self.base_url}/api/v1/thresholds"
            response = self._requests.get(url, timeout=5)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            logger.warning("Failed to fetch thresholds from %s: %s", self.base_url, e)
            return None

    def get_all_thresholds(self) -> Dict[str, Any]:
        """
        Get all threshold configurations.

        Returns cached values if available, otherwise fetches from the API.
        Falls back to defaults if the API is unavailable.

        Returns:
            Dictionary with all threshold configurations
        """
        with self._lock:
            if self._is_cache_valid():
                return self._cache

            fetched = self._fetch_all_thresholds()
            if fetched:
                self._cache = fetched
                self._cache_time = time.time()
                logger.debug("Refreshed threshold cache from API")
                return self._cache

            # Return defaults if fetch failed
            if not self._cache:
                self._cache = {
                    "health": DEFAULT_HEALTH_THRESHOLDS,
                    "confidence": DEFAULT_CONFIDENCE_THRESHOLDS,
                    "learning": DEFAULT_LEARNING_THRESHOLDS,
                }
                self._cache_time = time.time()
                logger.info("Using default thresholds")

            return self._cache

    def get_health_thresholds(self) -> Dict[str, float]:
        """
        Get health status thresholds.

        Returns:
            Dictionary with keys: critical, warning, degraded
        """
        all_thresholds = self.get_all_thresholds()
        return all_thresholds.get("health", DEFAULT_HEALTH_THRESHOLDS)

    def get_confidence_thresholds(self) -> Dict[str, float]:
        """
        Get confidence automation thresholds.

        Returns:
            Dictionary with keys: auto_apply, suggest_apply, manual_review, etc.
        """
        all_thresholds = self.get_all_thresholds()
        return all_thresholds.get("confidence", DEFAULT_CONFIDENCE_THRESHOLDS)

    def get_learning_thresholds(self) -> Dict[str, float]:
        """
        Get learning algorithm thresholds.

        Returns:
            Dictionary with keys: high_success_rate, low_success_rate, etc.
        """
        all_thresholds = self.get_all_thresholds()
        return all_thresholds.get("learning", DEFAULT_LEARNING_THRESHOLDS)

    def get_equipment_thresholds(self, equipment_type: str) -> Dict[str, Any]:
        """
        Get equipment-specific thresholds.

        Args:
            equipment_type: Type of equipment (e.g., "temperature", "cpu", "battery_soc")

        Returns:
            Dictionary with keys: healthy, warning, critical, unit, higher_is_worse
        """
        all_thresholds = self.get_all_thresholds()
        equipment = all_thresholds.get("equipment", {})
        return equipment.get(equipment_type, {})

    def get_threshold_value(
        self,
        config_type: str,
        threshold_name: str,
        default: Optional[float] = None,
    ) -> Optional[float]:
        """
        Get a specific threshold value.

        Args:
            config_type: Configuration type (e.g., "health", "confidence")
            threshold_name: Threshold name within the config (e.g., "critical")
            default: Default value if not found

        Returns:
            Threshold value or default
        """
        all_thresholds = self.get_all_thresholds()
        config = all_thresholds.get(config_type, {})
        return config.get(threshold_name, default)

    def invalidate_cache(self) -> None:
        """Invalidate the local cache, forcing a refresh on next access."""
        with self._lock:
            self._cache = {}
            self._cache_time = 0
            logger.debug("Threshold cache invalidated")

    def refresh(self) -> Dict[str, Any]:
        """Force refresh the cache from the API."""
        self.invalidate_cache()
        return self.get_all_thresholds()


# Singleton instance for convenience
_client: Optional[ThresholdConfigClient] = None
_client_lock = threading.Lock()


def get_threshold_client() -> ThresholdConfigClient:
    """Get the singleton ThresholdConfigClient instance."""
    global _client
    if _client is None:
        with _client_lock:
            if _client is None:
                _client = ThresholdConfigClient()
    return _client


def reset_threshold_client() -> None:
    """Reset the singleton client (for testing)."""
    global _client
    with _client_lock:
        _client = None
