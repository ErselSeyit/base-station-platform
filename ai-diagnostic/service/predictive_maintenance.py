"""
Predictive Maintenance Service - AI-powered equipment failure prediction

Quick Win #3: Predictive Fan Failure
- Monitors fan RPM trends
- Detects degradation patterns (gradual slowdown, erratic behavior)
- Predicts failure before it occurs

Also includes:
- Temperature anomaly detection
- Power supply health monitoring
- General component health scoring
"""

import logging
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from collections import defaultdict
from enum import Enum
import statistics
import threading

import numpy as np

logger = logging.getLogger(__name__)


class HealthStatus(Enum):
    """Component health status."""
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    WARNING = "warning"
    CRITICAL = "critical"
    FAILED = "failed"


class PredictionConfidence(Enum):
    """Confidence level of predictions."""
    HIGH = "high"        # >80% confidence
    MEDIUM = "medium"    # 50-80% confidence
    LOW = "low"          # <50% confidence


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


class PredictiveMaintenanceService:
    """
    AI-powered predictive maintenance for base station equipment.

    Features:
    - Fan failure prediction from RPM trends
    - Temperature anomaly detection
    - Power supply health monitoring
    - Component health scoring
    """

    # Thresholds for fan health
    FAN_HEALTHY_RPM_MIN = 2000
    FAN_WARNING_RPM_MIN = 1500
    FAN_CRITICAL_RPM_MIN = 1000
    FAN_RPM_VARIATION_THRESHOLD = 0.15  # 15% variation is concerning
    FAN_DEGRADATION_SLOPE_THRESHOLD = -10  # RPM/hour decline

    # Thresholds for temperature
    TEMP_HEALTHY_MAX = 55  # Celsius
    TEMP_WARNING_MAX = 65
    TEMP_CRITICAL_MAX = 75

    # Thresholds for power
    VOLTAGE_TOLERANCE = 0.05  # 5% tolerance

    # Thresholds for battery health
    BATTERY_SOC_HEALTHY_MIN = 80  # %
    BATTERY_SOC_WARNING_MIN = 50
    BATTERY_SOC_CRITICAL_MIN = 20
    BATTERY_DOD_HEALTHY_MAX = 50  # % depth of discharge
    BATTERY_DOD_WARNING_MAX = 70
    BATTERY_DOD_CRITICAL_MAX = 85
    BATTERY_TEMP_HEALTHY_MAX = 35  # Celsius
    BATTERY_TEMP_WARNING_MAX = 45
    BATTERY_TEMP_CRITICAL_MAX = 55
    BATTERY_CYCLE_HEALTHY_MAX = 500  # cycles
    BATTERY_CYCLE_WARNING_MAX = 800
    BATTERY_CYCLE_CRITICAL_MAX = 1000
    BATTERY_CAPACITY_DEGRADATION_THRESHOLD = 0.1  # 10% capacity loss

    # Thresholds for fiber transport
    FIBER_RX_POWER_HEALTHY_MIN = -20  # dBm
    FIBER_RX_POWER_WARNING_MIN = -25
    FIBER_RX_POWER_CRITICAL_MIN = -30
    FIBER_TX_POWER_HEALTHY_MIN = -5
    FIBER_TX_POWER_WARNING_MIN = -8
    FIBER_TX_POWER_CRITICAL_MIN = -10
    FIBER_BER_HEALTHY_MAX = 1e-12  # Bit Error Rate
    FIBER_BER_WARNING_MAX = 1e-9
    FIBER_BER_CRITICAL_MAX = 1e-6
    FIBER_OSNR_HEALTHY_MIN = 25  # dB
    FIBER_OSNR_WARNING_MIN = 18
    FIBER_OSNR_CRITICAL_MIN = 12

    # Minimum data points for analysis
    MIN_DATA_POINTS = 10
    PREFERRED_DATA_POINTS = 100

    def __init__(self):
        self.historical_data: Dict[str, List[MetricDataPoint]] = defaultdict(list)
        logger.info("PredictiveMaintenanceService initialized")

    def add_metric(self, data_point: MetricDataPoint):
        """Add a metric data point to historical storage."""
        key = f"{data_point.station_id}:{data_point.metric_type}"
        self.historical_data[key].append(data_point)

        # Keep only last 7 days of data
        cutoff = datetime.now(timezone.utc) - timedelta(days=7)
        self.historical_data[key] = [
            dp for dp in self.historical_data[key]
            if dp.timestamp > cutoff
        ]

    def add_metrics_batch(self, data_points: List[MetricDataPoint]):
        """Add multiple metric data points."""
        for dp in data_points:
            self.add_metric(dp)

    def analyze_fan_health(
        self,
        station_id: str,
        analysis_window: timedelta = timedelta(hours=24)
    ) -> Optional[FailurePrediction]:
        """
        Analyze fan health and predict potential failures.

        Args:
            station_id: Base station identifier
            analysis_window: Time window for analysis

        Returns:
            FailurePrediction if issues detected, None otherwise
        """
        key = f"{station_id}:FAN_SPEED"
        data_points = self._get_recent_data(key, analysis_window)

        if len(data_points) < self.MIN_DATA_POINTS:
            return None

        # Perform trend analysis
        trend = self._analyze_trend(data_points)

        # Assess current health
        current_rpm = data_points[-1].value
        health_status = self._assess_fan_health_status(current_rpm, trend)

        # Calculate failure probability
        probability, ttf = self._calculate_fan_failure_probability(current_rpm, trend)

        # Determine confidence based on data quality
        confidence = self._determine_confidence(len(data_points), trend.r_squared)

        # Generate recommendation
        recommendation = self._get_fan_recommendation(health_status, trend, probability)

        if probability < 0.1 and health_status == HealthStatus.HEALTHY:
            return None  # No significant issues

        return FailurePrediction(
            component="cooling_fan",
            station_id=station_id,
            prediction=self._get_fan_prediction_text(health_status, trend, probability),
            confidence=confidence,
            probability=probability,
            estimated_time_to_failure=ttf,
            current_health=health_status,
            trend=trend,
            recommended_action=recommendation,
            data_points_analyzed=len(data_points),
            analysis_window=analysis_window
        )

    def analyze_temperature(
        self,
        station_id: str,
        analysis_window: timedelta = timedelta(hours=24)
    ) -> Optional[FailurePrediction]:
        """Analyze temperature trends and predict thermal issues."""
        key = f"{station_id}:TEMPERATURE"
        data_points = self._get_recent_data(key, analysis_window)

        if len(data_points) < self.MIN_DATA_POINTS:
            return None

        trend = self._analyze_trend(data_points)
        current_temp = data_points[-1].value

        # Assess health
        if current_temp >= self.TEMP_CRITICAL_MAX:
            health_status = HealthStatus.CRITICAL
        elif current_temp >= self.TEMP_WARNING_MAX:
            health_status = HealthStatus.WARNING
        elif current_temp >= self.TEMP_HEALTHY_MAX:
            health_status = HealthStatus.DEGRADED
        else:
            health_status = HealthStatus.HEALTHY

        # Check for rising temperature trend
        if trend.direction == "increasing" and trend.slope > 0.5:  # >0.5°C/hour
            probability = min(0.9, 0.3 + trend.slope * 0.2)
            hours_to_critical = (self.TEMP_CRITICAL_MAX - current_temp) / trend.slope if trend.slope > 0 else None
            ttf = timedelta(hours=hours_to_critical) if hours_to_critical and hours_to_critical > 0 else None
        else:
            probability = 0.1 if health_status != HealthStatus.HEALTHY else 0.0
            ttf = None

        if probability < 0.1 and health_status == HealthStatus.HEALTHY:
            return None

        confidence = self._determine_confidence(len(data_points), trend.r_squared)

        return FailurePrediction(
            component="thermal_system",
            station_id=station_id,
            prediction=f"Temperature at {current_temp:.1f}°C, trend: {trend.direction}",
            confidence=confidence,
            probability=probability,
            estimated_time_to_failure=ttf,
            current_health=health_status,
            trend=trend,
            recommended_action=self._get_temperature_recommendation(health_status, trend),
            data_points_analyzed=len(data_points),
            analysis_window=analysis_window
        )

    def analyze_power_supply(
        self,
        station_id: str,
        analysis_window: timedelta = timedelta(hours=24)
    ) -> Optional[FailurePrediction]:
        """Analyze power supply voltage and current for anomalies."""
        voltage_key = f"{station_id}:VOLTAGE"
        current_key = f"{station_id}:CURRENT"

        voltage_data = self._get_recent_data(voltage_key, analysis_window)
        current_data = self._get_recent_data(current_key, analysis_window)

        if len(voltage_data) < self.MIN_DATA_POINTS:
            return None

        voltage_trend = self._analyze_trend(voltage_data)
        current_trend = self._analyze_trend(current_data) if current_data else None

        # Check for voltage instability
        voltage_variation = voltage_trend.std_dev / voltage_trend.mean if voltage_trend.mean > 0 else 0

        if voltage_variation > self.VOLTAGE_TOLERANCE:
            health_status = HealthStatus.WARNING
            probability = min(0.8, voltage_variation * 5)
            prediction = f"Voltage instability detected: {voltage_variation*100:.1f}% variation"
            recommendation = "Check power supply connections and backup battery"
        elif voltage_trend.direction == "decreasing" and voltage_trend.slope < -0.01:
            health_status = HealthStatus.DEGRADED
            probability = 0.4
            prediction = "Gradual voltage decline detected"
            recommendation = "Monitor power supply and schedule inspection"
        else:
            return None  # No significant issues

        return FailurePrediction(
            component="power_supply",
            station_id=station_id,
            prediction=prediction,
            confidence=self._determine_confidence(len(voltage_data), voltage_trend.r_squared),
            probability=probability,
            estimated_time_to_failure=None,
            current_health=health_status,
            trend=voltage_trend,
            recommended_action=recommendation,
            data_points_analyzed=len(voltage_data),
            analysis_window=analysis_window
        )

    def analyze_battery_health(
        self,
        station_id: str,
        analysis_window: timedelta = timedelta(hours=24)
    ) -> Optional[FailurePrediction]:
        """
        Analyze battery health and predict degradation.

        Monitors:
        - State of Charge (SOC) trends
        - Depth of Discharge (DOD) patterns
        - Temperature stress
        - Cycle count degradation
        - Capacity fade estimation

        Args:
            station_id: Base station identifier
            analysis_window: Time window for analysis

        Returns:
            FailurePrediction if degradation detected, None otherwise
        """
        soc_key = f"{station_id}:BATTERY_SOC"
        dod_key = f"{station_id}:BATTERY_DOD"
        temp_key = f"{station_id}:BATTERY_TEMP"
        cycle_key = f"{station_id}:BATTERY_CYCLES"

        soc_data = self._get_recent_data(soc_key, analysis_window)
        dod_data = self._get_recent_data(dod_key, analysis_window)
        temp_data = self._get_recent_data(temp_key, analysis_window)
        cycle_data = self._get_recent_data(cycle_key, analysis_window)

        # Need at least SOC data
        if len(soc_data) < self.MIN_DATA_POINTS:
            return None

        soc_trend = self._analyze_trend(soc_data)
        dod_trend = self._analyze_trend(dod_data) if dod_data else None
        temp_trend = self._analyze_trend(temp_data) if temp_data else None

        current_soc = soc_data[-1].value
        current_dod = dod_data[-1].value if dod_data else 0
        current_temp = temp_data[-1].value if temp_data else 25
        current_cycles = cycle_data[-1].value if cycle_data else 0

        # Multi-factor health assessment
        health_factors = []
        issues = []

        # Factor 1: State of Charge assessment
        if current_soc < self.BATTERY_SOC_CRITICAL_MIN:
            health_factors.append(0.2)
            issues.append(f"Critical SOC: {current_soc:.1f}%")
        elif current_soc < self.BATTERY_SOC_WARNING_MIN:
            health_factors.append(0.5)
            issues.append(f"Low SOC: {current_soc:.1f}%")
        elif current_soc < self.BATTERY_SOC_HEALTHY_MIN:
            health_factors.append(0.8)
        else:
            health_factors.append(1.0)

        # Factor 2: Depth of Discharge stress
        if dod_trend:
            if current_dod > self.BATTERY_DOD_CRITICAL_MAX:
                health_factors.append(0.3)
                issues.append(f"Critical DOD: {current_dod:.1f}%")
            elif current_dod > self.BATTERY_DOD_WARNING_MAX:
                health_factors.append(0.6)
                issues.append(f"High DOD: {current_dod:.1f}%")
            elif current_dod > self.BATTERY_DOD_HEALTHY_MAX:
                health_factors.append(0.8)
            else:
                health_factors.append(1.0)

        # Factor 3: Temperature stress
        if temp_trend:
            if current_temp > self.BATTERY_TEMP_CRITICAL_MAX:
                health_factors.append(0.2)
                issues.append(f"Critical temperature: {current_temp:.1f}°C")
            elif current_temp > self.BATTERY_TEMP_WARNING_MAX:
                health_factors.append(0.5)
                issues.append(f"High temperature: {current_temp:.1f}°C")
            elif current_temp > self.BATTERY_TEMP_HEALTHY_MAX:
                health_factors.append(0.8)
            else:
                health_factors.append(1.0)

        # Factor 4: Cycle count degradation
        if current_cycles > 0:
            if current_cycles > self.BATTERY_CYCLE_CRITICAL_MAX:
                health_factors.append(0.3)
                issues.append(f"High cycle count: {current_cycles:.0f}")
            elif current_cycles > self.BATTERY_CYCLE_WARNING_MAX:
                health_factors.append(0.6)
                issues.append(f"Elevated cycles: {current_cycles:.0f}")
            elif current_cycles > self.BATTERY_CYCLE_HEALTHY_MAX:
                health_factors.append(0.8)
            else:
                health_factors.append(1.0)

        # Factor 5: SOC trend degradation (capacity fade indicator)
        if soc_trend.direction == "decreasing" and soc_trend.slope < -0.5:
            # SOC declining faster than normal charging/usage
            health_factors.append(0.6)
            issues.append(f"Capacity fade detected: {abs(soc_trend.slope):.2f}%/hr decline")

        # Calculate combined health score
        combined_health = sum(health_factors) / len(health_factors) if health_factors else 1.0

        # Determine health status
        if combined_health < 0.4:
            health_status = HealthStatus.CRITICAL
        elif combined_health < 0.6:
            health_status = HealthStatus.WARNING
        elif combined_health < 0.8:
            health_status = HealthStatus.DEGRADED
        else:
            health_status = HealthStatus.HEALTHY

        # Calculate failure probability
        probability = 1.0 - combined_health

        # Estimate time to failure based on capacity degradation
        ttf = None
        if soc_trend.direction == "decreasing" and soc_trend.slope < -0.1:
            hours_to_critical = (current_soc - self.BATTERY_SOC_CRITICAL_MIN) / abs(soc_trend.slope)
            if hours_to_critical > 0:
                ttf = timedelta(hours=hours_to_critical)

        if probability < 0.1 and health_status == HealthStatus.HEALTHY:
            return None

        # Generate prediction text
        if issues:
            prediction_text = f"Battery degradation: {', '.join(issues[:2])}"
        else:
            prediction_text = f"Battery health at {combined_health*100:.0f}%"

        # Generate recommendation
        recommendation = self._get_battery_recommendation(health_status, issues, current_cycles)

        confidence = self._determine_confidence(len(soc_data), soc_trend.r_squared)

        return FailurePrediction(
            component="battery_system",
            station_id=station_id,
            prediction=prediction_text,
            confidence=confidence,
            probability=probability,
            estimated_time_to_failure=ttf,
            current_health=health_status,
            trend=soc_trend,
            recommended_action=recommendation,
            data_points_analyzed=len(soc_data),
            analysis_window=analysis_window
        )

    def analyze_fiber_transport(
        self,
        station_id: str,
        analysis_window: timedelta = timedelta(hours=24)
    ) -> Optional[FailurePrediction]:
        """
        Analyze fiber optic transport health and predict link degradation.

        Monitors:
        - RX/TX optical power levels
        - Bit Error Rate (BER)
        - Optical Signal-to-Noise Ratio (OSNR)

        Args:
            station_id: Base station identifier
            analysis_window: Time window for analysis

        Returns:
            FailurePrediction if degradation detected, None otherwise
        """
        rx_power_key = f"{station_id}:FIBER_RX_POWER"
        tx_power_key = f"{station_id}:FIBER_TX_POWER"
        ber_key = f"{station_id}:FIBER_BER"
        osnr_key = f"{station_id}:FIBER_OSNR"

        rx_data = self._get_recent_data(rx_power_key, analysis_window)
        tx_data = self._get_recent_data(tx_power_key, analysis_window)
        ber_data = self._get_recent_data(ber_key, analysis_window)
        osnr_data = self._get_recent_data(osnr_key, analysis_window)

        # Need at least RX power data
        if len(rx_data) < self.MIN_DATA_POINTS:
            return None

        rx_trend = self._analyze_trend(rx_data)
        tx_trend = self._analyze_trend(tx_data) if tx_data else None
        ber_trend = self._analyze_trend(ber_data) if ber_data else None
        osnr_trend = self._analyze_trend(osnr_data) if osnr_data else None

        current_rx = rx_data[-1].value
        current_tx = tx_data[-1].value if tx_data else 0
        current_ber = ber_data[-1].value if ber_data else 0
        current_osnr = osnr_data[-1].value if osnr_data else 30

        health_factors = []
        issues = []

        # Factor 1: RX Power assessment
        if current_rx < self.FIBER_RX_POWER_CRITICAL_MIN:
            health_factors.append(0.2)
            issues.append(f"Critical RX power: {current_rx:.1f} dBm")
        elif current_rx < self.FIBER_RX_POWER_WARNING_MIN:
            health_factors.append(0.5)
            issues.append(f"Low RX power: {current_rx:.1f} dBm")
        elif current_rx < self.FIBER_RX_POWER_HEALTHY_MIN:
            health_factors.append(0.8)
        else:
            health_factors.append(1.0)

        # Factor 2: TX Power assessment
        if tx_trend:
            if current_tx < self.FIBER_TX_POWER_CRITICAL_MIN:
                health_factors.append(0.3)
                issues.append(f"Critical TX power: {current_tx:.1f} dBm")
            elif current_tx < self.FIBER_TX_POWER_WARNING_MIN:
                health_factors.append(0.6)
                issues.append(f"Low TX power: {current_tx:.1f} dBm")
            elif current_tx < self.FIBER_TX_POWER_HEALTHY_MIN:
                health_factors.append(0.8)
            else:
                health_factors.append(1.0)

        # Factor 3: BER assessment (lower is better)
        if ber_trend and current_ber > 0:
            if current_ber > self.FIBER_BER_CRITICAL_MAX:
                health_factors.append(0.2)
                issues.append(f"Critical BER: {current_ber:.2e}")
            elif current_ber > self.FIBER_BER_WARNING_MAX:
                health_factors.append(0.5)
                issues.append(f"High BER: {current_ber:.2e}")
            elif current_ber > self.FIBER_BER_HEALTHY_MAX:
                health_factors.append(0.8)
            else:
                health_factors.append(1.0)

        # Factor 4: OSNR assessment (higher is better)
        if osnr_trend:
            if current_osnr < self.FIBER_OSNR_CRITICAL_MIN:
                health_factors.append(0.2)
                issues.append(f"Critical OSNR: {current_osnr:.1f} dB")
            elif current_osnr < self.FIBER_OSNR_WARNING_MIN:
                health_factors.append(0.5)
                issues.append(f"Low OSNR: {current_osnr:.1f} dB")
            elif current_osnr < self.FIBER_OSNR_HEALTHY_MIN:
                health_factors.append(0.8)
            else:
                health_factors.append(1.0)

        # Factor 5: Power degradation trend
        if rx_trend.direction == "decreasing" and rx_trend.slope < -0.1:
            # RX power declining - potential fiber degradation
            health_factors.append(0.6)
            issues.append(f"RX power declining: {abs(rx_trend.slope):.2f} dBm/hr")

        # Calculate combined health
        combined_health = sum(health_factors) / len(health_factors) if health_factors else 1.0

        # Determine health status
        if combined_health < 0.4:
            health_status = HealthStatus.CRITICAL
        elif combined_health < 0.6:
            health_status = HealthStatus.WARNING
        elif combined_health < 0.8:
            health_status = HealthStatus.DEGRADED
        else:
            health_status = HealthStatus.HEALTHY

        probability = 1.0 - combined_health

        # Estimate time to failure
        ttf = None
        if rx_trend.direction == "decreasing" and rx_trend.slope < -0.05:
            hours_to_critical = (current_rx - self.FIBER_RX_POWER_CRITICAL_MIN) / abs(rx_trend.slope)
            if hours_to_critical > 0:
                ttf = timedelta(hours=hours_to_critical)

        if probability < 0.1 and health_status == HealthStatus.HEALTHY:
            return None

        # Generate prediction text
        if issues:
            prediction_text = f"Fiber link degradation: {', '.join(issues[:2])}"
        else:
            prediction_text = f"Fiber link health at {combined_health*100:.0f}%"

        has_ber_issue = any("BER" in issue for issue in issues)
        recommendation = self._get_fiber_recommendation(health_status, has_ber_issue, rx_trend)

        confidence = self._determine_confidence(len(rx_data), rx_trend.r_squared)

        return FailurePrediction(
            component="fiber_transport",
            station_id=station_id,
            prediction=prediction_text,
            confidence=confidence,
            probability=probability,
            estimated_time_to_failure=ttf,
            current_health=health_status,
            trend=rx_trend,
            recommended_action=recommendation,
            data_points_analyzed=len(rx_data),
            analysis_window=analysis_window
        )

    def _get_battery_recommendation(
        self,
        status: HealthStatus,
        issues: List[str],
        cycle_count: float
    ) -> str:
        """Get recommended action for battery issues."""
        if status == HealthStatus.CRITICAL:
            return "URGENT: Battery replacement required. Risk of power failure"
        elif status == HealthStatus.WARNING:
            if cycle_count > self.BATTERY_CYCLE_WARNING_MAX:
                return "Schedule battery replacement within 30 days due to cycle degradation"
            return "Monitor battery closely. Schedule inspection within 1 week"
        elif status == HealthStatus.DEGRADED:
            return "Battery showing early degradation. Plan for replacement within 3 months"
        else:
            return "Battery operating normally. Continue standard maintenance"

    def _get_fiber_recommendation(
        self,
        status: HealthStatus,
        has_ber_issue: bool,
        rx_trend: TrendAnalysis
    ) -> str:
        """Get recommended action for fiber transport issues."""
        if status == HealthStatus.CRITICAL:
            return "URGENT: Fiber link at risk of failure. Inspect connectors, check for fiber damage"
        elif status == HealthStatus.WARNING:
            if has_ber_issue:
                return "High error rate detected. Clean connectors, verify SFP modules"
            return "Signal degradation detected. Schedule OTDR test within 1 week"
        elif status == HealthStatus.DEGRADED:
            if rx_trend.direction == "decreasing":
                return "Gradual signal loss. Check for connector contamination or bend loss"
            return "Monitor fiber link. Schedule preventive inspection"
        else:
            return "Fiber transport operating normally"

    def get_station_health_report(
        self,
        station_id: str,
        analysis_window: timedelta = timedelta(hours=24)
    ) -> Dict[str, Any]:
        """
        Generate comprehensive health report for a station.

        Args:
            station_id: Base station identifier
            analysis_window: Time window for analysis

        Returns:
            Health report dictionary
        """
        predictions = []

        # Analyze all components
        fan_pred = self.analyze_fan_health(station_id, analysis_window)
        if fan_pred:
            predictions.append(fan_pred)

        temp_pred = self.analyze_temperature(station_id, analysis_window)
        if temp_pred:
            predictions.append(temp_pred)

        power_pred = self.analyze_power_supply(station_id, analysis_window)
        if power_pred:
            predictions.append(power_pred)

        battery_pred = self.analyze_battery_health(station_id, analysis_window)
        if battery_pred:
            predictions.append(battery_pred)

        fiber_pred = self.analyze_fiber_transport(station_id, analysis_window)
        if fiber_pred:
            predictions.append(fiber_pred)

        # Calculate overall health score
        if predictions:
            health_score = 100 - sum(p.probability * 50 for p in predictions)
            health_score = max(0, min(100, health_score))

            # Determine overall status
            statuses = [p.current_health for p in predictions]
            if HealthStatus.CRITICAL in statuses:
                overall_status = HealthStatus.CRITICAL
            elif HealthStatus.WARNING in statuses:
                overall_status = HealthStatus.WARNING
            elif HealthStatus.DEGRADED in statuses:
                overall_status = HealthStatus.DEGRADED
            else:
                overall_status = HealthStatus.HEALTHY
        else:
            health_score = 100
            overall_status = HealthStatus.HEALTHY

        return {
            "station_id": station_id,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "overall_health_score": round(health_score, 1),
            "overall_status": overall_status.value,
            "analysis_window_hours": analysis_window.total_seconds() / 3600,
            "predictions": [p.to_dict() for p in predictions],
            "issues_detected": len(predictions),
            "maintenance_recommended": any(
                p.probability > 0.5 or p.current_health in [HealthStatus.CRITICAL, HealthStatus.WARNING]
                for p in predictions
            )
        }

    def _get_recent_data(
        self,
        key: str,
        window: timedelta
    ) -> List[MetricDataPoint]:
        """Get recent data points within the specified window."""
        cutoff = datetime.now(timezone.utc) - window
        data = self.historical_data.get(key, [])
        return [dp for dp in data if dp.timestamp > cutoff]

    def _analyze_trend(self, data_points: List[MetricDataPoint]) -> TrendAnalysis:
        """Perform trend analysis on metric data."""
        if not data_points:
            return TrendAnalysis(
                direction="unknown", slope=0, r_squared=0,
                mean=0, std_dev=0, min_value=0, max_value=0, data_points=0
            )

        values = [dp.value for dp in data_points]
        timestamps = [(dp.timestamp - data_points[0].timestamp).total_seconds() / 3600
                     for dp in data_points]  # Hours since first point

        mean_val = statistics.mean(values)
        std_dev = statistics.stdev(values) if len(values) > 1 else 0

        # Linear regression for trend
        if len(values) > 1:
            coeffs = np.polyfit(timestamps, values, 1)
            slope = coeffs[0]

            # Calculate R-squared
            predicted = np.polyval(coeffs, timestamps)
            ss_res = np.sum((np.array(values) - predicted) ** 2)
            ss_tot = np.sum((np.array(values) - mean_val) ** 2)
            r_squared = 1 - (ss_res / ss_tot) if ss_tot > 0 else 0
        else:
            slope = 0
            r_squared = 0

        # Determine direction
        cv = std_dev / mean_val if mean_val > 0 else 0
        if cv > 0.2:
            direction = "erratic"
        elif abs(slope) < 0.1:
            direction = "stable"
        elif slope > 0:
            direction = "increasing"
        else:
            direction = "decreasing"

        return TrendAnalysis(
            direction=direction,
            slope=slope,
            r_squared=max(0, min(1, r_squared)),
            mean=mean_val,
            std_dev=std_dev,
            min_value=min(values),
            max_value=max(values),
            data_points=len(values)
        )

    def _assess_fan_health_status(self, current_rpm: float, trend: TrendAnalysis) -> HealthStatus:
        """Assess fan health status based on RPM and trend."""
        if current_rpm < self.FAN_CRITICAL_RPM_MIN:
            return HealthStatus.CRITICAL
        elif current_rpm < self.FAN_WARNING_RPM_MIN:
            return HealthStatus.WARNING
        elif current_rpm < self.FAN_HEALTHY_RPM_MIN:
            return HealthStatus.DEGRADED
        elif trend.direction == "erratic":
            return HealthStatus.WARNING
        elif trend.direction == "decreasing" and trend.slope < self.FAN_DEGRADATION_SLOPE_THRESHOLD:
            return HealthStatus.DEGRADED
        else:
            return HealthStatus.HEALTHY

    def _calculate_fan_failure_probability(
        self,
        current_rpm: float,
        trend: TrendAnalysis
    ) -> Tuple[float, Optional[timedelta]]:
        """Calculate probability of fan failure and estimated time to failure."""
        probability = 0.0
        ttf = None

        # Base probability from current RPM
        if current_rpm < self.FAN_CRITICAL_RPM_MIN:
            probability = 0.9
        elif current_rpm < self.FAN_WARNING_RPM_MIN:
            probability = 0.6
        elif current_rpm < self.FAN_HEALTHY_RPM_MIN:
            probability = 0.3

        # Adjust for trend
        if trend.direction == "decreasing" and trend.slope < 0:
            # Calculate hours until critical RPM
            hours_to_critical = (current_rpm - self.FAN_CRITICAL_RPM_MIN) / abs(trend.slope)
            if hours_to_critical > 0:
                ttf = timedelta(hours=hours_to_critical)
                # Higher probability if failure imminent
                if hours_to_critical < 24:
                    probability = max(probability, 0.8)
                elif hours_to_critical < 72:
                    probability = max(probability, 0.5)

        # Adjust for erratic behavior
        if trend.direction == "erratic":
            probability = max(probability, 0.5)

        # High variation is concerning
        cv = trend.std_dev / trend.mean if trend.mean > 0 else 0
        if cv > self.FAN_RPM_VARIATION_THRESHOLD:
            probability = max(probability, 0.4)

        return min(1.0, probability), ttf

    def _determine_confidence(self, data_points: int, r_squared: float) -> PredictionConfidence:
        """Determine prediction confidence based on data quality."""
        if data_points >= self.PREFERRED_DATA_POINTS and r_squared > 0.7:
            return PredictionConfidence.HIGH
        elif data_points >= self.MIN_DATA_POINTS and r_squared > 0.4:
            return PredictionConfidence.MEDIUM
        else:
            return PredictionConfidence.LOW

    def _get_fan_prediction_text(
        self,
        status: HealthStatus,
        trend: TrendAnalysis,
        probability: float
    ) -> str:
        """Generate human-readable prediction text for fan status."""
        if status == HealthStatus.CRITICAL:
            return f"Fan operating at critical RPM ({trend.mean:.0f}), immediate attention required"
        elif status == HealthStatus.WARNING:
            return f"Fan degradation detected, RPM trend: {trend.direction} ({trend.slope:.1f}/hr)"
        elif trend.direction == "decreasing":
            return f"Fan RPM declining at {abs(trend.slope):.1f}/hr, monitor closely"
        elif trend.direction == "erratic":
            return f"Erratic fan behavior detected (±{trend.std_dev:.0f} RPM variation)"
        else:
            return f"Fan operating normally at {trend.mean:.0f} RPM"

    def _get_fan_recommendation(
        self,
        status: HealthStatus,
        trend: TrendAnalysis,
        probability: float
    ) -> str:
        """Get recommended action for fan issues."""
        if status == HealthStatus.CRITICAL or probability > 0.8:
            return "URGENT: Schedule immediate fan replacement to prevent thermal shutdown"
        elif status == HealthStatus.WARNING or probability > 0.5:
            return "Schedule fan replacement within 1 week"
        elif status == HealthStatus.DEGRADED:
            return "Monitor fan closely, schedule inspection within 2 weeks"
        else:
            return "Continue normal monitoring"

    def _get_temperature_recommendation(
        self,
        status: HealthStatus,
        trend: TrendAnalysis
    ) -> str:
        """Get recommended action for temperature issues."""
        if status == HealthStatus.CRITICAL:
            return "CRITICAL: Temperature exceeds safe limits. Check cooling system immediately"
        elif status == HealthStatus.WARNING:
            return "High temperature warning. Verify cooling system operation"
        elif trend.direction == "increasing" and trend.slope > 0.5:
            return "Rising temperature trend. Check airflow and cooling capacity"
        else:
            return "Temperature within normal range"


# Singleton instance with thread-safe initialization
_maintenance_service: Optional[PredictiveMaintenanceService] = None
_maintenance_service_lock = threading.Lock()


def get_predictive_maintenance_service() -> PredictiveMaintenanceService:
    """Get or create singleton PredictiveMaintenanceService instance (thread-safe)."""
    global _maintenance_service
    if _maintenance_service is None:
        with _maintenance_service_lock:
            if _maintenance_service is None:  # Double-check locking
                _maintenance_service = PredictiveMaintenanceService()
    return _maintenance_service
