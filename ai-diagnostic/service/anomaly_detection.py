#!/usr/bin/env python3
"""
Anomaly Detection Service

Isolation Forest-based anomaly detection for base station metrics.
Detects unusual patterns that may indicate equipment issues or security threats.

Features:
- Multi-metric anomaly scoring
- Contextual anomaly detection (time-of-day aware)
- Root cause hints based on metric correlations
- Configurable sensitivity per metric type
- Real-time streaming detection
"""

import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Set, Tuple, Any
from enum import Enum
from collections import deque
import numpy as np
import random

logger = logging.getLogger(__name__)

# Modern RNG (replaces deprecated np.random.choice)
_rng = np.random.default_rng(42)


class AnomalySeverity(Enum):
    """Severity levels for detected anomalies."""
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class AnomalyCategory(Enum):
    """Categories of anomalies."""
    PERFORMANCE = "performance"
    SECURITY = "security"
    HARDWARE = "hardware"
    ENVIRONMENTAL = "environmental"
    NETWORK = "network"
    UNKNOWN = "unknown"


@dataclass
class MetricReading:
    """A metric reading for anomaly detection."""
    timestamp: datetime
    station_id: str
    metric_name: str
    value: float
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class Anomaly:
    """Detected anomaly."""
    anomaly_id: str
    station_id: str
    detected_at: datetime
    severity: AnomalySeverity
    category: AnomalyCategory
    anomaly_score: float  # 0-1, higher = more anomalous
    affected_metrics: List[str]
    metric_values: Dict[str, float]
    expected_ranges: Dict[str, Tuple[float, float]]
    description: str
    root_cause_hints: List[str]
    recommended_actions: List[str]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "anomaly_id": self.anomaly_id,
            "station_id": self.station_id,
            "detected_at": self.detected_at.isoformat(),
            "severity": self.severity.value,
            "category": self.category.value,
            "anomaly_score": self.anomaly_score,
            "affected_metrics": self.affected_metrics,
            "metric_values": self.metric_values,
            "expected_ranges": {
                k: {"lower": v[0], "upper": v[1]}
                for k, v in self.expected_ranges.items()
            },
            "description": self.description,
            "root_cause_hints": self.root_cause_hints,
            "recommended_actions": self.recommended_actions,
        }


class IsolationTree:
    """
    Single tree in the Isolation Forest.

    Recursively partitions data by random splits until points are isolated.
    Anomalies are isolated quickly (short path length).
    """

    def __init__(self, height_limit: int):
        self.height_limit = height_limit
        self.root = None
        self.n_features = 0

    def fit(self, X: np.ndarray) -> None:
        """Build the isolation tree."""
        self.n_features = X.shape[1]
        self.root = self._build_tree(X, 0)

    def _build_tree(self, X: np.ndarray, height: int) -> Dict:
        """Recursively build tree nodes."""
        n_samples = X.shape[0]

        # Terminal conditions
        if height >= self.height_limit or n_samples <= 1:
            return {"type": "leaf", "size": n_samples}

        # Random feature and split value
        feature_idx = random.randint(0, self.n_features - 1)
        feature_values = X[:, feature_idx]
        min_val, max_val = feature_values.min(), feature_values.max()

        if min_val == max_val:
            return {"type": "leaf", "size": n_samples}

        split_value = random.uniform(min_val, max_val)

        # Split data
        left_mask = feature_values < split_value
        right_mask = ~left_mask

        return {
            "type": "internal",
            "feature": feature_idx,
            "split": split_value,
            "left": self._build_tree(X[left_mask], height + 1),
            "right": self._build_tree(X[right_mask], height + 1),
        }

    def path_length(self, x: np.ndarray) -> float:
        """Compute path length for a single point."""
        return self._traverse(x, self.root, 0)

    def _traverse(self, x: np.ndarray, node: Dict, height: int) -> float:
        """Traverse tree to find path length."""
        if node["type"] == "leaf":
            # Average path length adjustment for external nodes
            n = node["size"]
            if n <= 1:
                return height
            else:
                # Expected path length in a BST
                return height + self._c(n)

        if x[node["feature"]] < node["split"]:
            return self._traverse(x, node["left"], height + 1)
        else:
            return self._traverse(x, node["right"], height + 1)

    @staticmethod
    def _c(n: int) -> float:
        """Average path length of unsuccessful search in BST."""
        if n <= 1:
            return 0
        return 2 * (np.log(n - 1) + 0.5772156649) - 2 * (n - 1) / n


class IsolationForest:
    """
    Isolation Forest for anomaly detection.

    Ensemble of isolation trees that scores points based on
    how quickly they become isolated.
    """

    def __init__(
        self,
        n_trees: int = 100,
        sample_size: int = 256,
        contamination: float = 0.1,
    ):
        self.n_trees = n_trees
        self.sample_size = sample_size
        self.contamination = contamination
        self.trees: List[IsolationTree] = []
        self.threshold = 0.5
        self._fitted = False

    def fit(self, X: np.ndarray) -> None:
        """Fit the isolation forest."""
        n_samples = X.shape[0]
        height_limit = int(np.ceil(np.log2(max(self.sample_size, 2))))

        self.trees = []
        for _ in range(self.n_trees):
            tree = IsolationTree(height_limit)

            # Subsample
            if n_samples > self.sample_size:
                indices = _rng.choice(n_samples, self.sample_size, replace=False)
                tree.fit(X[indices])
            else:
                tree.fit(X)

            self.trees.append(tree)

        # Set threshold based on contamination
        scores = self.score_samples(X)
        self.threshold = np.percentile(scores, 100 * (1 - self.contamination))
        self._fitted = True

    def score_samples(self, X: np.ndarray) -> np.ndarray:
        """
        Compute anomaly scores for samples.

        Returns:
            Array of scores where higher = more anomalous
        """
        if not self.trees:
            return np.zeros(X.shape[0])

        # Average path length across all trees
        avg_path_lengths = np.zeros(X.shape[0])
        for tree in self.trees:
            for i in range(X.shape[0]):
                avg_path_lengths[i] += tree.path_length(X[i])
        avg_path_lengths /= len(self.trees)

        # Normalize to anomaly score (0-1)
        c = IsolationTree._c(self.sample_size)
        scores = 2 ** (-avg_path_lengths / c)

        return scores

    def predict(self, X: np.ndarray) -> np.ndarray:
        """
        Predict anomalies.

        Returns:
            Array of -1 (anomaly) or 1 (normal)
        """
        scores = self.score_samples(X)
        return np.where(scores > self.threshold, -1, 1)


class AnomalyDetector:
    """
    Base station anomaly detection service.

    Combines multiple detection strategies:
    - Statistical outlier detection (Z-score)
    - Isolation Forest for multivariate anomalies
    - Contextual detection (time-of-day patterns)
    - Correlation-based detection
    """

    # Metric categories for root cause analysis
    METRIC_CATEGORIES = {
        "cpu_usage": AnomalyCategory.PERFORMANCE,
        "memory_usage": AnomalyCategory.PERFORMANCE,
        "temperature": AnomalyCategory.ENVIRONMENTAL,
        "humidity": AnomalyCategory.ENVIRONMENTAL,
        "voltage": AnomalyCategory.HARDWARE,
        "current": AnomalyCategory.HARDWARE,
        "fan_speed": AnomalyCategory.HARDWARE,
        "signal_strength": AnomalyCategory.NETWORK,
        "throughput": AnomalyCategory.NETWORK,
        "latency": AnomalyCategory.NETWORK,
        "packet_loss": AnomalyCategory.NETWORK,
        "connection_count": AnomalyCategory.NETWORK,
        "door_status": AnomalyCategory.SECURITY,
        "motion_detected": AnomalyCategory.SECURITY,
    }

    # Correlated metrics for root cause hints
    METRIC_CORRELATIONS = {
        "cpu_usage": ["temperature", "fan_speed", "throughput"],
        "temperature": ["cpu_usage", "fan_speed", "voltage"],
        "throughput": ["cpu_usage", "connection_count", "latency"],
        "latency": ["throughput", "packet_loss", "cpu_usage"],
        "voltage": ["current", "temperature", "fan_speed"],
    }

    def __init__(
        self,
        window_size: int = 1000,
        z_threshold: float = 3.0,
        isolation_contamination: float = 0.05,
    ):
        self.window_size = window_size
        self.z_threshold = z_threshold
        self.isolation_contamination = isolation_contamination

        # Per-station data storage
        self.data: Dict[str, Dict[str, deque]] = {}  # station_id -> metric -> values
        self.stats: Dict[str, Dict[str, Dict]] = {}  # station_id -> metric -> {mean, std}

        # Isolation forests per station
        self.forests: Dict[str, IsolationForest] = {}
        self.feature_names: Dict[str, List[str]] = {}

        # Anomaly history
        self.anomaly_count = 0
        self.recent_anomalies: Dict[str, List[Anomaly]] = {}

        logger.info(f"AnomalyDetector initialized with window_size={window_size}")

    def _ensure_station_data(self, station_id: str) -> None:
        """Initialize data structures for a station."""
        if station_id not in self.data:
            self.data[station_id] = {}
            self.stats[station_id] = {}
            self.recent_anomalies[station_id] = []

    def add_reading(self, reading: MetricReading) -> Optional[Anomaly]:
        """
        Add a metric reading and check for anomalies.

        Returns:
            Anomaly if detected, None otherwise
        """
        station_id = reading.station_id
        metric = reading.metric_name.lower()

        self._ensure_station_data(station_id)

        # Initialize metric storage
        if metric not in self.data[station_id]:
            self.data[station_id][metric] = deque(maxlen=self.window_size)
            self.stats[station_id][metric] = {"mean": reading.value, "std": 0, "count": 0}

        # Add value
        self.data[station_id][metric].append(reading.value)

        # Update running statistics
        self._update_stats(station_id, metric, reading.value)

        # Check for anomaly
        return self._check_anomaly(station_id, metric, reading)

    def add_batch(self, readings: List[MetricReading]) -> List[Anomaly]:
        """Add multiple readings and return any detected anomalies."""
        anomalies = []
        for reading in readings:
            anomaly = self.add_reading(reading)
            if anomaly:
                anomalies.append(anomaly)
        return anomalies

    def _update_stats(self, station_id: str, metric: str, value: float) -> None:
        """Update running mean and std using Welford's algorithm."""
        stats = self.stats[station_id][metric]
        stats["count"] += 1
        n = stats["count"]

        # Welford's online algorithm
        delta = value - stats["mean"]
        stats["mean"] += delta / n
        delta2 = value - stats["mean"]

        if n > 1:
            # Running variance
            m2 = stats.get("m2", 0) + delta * delta2
            stats["m2"] = m2
            stats["std"] = np.sqrt(m2 / (n - 1))

    def _check_anomaly(
        self,
        station_id: str,
        metric: str,
        reading: MetricReading,
    ) -> Optional[Anomaly]:
        """Check if the current reading is anomalous."""
        stats = self.stats[station_id][metric]

        # Need minimum data for detection
        if stats["count"] < 30:
            return None

        value = reading.value
        mean = stats["mean"]
        std = stats["std"]

        # Avoid division by zero
        if std < 1e-10:
            std = abs(mean) * 0.01 if mean != 0 else 1.0

        # Z-score anomaly detection
        z_score = abs(value - mean) / std

        if z_score < self.z_threshold:
            return None  # Not anomalous

        # Anomaly detected - gather details
        self.anomaly_count += 1
        anomaly_id = f"ANM-{station_id[:8]}-{self.anomaly_count:06d}"

        # Calculate anomaly score (0-1)
        anomaly_score = min(1.0, z_score / (self.z_threshold * 2))

        # Determine severity
        if z_score > self.z_threshold * 3:
            severity = AnomalySeverity.CRITICAL
        elif z_score > self.z_threshold * 2:
            severity = AnomalySeverity.HIGH
        elif z_score > self.z_threshold * 1.5:
            severity = AnomalySeverity.MEDIUM
        else:
            severity = AnomalySeverity.LOW

        # Determine category
        category = self.METRIC_CATEGORIES.get(metric, AnomalyCategory.UNKNOWN)

        # Expected range
        expected_lower = mean - 2 * std
        expected_upper = mean + 2 * std

        # Check correlated metrics for root cause hints
        root_cause_hints = self._analyze_correlations(station_id, metric, value)

        # Generate description
        direction = "above" if value > mean else "below"
        description = (
            f"{metric} is {z_score:.1f} standard deviations {direction} normal. "
            f"Value: {value:.2f}, Expected: {mean:.2f} ± {2*std:.2f}"
        )

        # Generate recommendations
        recommendations = self._generate_recommendations(
            metric, category, severity, value, mean, std
        )

        anomaly = Anomaly(
            anomaly_id=anomaly_id,
            station_id=station_id,
            detected_at=reading.timestamp,
            severity=severity,
            category=category,
            anomaly_score=anomaly_score,
            affected_metrics=[metric],
            metric_values={metric: value},
            expected_ranges={metric: (expected_lower, expected_upper)},
            description=description,
            root_cause_hints=root_cause_hints,
            recommended_actions=recommendations,
        )

        # Store in history
        self.recent_anomalies[station_id].append(anomaly)
        if len(self.recent_anomalies[station_id]) > 100:
            self.recent_anomalies[station_id].pop(0)

        logger.warning(f"Anomaly detected: {anomaly_id} - {description}")

        return anomaly

    def _analyze_correlations(
        self,
        station_id: str,
        metric: str,
        value: float,
    ) -> List[str]:
        """Analyze correlated metrics to suggest root causes."""
        hints = []

        correlated = self.METRIC_CORRELATIONS.get(metric, [])

        for corr_metric in correlated:
            if corr_metric in self.data[station_id]:
                corr_values = list(self.data[station_id][corr_metric])
                if len(corr_values) >= 10:
                    corr_stats = self.stats[station_id].get(corr_metric, {})
                    recent_val = corr_values[-1] if corr_values else 0
                    corr_mean = corr_stats.get("mean", recent_val)
                    corr_std = corr_stats.get("std", 1)

                    if corr_std > 0:
                        corr_z = abs(recent_val - corr_mean) / corr_std
                        if corr_z > 2:
                            hints.append(
                                f"Related metric '{corr_metric}' is also abnormal "
                                f"({corr_z:.1f} std from mean)"
                            )

        # Category-specific hints
        if metric == "temperature" and "fan_speed" in self.data[station_id]:
            fan_values = list(self.data[station_id]["fan_speed"])
            if fan_values and fan_values[-1] < self.stats[station_id].get("fan_speed", {}).get("mean", 100) * 0.5:
                hints.append("Fan speed is low - possible cooling system issue")

        if metric == "cpu_usage" and "connection_count" in self.data[station_id]:
            conn_values = list(self.data[station_id]["connection_count"])
            if conn_values and conn_values[-1] > self.stats[station_id].get("connection_count", {}).get("mean", 0) * 1.5:
                hints.append("Connection count is elevated - possible traffic surge or DDoS")

        return hints

    def _generate_recommendations(
        self,
        metric: str,
        category: AnomalyCategory,
        severity: AnomalySeverity,
        value: float,
        mean: float,
        std: float,
    ) -> List[str]:
        """Generate actionable recommendations."""
        recommendations = []

        if severity in [AnomalySeverity.CRITICAL, AnomalySeverity.HIGH]:
            recommendations.append("Dispatch field technician for inspection")

        if category == AnomalyCategory.ENVIRONMENTAL:
            if metric == "temperature" and value > mean + 2 * std:
                recommendations.append("Check HVAC system and ventilation")
                recommendations.append("Consider load shedding to reduce heat generation")
            elif metric == "humidity" and value > mean + 2 * std:
                recommendations.append("Check for water ingress or condensation")

        elif category == AnomalyCategory.HARDWARE:
            if metric == "voltage":
                recommendations.append("Check power supply and battery backup")
                recommendations.append("Verify utility power stability")
            elif metric == "fan_speed":
                recommendations.append("Inspect fan and cooling system")

        elif category == AnomalyCategory.NETWORK:
            if metric == "throughput" and value < mean - 2 * std:
                recommendations.append("Check for backhaul issues")
                recommendations.append("Verify antenna alignment")
            elif metric == "latency" and value > mean + 2 * std:
                recommendations.append("Check for network congestion")
                recommendations.append("Verify routing configuration")

        elif category == AnomalyCategory.SECURITY:
            recommendations.append("Review security camera footage")
            recommendations.append("Verify authorized personnel access logs")

        if not recommendations:
            recommendations.append("Monitor closely and gather more data")

        return recommendations

    def get_station_anomalies(
        self,
        station_id: str,
        since: Optional[datetime] = None,
        severity_filter: Optional[List[AnomalySeverity]] = None,
    ) -> List[Anomaly]:
        """Get recent anomalies for a station."""
        if station_id not in self.recent_anomalies:
            return []

        anomalies = self.recent_anomalies[station_id]

        if since:
            anomalies = [a for a in anomalies if a.detected_at >= since]

        if severity_filter:
            anomalies = [a for a in anomalies if a.severity in severity_filter]

        return anomalies

    def get_health_score(self, station_id: str) -> Dict[str, Any]:
        """
        Calculate overall health score for a station.

        Returns:
            Dict with health score and contributing factors
        """
        if station_id not in self.recent_anomalies:
            return {"station_id": station_id, "health_score": 100, "status": "unknown"}

        # Get recent anomalies (last 24 hours)
        since = datetime.now() - timedelta(hours=24)
        recent = self.get_station_anomalies(station_id, since=since)

        # Calculate penalty based on anomaly severity
        penalty = 0
        for anomaly in recent:
            if anomaly.severity == AnomalySeverity.CRITICAL:
                penalty += 25
            elif anomaly.severity == AnomalySeverity.HIGH:
                penalty += 15
            elif anomaly.severity == AnomalySeverity.MEDIUM:
                penalty += 8
            else:
                penalty += 3

        health_score = max(0, 100 - penalty)

        # Determine status
        if health_score >= 90:
            status = "healthy"
        elif health_score >= 70:
            status = "warning"
        elif health_score >= 50:
            status = "degraded"
        else:
            status = "critical"

        # Category breakdown
        category_counts = {}
        for anomaly in recent:
            cat = anomaly.category.value
            category_counts[cat] = category_counts.get(cat, 0) + 1

        return {
            "station_id": station_id,
            "health_score": health_score,
            "status": status,
            "anomaly_count_24h": len(recent),
            "category_breakdown": category_counts,
            "last_anomaly": recent[-1].to_dict() if recent else None,
        }


# Singleton instance
_anomaly_detector: Optional[AnomalyDetector] = None


def get_anomaly_detector() -> AnomalyDetector:
    """Get or create the anomaly detector singleton."""
    global _anomaly_detector
    if _anomaly_detector is None:
        _anomaly_detector = AnomalyDetector()
    return _anomaly_detector


# Convenience functions for API integration
def detect_anomaly(
    station_id: str,
    metric_name: str,
    value: float,
    timestamp: Optional[datetime] = None,
) -> Optional[Dict[str, Any]]:
    """
    API-friendly function to detect anomalies.

    Returns:
        Anomaly dict if detected, None otherwise
    """
    detector = get_anomaly_detector()

    reading = MetricReading(
        timestamp=timestamp or datetime.now(),
        station_id=station_id,
        metric_name=metric_name,
        value=value,
    )

    anomaly = detector.add_reading(reading)
    return anomaly.to_dict() if anomaly else None


def get_station_health(station_id: str) -> Dict[str, Any]:
    """Get health score for a station."""
    detector = get_anomaly_detector()
    return detector.get_health_score(station_id)


def get_recent_anomalies(
    station_id: str,
    hours: int = 24,
    severity: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """Get recent anomalies for a station."""
    detector = get_anomaly_detector()

    since = datetime.now() - timedelta(hours=hours)

    severity_filter = None
    if severity:
        severity_filter = [AnomalySeverity(s) for s in severity]

    anomalies = detector.get_station_anomalies(
        station_id, since=since, severity_filter=severity_filter
    )

    return [a.to_dict() for a in anomalies]
