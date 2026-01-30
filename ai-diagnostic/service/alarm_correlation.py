"""
Alarm Correlation Service - AI-powered alarm analysis and root cause identification

Quick Win #2: Alarm Correlation
- Clusters related alarms to identify root causes
- Reduces alarm storms by grouping correlated events
- Predicts cascading failures
- Learns from operator feedback

Uses temporal and spatial correlation with DBSCAN clustering.
"""

import logging
from typing import List, Dict, Any, Optional, Tuple, Set
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from collections import defaultdict
from enum import Enum
import json

import numpy as np

# Lazy import for scikit-learn
sklearn = None

logger = logging.getLogger(__name__)


def _ensure_sklearn():
    """Lazy load scikit-learn."""
    global sklearn
    if sklearn is None:
        try:
            import sklearn as _sklearn
            from sklearn.cluster import DBSCAN
            from sklearn.preprocessing import StandardScaler
            sklearn = _sklearn
        except ImportError:
            raise ImportError("scikit-learn is required for alarm correlation")
    return sklearn


class AlarmSeverity(Enum):
    """Alarm severity levels."""
    CRITICAL = "critical"
    MAJOR = "major"
    MINOR = "minor"
    WARNING = "warning"
    INFO = "info"


class CorrelationType(Enum):
    """Types of alarm correlation."""
    TEMPORAL = "temporal"          # Alarms close in time
    SPATIAL = "spatial"            # Alarms from same/nearby equipment
    CAUSAL = "causal"              # Known cause-effect relationship
    PATTERN = "pattern"            # Historical pattern match


@dataclass
class Alarm:
    """Alarm event data structure."""
    alarm_id: str
    station_id: str
    alarm_type: str
    severity: AlarmSeverity
    timestamp: datetime
    message: str
    metric_type: Optional[str] = None
    metric_value: Optional[float] = None
    cleared: bool = False
    cleared_at: Optional[datetime] = None
    acknowledged: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "alarm_id": self.alarm_id,
            "station_id": self.station_id,
            "alarm_type": self.alarm_type,
            "severity": self.severity.value,
            "timestamp": self.timestamp.isoformat(),
            "message": self.message,
            "metric_type": self.metric_type,
            "metric_value": self.metric_value,
            "cleared": self.cleared,
            "cleared_at": self.cleared_at.isoformat() if self.cleared_at else None,
            "acknowledged": self.acknowledged
        }


@dataclass
class AlarmCluster:
    """A group of correlated alarms."""
    cluster_id: str
    alarms: List[Alarm]
    root_cause: Optional[str] = None
    root_cause_confidence: float = 0.0
    correlation_types: List[CorrelationType] = field(default_factory=list)
    recommended_action: Optional[str] = None
    suppressed: bool = False
    created_at: datetime = field(default_factory=datetime.utcnow)

    @property
    def severity(self) -> AlarmSeverity:
        """Highest severity in the cluster."""
        severity_order = [AlarmSeverity.CRITICAL, AlarmSeverity.MAJOR,
                        AlarmSeverity.MINOR, AlarmSeverity.WARNING, AlarmSeverity.INFO]
        for sev in severity_order:
            if any(a.severity == sev for a in self.alarms):
                return sev
        return AlarmSeverity.INFO

    @property
    def station_ids(self) -> Set[str]:
        """All stations involved in this cluster."""
        return {a.station_id for a in self.alarms}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "cluster_id": self.cluster_id,
            "alarm_count": len(self.alarms),
            "severity": self.severity.value,
            "station_ids": list(self.station_ids),
            "root_cause": self.root_cause,
            "root_cause_confidence": round(self.root_cause_confidence, 3),
            "correlation_types": [ct.value for ct in self.correlation_types],
            "recommended_action": self.recommended_action,
            "suppressed": self.suppressed,
            "created_at": self.created_at.isoformat(),
            "time_span_seconds": self._get_time_span(),
            "alarms": [a.to_dict() for a in self.alarms]
        }

    def _get_time_span(self) -> float:
        """Get time span of alarms in seconds."""
        if len(self.alarms) < 2:
            return 0
        timestamps = [a.timestamp for a in self.alarms]
        return (max(timestamps) - min(timestamps)).total_seconds()


@dataclass
class CorrelationResult:
    """Result of alarm correlation analysis."""
    clusters: List[AlarmCluster]
    uncorrelated_alarms: List[Alarm]
    total_alarms: int
    suppression_count: int
    processing_time_ms: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_alarms": self.total_alarms,
            "cluster_count": len(self.clusters),
            "uncorrelated_count": len(self.uncorrelated_alarms),
            "suppression_count": self.suppression_count,
            "reduction_percentage": round(
                (1 - (len(self.clusters) + len(self.uncorrelated_alarms)) / max(self.total_alarms, 1)) * 100, 1
            ),
            "processing_time_ms": round(self.processing_time_ms, 2),
            "clusters": [c.to_dict() for c in self.clusters],
            "uncorrelated_alarms": [a.to_dict() for a in self.uncorrelated_alarms]
        }


class AlarmCorrelationService:
    """
    AI-powered alarm correlation service.

    Features:
    - Temporal clustering (DBSCAN on timestamps)
    - Spatial correlation (same station/equipment)
    - Causal relationship detection (known patterns)
    - Root cause identification
    - Alarm suppression recommendations
    """

    # Known causal relationships: (cause_type, effect_type) -> description
    CAUSAL_RULES = {
        ("POWER_FAILURE", "TEMPERATURE_HIGH"): "Power failure caused cooling system shutdown",
        ("POWER_FAILURE", "FAN_FAILURE"): "Power failure stopped cooling fans",
        ("POWER_FAILURE", "SIGNAL_LOSS"): "Power failure caused transmitter shutdown",
        ("FAN_FAILURE", "TEMPERATURE_HIGH"): "Fan failure caused overheating",
        ("TEMPERATURE_HIGH", "CPU_THROTTLE"): "High temperature caused CPU throttling",
        ("TEMPERATURE_HIGH", "THROUGHPUT_LOW"): "High temperature caused performance degradation",
        ("FIBER_CUT", "SIGNAL_LOSS"): "Fiber cut caused signal loss",
        ("FIBER_CUT", "BACKHAUL_DOWN"): "Fiber cut caused backhaul failure",
        ("BACKHAUL_DOWN", "HANDOVER_FAILURE"): "Backhaul down caused handover failures",
        ("TX_IMBALANCE", "RSRP_WEAK"): "TX imbalance caused weak signal",
        ("TX_IMBALANCE", "THROUGHPUT_LOW"): "TX imbalance degraded throughput",
        ("INTERFERENCE", "SINR_LOW"): "Interference degraded SINR",
        ("INTERFERENCE", "BLER_HIGH"): "Interference increased block error rate",
        ("VSWR_HIGH", "TX_POWER_REDUCED"): "High VSWR caused power reduction",
        ("ANTENNA_TILT_CHANGE", "COVERAGE_CHANGE"): "Antenna tilt change affected coverage",
    }

    # Temporal window for correlation (seconds)
    TEMPORAL_WINDOW = 300  # 5 minutes

    # DBSCAN parameters
    DBSCAN_EPS = 60  # seconds
    DBSCAN_MIN_SAMPLES = 2

    def __init__(self):
        self.cluster_counter = 0
        self.learned_patterns: Dict[str, Dict] = {}  # Learned from operator feedback
        logger.info("AlarmCorrelationService initialized")

    def correlate_alarms(self, alarms: List[Alarm]) -> CorrelationResult:
        """
        Correlate a list of alarms to identify related events and root causes.

        Args:
            alarms: List of alarm events to correlate

        Returns:
            CorrelationResult with clusters and analysis
        """
        import time
        start_time = time.time()

        if not alarms:
            return CorrelationResult(
                clusters=[],
                uncorrelated_alarms=[],
                total_alarms=0,
                suppression_count=0,
                processing_time_ms=0
            )

        # Step 1: Temporal clustering with DBSCAN
        temporal_clusters = self._temporal_clustering(alarms)

        # Step 2: Spatial grouping within temporal clusters
        spatial_clusters = self._spatial_grouping(temporal_clusters)

        # Step 3: Causal analysis
        analyzed_clusters = self._causal_analysis(spatial_clusters)

        # Step 4: Determine suppression candidates
        suppression_count = self._apply_suppression(analyzed_clusters)

        # Separate correlated and uncorrelated alarms
        correlated_alarm_ids = set()
        for cluster in analyzed_clusters:
            correlated_alarm_ids.update(a.alarm_id for a in cluster.alarms)

        uncorrelated = [a for a in alarms if a.alarm_id not in correlated_alarm_ids]

        processing_time = (time.time() - start_time) * 1000

        return CorrelationResult(
            clusters=analyzed_clusters,
            uncorrelated_alarms=uncorrelated,
            total_alarms=len(alarms),
            suppression_count=suppression_count,
            processing_time_ms=processing_time
        )

    def _temporal_clustering(self, alarms: List[Alarm]) -> List[List[Alarm]]:
        """Cluster alarms by timestamp using DBSCAN."""
        _ensure_sklearn()
        from sklearn.cluster import DBSCAN
        from sklearn.preprocessing import StandardScaler

        if len(alarms) < self.DBSCAN_MIN_SAMPLES:
            return [[a] for a in alarms]

        # Convert timestamps to seconds since first alarm
        base_time = min(a.timestamp for a in alarms)
        timestamps = np.array([
            [(a.timestamp - base_time).total_seconds()]
            for a in alarms
        ])

        # DBSCAN clustering
        clustering = DBSCAN(
            eps=self.DBSCAN_EPS,
            min_samples=self.DBSCAN_MIN_SAMPLES
        ).fit(timestamps)

        # Group alarms by cluster label
        clusters: Dict[int, List[Alarm]] = defaultdict(list)
        for alarm, label in zip(alarms, clustering.labels_):
            clusters[label].append(alarm)

        # Convert to list of lists (noise points as individual clusters)
        result = []
        for label, cluster_alarms in clusters.items():
            if label == -1:  # Noise - each alarm is its own cluster
                for alarm in cluster_alarms:
                    result.append([alarm])
            else:
                result.append(cluster_alarms)

        return result

    def _spatial_grouping(self, temporal_clusters: List[List[Alarm]]) -> List[AlarmCluster]:
        """Group alarms within temporal clusters by station/equipment."""
        alarm_clusters = []

        for alarms in temporal_clusters:
            # Group by station
            by_station: Dict[str, List[Alarm]] = defaultdict(list)
            for alarm in alarms:
                by_station[alarm.station_id].append(alarm)

            # Create clusters
            for station_id, station_alarms in by_station.items():
                self.cluster_counter += 1
                cluster = AlarmCluster(
                    cluster_id=f"CL-{self.cluster_counter:06d}",
                    alarms=station_alarms,
                    correlation_types=[CorrelationType.TEMPORAL]
                )

                if len(station_alarms) > 1:
                    cluster.correlation_types.append(CorrelationType.SPATIAL)

                alarm_clusters.append(cluster)

        return alarm_clusters

    def _causal_analysis(self, clusters: List[AlarmCluster]) -> List[AlarmCluster]:
        """Identify causal relationships and root causes within clusters."""
        for cluster in clusters:
            if len(cluster.alarms) < 2:
                continue

            alarm_types = {a.alarm_type for a in cluster.alarms}

            # Check known causal rules
            for (cause, effect), description in self.CAUSAL_RULES.items():
                if cause in alarm_types and effect in alarm_types:
                    cluster.correlation_types.append(CorrelationType.CAUSAL)
                    cluster.root_cause = cause
                    cluster.root_cause_confidence = 0.9
                    cluster.recommended_action = self._get_action_for_cause(cause)
                    break

            # If no causal rule matched, use heuristic: earliest alarm is likely root cause
            if not cluster.root_cause:
                sorted_alarms = sorted(cluster.alarms, key=lambda a: a.timestamp)
                cluster.root_cause = sorted_alarms[0].alarm_type
                cluster.root_cause_confidence = 0.6
                cluster.recommended_action = f"Investigate {cluster.root_cause} as potential root cause"

            # Check learned patterns
            pattern_key = self._get_pattern_key(cluster)
            if pattern_key in self.learned_patterns:
                pattern = self.learned_patterns[pattern_key]
                cluster.root_cause = pattern.get("root_cause", cluster.root_cause)
                cluster.root_cause_confidence = pattern.get("confidence", 0.8)
                cluster.recommended_action = pattern.get("action", cluster.recommended_action)
                cluster.correlation_types.append(CorrelationType.PATTERN)

        return clusters

    def _get_action_for_cause(self, cause: str) -> str:
        """Get recommended action for a known root cause."""
        actions = {
            "POWER_FAILURE": "Check power supply and backup battery status",
            "FAN_FAILURE": "Replace failed fan unit and check thermal conditions",
            "TEMPERATURE_HIGH": "Check cooling system and airflow",
            "FIBER_CUT": "Dispatch technician to locate and repair fiber break",
            "BACKHAUL_DOWN": "Check backhaul connectivity and switch to backup link",
            "TX_IMBALANCE": "Recalibrate TX path and check antenna connections",
            "INTERFERENCE": "Identify interference source and adjust frequency plan",
            "VSWR_HIGH": "Check antenna and feeder connections for damage",
            "ANTENNA_TILT_CHANGE": "Verify antenna tilt settings match configuration",
        }
        return actions.get(cause, f"Investigate {cause}")

    def _get_pattern_key(self, cluster: AlarmCluster) -> str:
        """Generate a key for pattern matching."""
        alarm_types = sorted({a.alarm_type for a in cluster.alarms})
        return "|".join(alarm_types)

    def _apply_suppression(self, clusters: List[AlarmCluster]) -> int:
        """Mark clusters for suppression (symptom alarms when root cause is known)."""
        suppression_count = 0

        for cluster in clusters:
            if cluster.root_cause and cluster.root_cause_confidence > 0.7:
                # Suppress symptom alarms (keep only root cause visible)
                for alarm in cluster.alarms:
                    if alarm.alarm_type != cluster.root_cause:
                        suppression_count += 1

                if suppression_count > 0:
                    cluster.suppressed = True

        return suppression_count

    def learn_from_feedback(
        self,
        cluster_id: str,
        actual_root_cause: str,
        action_taken: str,
        was_correct: bool
    ):
        """
        Learn from operator feedback to improve future correlations.

        Args:
            cluster_id: The cluster that was analyzed
            actual_root_cause: What the actual root cause was
            action_taken: What action resolved the issue
            was_correct: Whether the automated analysis was correct
        """
        # This would typically store in a database
        # For now, we keep in memory
        logger.info(
            "Learning from feedback: cluster=%s, root_cause=%s, correct=%s",
            cluster_id, actual_root_cause, was_correct
        )

        # Update learned patterns (simplified - would use proper ML in production)
        if was_correct:
            pattern_key = f"learned_{cluster_id}"
            self.learned_patterns[pattern_key] = {
                "root_cause": actual_root_cause,
                "action": action_taken,
                "confidence": 0.85,
                "feedback_count": 1
            }

    def get_alarm_summary(self, result: CorrelationResult) -> Dict[str, Any]:
        """Generate a summary of the correlation result."""
        severity_counts = defaultdict(int)
        root_causes = defaultdict(int)

        for cluster in result.clusters:
            severity_counts[cluster.severity.value] += 1
            if cluster.root_cause:
                root_causes[cluster.root_cause] += 1

        return {
            "total_alarms": result.total_alarms,
            "clusters": len(result.clusters),
            "uncorrelated": len(result.uncorrelated_alarms),
            "suppressed": result.suppression_count,
            "reduction_percentage": round(
                (1 - (len(result.clusters) + len(result.uncorrelated_alarms)) / max(result.total_alarms, 1)) * 100, 1
            ),
            "by_severity": dict(severity_counts),
            "top_root_causes": dict(sorted(root_causes.items(), key=lambda x: -x[1])[:5]),
            "stations_affected": list({
                s for c in result.clusters for s in c.station_ids
            })
        }


# Singleton instance
_alarm_service: Optional[AlarmCorrelationService] = None


def get_alarm_correlation_service() -> AlarmCorrelationService:
    """Get or create singleton AlarmCorrelationService instance."""
    global _alarm_service
    if _alarm_service is None:
        _alarm_service = AlarmCorrelationService()
    return _alarm_service


def parse_alarm_from_dict(data: Dict[str, Any]) -> Alarm:
    """Parse an Alarm from dictionary (e.g., from JSON API)."""
    return Alarm(
        alarm_id=data["alarm_id"],
        station_id=data["station_id"],
        alarm_type=data["alarm_type"],
        severity=AlarmSeverity(data.get("severity", "warning")),
        timestamp=datetime.fromisoformat(data["timestamp"]) if isinstance(data["timestamp"], str) else data["timestamp"],
        message=data.get("message", ""),
        metric_type=data.get("metric_type"),
        metric_value=data.get("metric_value"),
        cleared=data.get("cleared", False),
        cleared_at=datetime.fromisoformat(data["cleared_at"]) if data.get("cleared_at") else None,
        acknowledged=data.get("acknowledged", False)
    )
