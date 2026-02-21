"""
Tests for the anomaly detection service.

Covers MetricReading, IsolationForest, AnomalyDetector,
and the AnomalySeverity / AnomalyCategory enums.
"""

from datetime import datetime

import numpy as np
import pytest

from service.anomaly_detection import (
    AnomalyCategory,
    AnomalyDetector,
    AnomalySeverity,
    IsolationForest,
    MetricReading,
)


# ---- MetricReading tests ----

class TestMetricReading:

    def test_metric_reading_creation(self):
        now = datetime.now()
        reading = MetricReading(
            timestamp=now,
            station_id="STATION-1",
            metric_name="temperature",
            value=42.0,
        )
        assert reading.timestamp == now
        assert reading.station_id == "STATION-1"
        assert reading.metric_name == "temperature"
        assert reading.value == 42.0
        assert reading.metadata == {}

    def test_metric_reading_with_metadata(self):
        reading = MetricReading(
            timestamp=datetime.now(),
            station_id="STATION-2",
            metric_name="cpu_usage",
            value=78.5,
            metadata={"unit": "percent"},
        )
        assert reading.metadata == {"unit": "percent"}


# ---- Enum tests ----

class TestAnomalySeverity:

    def test_anomaly_severity_ordering(self):
        """Severity values are distinct and have expected string representations."""
        expected = ["low", "medium", "high", "critical"]
        actual = [
            AnomalySeverity.LOW.value,
            AnomalySeverity.MEDIUM.value,
            AnomalySeverity.HIGH.value,
            AnomalySeverity.CRITICAL.value,
        ]
        assert actual == expected

    def test_anomaly_severity_members(self):
        assert len(AnomalySeverity) == 4


class TestAnomalyCategory:

    def test_anomaly_category_values(self):
        expected_values = {
            "performance", "security", "hardware",
            "environmental", "network", "unknown",
        }
        actual_values = {member.value for member in AnomalyCategory}
        assert actual_values == expected_values


# ---- IsolationForest tests ----

class TestIsolationForest:

    def test_isolation_forest_fit(self):
        """Forest should fit without error and mark itself as fitted."""
        rng = np.random.default_rng(42)
        data = rng.normal(loc=50, scale=5, size=(100, 1))
        forest = IsolationForest(n_trees=10, sample_size=50, contamination=0.1)
        forest.fit(data)
        assert forest._fitted is True
        assert len(forest.trees) == 10

    def test_isolation_forest_detect_obvious_outlier(self):
        """An extreme outlier should be predicted as anomalous (-1)."""
        rng = np.random.default_rng(42)
        # Train on normal values in the range [1, 5]
        train_data = rng.uniform(1, 5, size=(200, 1))
        forest = IsolationForest(n_trees=50, sample_size=100, contamination=0.1)
        forest.fit(train_data)

        # A value of 1000 is far outside the training distribution
        outlier = np.array([[1000.0]])
        prediction = forest.predict(outlier)
        assert prediction[0] == -1, "Expected extreme outlier to be flagged as anomaly (-1)"

    def test_isolation_forest_normal_values_pass(self):
        """Values from the training distribution should mostly be normal (1)."""
        rng = np.random.default_rng(42)
        train_data = rng.normal(loc=50, scale=2, size=(200, 1))
        forest = IsolationForest(n_trees=50, sample_size=100, contamination=0.05)
        forest.fit(train_data)

        # Test points near the center of the distribution
        normal_points = np.array([[50.0], [49.0], [51.0]])
        predictions = forest.predict(normal_points)
        normal_count = np.sum(predictions == 1)
        # At least 2 out of 3 central points should be classified as normal
        assert normal_count >= 2, (
            f"Expected most central points to be normal, got {normal_count}/3"
        )

    def test_isolation_forest_score_samples(self):
        """Score samples should return scores between 0 and 1."""
        rng = np.random.default_rng(42)
        data = rng.normal(loc=0, scale=1, size=(100, 2))
        forest = IsolationForest(n_trees=20, sample_size=50, contamination=0.1)
        forest.fit(data)

        scores = forest.score_samples(data)
        assert scores.shape == (100,)
        assert np.all(scores >= 0)
        assert np.all(scores <= 1)

    def test_isolation_forest_unfitted_returns_zeros(self):
        """Score samples on unfitted forest should return zeros."""
        forest = IsolationForest(n_trees=10)
        data = np.array([[1.0, 2.0], [3.0, 4.0]])
        scores = forest.score_samples(data)
        assert np.all(scores == 0)


# ---- AnomalyDetector (service) tests ----

class TestAnomalyDetectionService:

    def test_anomaly_detection_service_creation(self):
        detector = AnomalyDetector()
        assert detector.window_size == 1000
        assert detector.z_threshold == 3.0
        assert detector.anomaly_count == 0

    def test_anomaly_detection_service_custom_params(self):
        detector = AnomalyDetector(
            window_size=500,
            z_threshold=2.5,
            isolation_contamination=0.1,
        )
        assert detector.window_size == 500
        assert detector.z_threshold == 2.5
        assert detector.isolation_contamination == 0.1

    def test_add_reading_no_anomaly_with_few_readings(self):
        """Detector needs >= 30 readings before it starts detecting anomalies."""
        detector = AnomalyDetector()
        for i in range(29):
            reading = MetricReading(
                timestamp=datetime.now(),
                station_id="S1",
                metric_name="cpu_usage",
                value=50.0 + i * 0.1,
            )
            anomaly = detector.add_reading(reading)
            assert anomaly is None

    def test_add_reading_detects_anomaly_after_warmup(self):
        """After warmup, an extreme reading should be flagged."""
        detector = AnomalyDetector(z_threshold=2.0)
        # Feed 50 normal readings
        for i in range(50):
            reading = MetricReading(
                timestamp=datetime.now(),
                station_id="S1",
                metric_name="temperature",
                value=50.0,
            )
            detector.add_reading(reading)

        # Feed an extreme reading
        extreme = MetricReading(
            timestamp=datetime.now(),
            station_id="S1",
            metric_name="temperature",
            value=999.0,
        )
        anomaly = detector.add_reading(extreme)
        assert anomaly is not None
        assert anomaly.station_id == "S1"
        assert "temperature" in anomaly.affected_metrics

    def test_get_health_score_unknown_station(self):
        detector = AnomalyDetector()
        health = detector.get_health_score("UNKNOWN")
        assert health["health_score"] == 100
        assert health["status"] == "unknown"

    def test_metric_categories_mapping(self):
        """Verify key metric-to-category mappings exist."""
        assert AnomalyDetector.METRIC_CATEGORIES["cpu_usage"] == AnomalyCategory.PERFORMANCE
        assert AnomalyDetector.METRIC_CATEGORIES["temperature"] == AnomalyCategory.ENVIRONMENTAL
        assert AnomalyDetector.METRIC_CATEGORIES["voltage"] == AnomalyCategory.HARDWARE
        assert AnomalyDetector.METRIC_CATEGORIES["signal_strength"] == AnomalyCategory.NETWORK
        assert AnomalyDetector.METRIC_CATEGORIES["door_status"] == AnomalyCategory.SECURITY
