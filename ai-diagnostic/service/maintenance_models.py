"""
Data models for predictive maintenance.

The metric sample, trend result, failure prediction and component-health value
types shared by the maintenance service and its analytics. Extracted from
predictive_maintenance.py so the pure analytics can depend on them without
importing the service (which would be a cycle).
"""

from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

from .utils import HealthStatus, PredictionConfidence


@dataclass
class MetricDataPoint:
    """A single metric measurement."""
    timestamp: datetime
    value: float
    station_id: str
    metric_type: str


@dataclass
class TrendAnalysis:
    """Result of trend analysis on a metric."""
    direction: str  # "increasing", "decreasing", "stable", "erratic"
    slope: float
    r_squared: float
    mean: float
    std_dev: float
    min_value: float
    max_value: float
    data_points: int


@dataclass
class FailurePrediction:
    """Predicted equipment failure."""
    component: str
    station_id: str
    prediction: str
    confidence: PredictionConfidence
    probability: float
    estimated_time_to_failure: Optional[timedelta]
    current_health: HealthStatus
    trend: TrendAnalysis
    recommended_action: str
    data_points_analyzed: int
    analysis_window: timedelta

    def to_dict(self) -> Dict[str, Any]:
        return {
            "component": self.component,
            "station_id": self.station_id,
            "prediction": self.prediction,
            "confidence": self.confidence.value,
            "probability": round(self.probability, 3),
            "estimated_hours_to_failure": self.estimated_time_to_failure.total_seconds() / 3600 if self.estimated_time_to_failure else None,
            "current_health": self.current_health.value,
            "trend": {
                "direction": self.trend.direction,
                "slope": round(self.trend.slope, 4),
                "r_squared": round(self.trend.r_squared, 3),
                "mean": round(self.trend.mean, 2),
                "std_dev": round(self.trend.std_dev, 2),
                "min": round(self.trend.min_value, 2),
                "max": round(self.trend.max_value, 2),
                "data_points": self.trend.data_points
            },
            "recommended_action": self.recommended_action,
            "analysis_window_hours": self.analysis_window.total_seconds() / 3600
        }


@dataclass
class ComponentHealth:
    """Overall health assessment for a component."""
    component: str
    station_id: str
    health_score: float  # 0-100
    status: HealthStatus
    metrics: Dict[str, TrendAnalysis]
    issues: List[str]
    predictions: List[FailurePrediction]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "component": self.component,
            "station_id": self.station_id,
            "health_score": round(self.health_score, 1),
            "status": self.status.value,
            "issues": self.issues,
            "prediction_count": len(self.predictions),
            "predictions": [p.to_dict() for p in self.predictions]
        }
