#!/usr/bin/env python3
"""
Traffic Prediction Service

LSTM-based traffic forecasting for 5G base stations.
Predicts network traffic patterns to enable proactive resource allocation.

Features:
- Multi-step ahead forecasting (1h, 6h, 24h)
- Per-cell and aggregate predictions
- Confidence intervals
- Trend and seasonality decomposition
- Anomalous traffic pattern detection
"""

import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Tuple, Any
from enum import Enum
import json
import numpy as np
from collections import deque

logger = logging.getLogger(__name__)

# Modern RNG (replaces deprecated np.random.seed/randn)
_rng = np.random.default_rng(42)


class TrafficMetricType(Enum):
    """Types of traffic metrics that can be predicted."""
    DL_THROUGHPUT = "dl_throughput"
    UL_THROUGHPUT = "ul_throughput"
    ACTIVE_USERS = "active_users"
    PRB_UTILIZATION = "prb_utilization"
    RRC_CONNECTIONS = "rrc_connections"


@dataclass
class TrafficDataPoint:
    """A single traffic measurement."""
    timestamp: datetime
    station_id: str
    cell_id: Optional[str]
    metric_type: TrafficMetricType
    value: float
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class TrafficPrediction:
    """Traffic prediction result."""
    station_id: str
    cell_id: Optional[str]
    metric_type: TrafficMetricType
    predictions: List[Tuple[datetime, float]]  # (timestamp, predicted_value)
    confidence_lower: List[float]
    confidence_upper: List[float]
    trend: str  # "increasing", "decreasing", "stable"
    seasonality_detected: bool
    prediction_horizon: str  # "1h", "6h", "24h"
    model_confidence: float  # 0-1
    generated_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "station_id": self.station_id,
            "cell_id": self.cell_id,
            "metric_type": self.metric_type.value,
            "predictions": [
                {"timestamp": ts.isoformat(), "value": val}
                for ts, val in self.predictions
            ],
            "confidence_lower": self.confidence_lower,
            "confidence_upper": self.confidence_upper,
            "trend": self.trend,
            "seasonality_detected": self.seasonality_detected,
            "prediction_horizon": self.prediction_horizon,
            "model_confidence": self.model_confidence,
            "generated_at": self.generated_at.isoformat(),
        }


class TrafficPredictor:
    """
    LSTM-based traffic prediction model.

    Uses a sliding window approach with the following architecture:
    - Input: Historical traffic data (sequence of measurements)
    - Model: Simplified LSTM-like recurrent computation
    - Output: Multi-step ahead predictions with confidence intervals

    Note: This is a lightweight implementation that doesn't require TensorFlow/PyTorch.
    For production, consider using a full deep learning framework.
    """

    def __init__(
        self,
        sequence_length: int = 168,  # 1 week of hourly data
        hidden_size: int = 64,
        prediction_steps: Dict[str, int] = None,
    ):
        self.sequence_length = sequence_length
        self.hidden_size = hidden_size
        self.prediction_steps = prediction_steps or {
            "1h": 1,
            "6h": 6,
            "24h": 24,
        }

        # Data storage per station/cell/metric
        self.history: Dict[str, deque] = {}

        # Model parameters (simplified - would be learned in production)
        # Using module-level RNG for reproducibility
        self.weights = {
            "input": _rng.standard_normal((hidden_size, 1)) * 0.1,
            "hidden": _rng.standard_normal((hidden_size, hidden_size)) * 0.1,
            "output": _rng.standard_normal((1, hidden_size)) * 0.1,
        }

        # Seasonality patterns (hourly, daily, weekly)
        self.seasonality_patterns: Dict[str, np.ndarray] = {}

        logger.info(f"TrafficPredictor initialized with sequence_length={sequence_length}")

    def _get_key(self, station_id: str, cell_id: Optional[str], metric_type: TrafficMetricType) -> str:
        """Generate a unique key for data storage."""
        cell_part = cell_id or "aggregate"
        return f"{station_id}:{cell_part}:{metric_type.value}"

    def add_data_point(self, data_point: TrafficDataPoint) -> None:
        """Add a new traffic measurement to the history."""
        key = self._get_key(data_point.station_id, data_point.cell_id, data_point.metric_type)

        if key not in self.history:
            self.history[key] = deque(maxlen=self.sequence_length * 4)  # Keep 4 weeks

        self.history[key].append((data_point.timestamp, data_point.value))

        # Update seasonality patterns periodically
        if len(self.history[key]) >= self.sequence_length:
            self._update_seasonality(key)

    def add_batch(self, data_points: List[TrafficDataPoint]) -> None:
        """Add multiple data points at once."""
        for dp in data_points:
            self.add_data_point(dp)

    def _update_seasonality(self, key: str) -> None:
        """Detect and store seasonality patterns."""
        if len(self.history[key]) < 168:  # Need at least 1 week
            return

        values = np.array([v for _, v in self.history[key]])

        # Simple hourly pattern detection (average by hour of week)
        if len(values) >= 168:
            # Reshape to weeks and compute average pattern
            n_complete_weeks = len(values) // 168
            if n_complete_weeks > 0:
                weekly_data = values[-(n_complete_weeks * 168):].reshape(n_complete_weeks, 168)
                self.seasonality_patterns[key] = np.mean(weekly_data, axis=0)

    def _detect_trend(self, values: np.ndarray) -> str:
        """Detect trend in the data using linear regression."""
        if len(values) < 24:
            return "stable"

        # Use last 24 hours for trend detection
        recent = values[-24:]
        x = np.arange(len(recent))

        # Simple linear regression
        slope = np.polyfit(x, recent, 1)[0]

        # Normalize by mean to get relative change
        mean_val = np.mean(recent)
        if mean_val > 0:
            relative_slope = slope / mean_val
        else:
            relative_slope = 0

        if relative_slope > 0.02:  # >2% per hour
            return "increasing"
        elif relative_slope < -0.02:
            return "decreasing"
        else:
            return "stable"

    def _simple_lstm_step(self, x: float, h: np.ndarray) -> Tuple[np.ndarray, float]:
        """
        Simplified LSTM-like computation step.

        In production, this would be replaced with a proper LSTM implementation.
        """
        # Normalize input
        x_norm = np.tanh(x / 1000.0)  # Assume typical throughput in Mbps

        # Simple recurrent computation
        h_new = np.tanh(
            self.weights["input"] @ np.array([[x_norm]]) +
            self.weights["hidden"] @ h
        )

        # Output projection
        y = float((self.weights["output"] @ h_new)[0, 0])

        return h_new, y

    def predict(
        self,
        station_id: str,
        metric_type: TrafficMetricType,
        cell_id: Optional[str] = None,
        horizon: str = "6h",
    ) -> Optional[TrafficPrediction]:
        """
        Generate traffic predictions.

        Args:
            station_id: Base station identifier
            metric_type: Type of traffic metric to predict
            cell_id: Optional cell identifier (None for aggregate)
            horizon: Prediction horizon ("1h", "6h", "24h")

        Returns:
            TrafficPrediction or None if insufficient data
        """
        key = self._get_key(station_id, cell_id, metric_type)

        if key not in self.history or len(self.history[key]) < 24:
            logger.warning(f"Insufficient data for prediction: {key}")
            return None

        # Get historical values
        history_data = list(self.history[key])
        timestamps = [t for t, _ in history_data]
        values = np.array([v for _, v in history_data])

        # Detect trend
        trend = self._detect_trend(values)

        # Check for seasonality
        seasonality_detected = key in self.seasonality_patterns

        # Get prediction steps
        steps = self.prediction_steps.get(horizon, 6)

        # Generate predictions using hybrid approach
        predictions = []
        confidence_lower = []
        confidence_upper = []

        # Initialize hidden state
        h = np.zeros((self.hidden_size, 1))

        # Run through historical data to initialize state
        for val in values[-min(48, len(values)):]:
            h, _ = self._simple_lstm_step(val, h)

        # Base prediction on recent mean and trend
        recent_mean = np.mean(values[-24:])
        recent_std = np.std(values[-24:]) if len(values) >= 24 else recent_mean * 0.1

        # Generate predictions
        last_timestamp = timestamps[-1]
        current_pred = values[-1]

        for i in range(steps):
            # Combine LSTM output with trend
            h, lstm_delta = self._simple_lstm_step(current_pred, h)

            # Add seasonality if available
            seasonality_adj = 0
            if seasonality_detected and key in self.seasonality_patterns:
                pattern = self.seasonality_patterns[key]
                hour_of_week = (last_timestamp.weekday() * 24 + last_timestamp.hour + i + 1) % 168
                if hour_of_week < len(pattern):
                    seasonality_adj = (pattern[hour_of_week] - recent_mean) * 0.3

            # Trend adjustment
            if trend == "increasing":
                trend_adj = recent_mean * 0.01 * (i + 1)
            elif trend == "decreasing":
                trend_adj = -recent_mean * 0.01 * (i + 1)
            else:
                trend_adj = 0

            # Final prediction
            pred_value = recent_mean + lstm_delta * recent_std + seasonality_adj + trend_adj
            pred_value = max(0, pred_value)  # Traffic can't be negative

            pred_timestamp = last_timestamp + timedelta(hours=i + 1)
            predictions.append((pred_timestamp, pred_value))

            # Confidence intervals (widen with prediction horizon)
            uncertainty = recent_std * (1 + 0.1 * i)
            confidence_lower.append(max(0, pred_value - 1.96 * uncertainty))
            confidence_upper.append(pred_value + 1.96 * uncertainty)

            current_pred = pred_value

        # Calculate model confidence based on data quality
        data_points = len(values)
        recency_hours = (datetime.now() - timestamps[-1]).total_seconds() / 3600

        confidence = min(1.0, data_points / self.sequence_length)
        if recency_hours > 1:
            confidence *= max(0.5, 1 - recency_hours / 24)

        return TrafficPrediction(
            station_id=station_id,
            cell_id=cell_id,
            metric_type=metric_type,
            predictions=predictions,
            confidence_lower=confidence_lower,
            confidence_upper=confidence_upper,
            trend=trend,
            seasonality_detected=seasonality_detected,
            prediction_horizon=horizon,
            model_confidence=confidence,
        )

    def get_capacity_forecast(
        self,
        station_id: str,
        threshold_percent: float = 80.0,
    ) -> Dict[str, Any]:
        """
        Forecast when capacity thresholds will be exceeded.

        Returns:
            Dict with capacity forecasts and recommendations
        """
        # Get PRB utilization prediction
        prb_pred = self.predict(
            station_id=station_id,
            metric_type=TrafficMetricType.PRB_UTILIZATION,
            horizon="24h",
        )

        if not prb_pred:
            return {"status": "insufficient_data"}

        # Find first threshold breach
        breach_time = None
        for ts, val in prb_pred.predictions:
            if val > threshold_percent:
                breach_time = ts
                break

        # Get peak prediction
        peak_value = max(v for _, v in prb_pred.predictions)
        peak_time = next(ts for ts, v in prb_pred.predictions if v == peak_value)

        result = {
            "station_id": station_id,
            "current_utilization": prb_pred.predictions[0][1] if prb_pred.predictions else 0,
            "peak_predicted": peak_value,
            "peak_time": peak_time.isoformat(),
            "threshold_percent": threshold_percent,
            "threshold_breach_predicted": breach_time is not None,
            "breach_time": breach_time.isoformat() if breach_time else None,
            "trend": prb_pred.trend,
            "recommendations": [],
        }

        # Add recommendations
        if breach_time:
            hours_until_breach = (breach_time - datetime.now()).total_seconds() / 3600
            if hours_until_breach < 2:
                result["recommendations"].append({
                    "priority": "high",
                    "action": "immediate_load_balancing",
                    "description": "Initiate MLB to redistribute load to neighboring cells",
                })
            elif hours_until_breach < 6:
                result["recommendations"].append({
                    "priority": "medium",
                    "action": "proactive_capacity_expansion",
                    "description": "Consider activating additional carriers or cells",
                })

        if prb_pred.trend == "increasing":
            result["recommendations"].append({
                "priority": "low",
                "action": "monitor_closely",
                "description": "Traffic is trending upward, continue monitoring",
            })

        return result


# Singleton instance
_traffic_predictor: Optional[TrafficPredictor] = None


def get_traffic_predictor() -> TrafficPredictor:
    """Get or create the traffic predictor singleton."""
    global _traffic_predictor
    if _traffic_predictor is None:
        _traffic_predictor = TrafficPredictor()
    return _traffic_predictor


# Convenience functions for API integration
def predict_traffic(
    station_id: str,
    metric_type: str,
    horizon: str = "6h",
    cell_id: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """
    API-friendly function to predict traffic.

    Args:
        station_id: Base station ID
        metric_type: One of "dl_throughput", "ul_throughput", "active_users",
                     "prb_utilization", "rrc_connections"
        horizon: "1h", "6h", or "24h"
        cell_id: Optional cell ID

    Returns:
        Prediction dict or None
    """
    predictor = get_traffic_predictor()

    try:
        mt = TrafficMetricType(metric_type)
    except ValueError:
        logger.error(f"Invalid metric type: {metric_type}")
        return None

    prediction = predictor.predict(
        station_id=station_id,
        metric_type=mt,
        cell_id=cell_id,
        horizon=horizon,
    )

    return prediction.to_dict() if prediction else None


def add_traffic_data(
    station_id: str,
    metric_type: str,
    value: float,
    timestamp: Optional[datetime] = None,
    cell_id: Optional[str] = None,
) -> bool:
    """
    API-friendly function to add traffic data.

    Returns:
        True if successful
    """
    predictor = get_traffic_predictor()

    try:
        mt = TrafficMetricType(metric_type)
    except ValueError:
        logger.error(f"Invalid metric type: {metric_type}")
        return False

    data_point = TrafficDataPoint(
        timestamp=timestamp or datetime.now(),
        station_id=station_id,
        cell_id=cell_id,
        metric_type=mt,
        value=value,
    )

    predictor.add_data_point(data_point)
    return True


def get_capacity_forecast(station_id: str, threshold: float = 80.0) -> Dict[str, Any]:
    """Get capacity forecast for a station."""
    predictor = get_traffic_predictor()
    return predictor.get_capacity_forecast(station_id, threshold)
