"""
Digital Twin Service for Base Station Simulation.

Provides virtual replicas of physical base stations for:
- What-if scenario analysis
- Failure prediction simulation
- Configuration change impact assessment
- Training data generation for ML models
"""

import json
import logging
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum
from typing import Any, Callable, Dict, List, Optional

import numpy as np

logger = logging.getLogger(__name__)

# Modern RNG (replaces deprecated np.random functions)
_rng = np.random.default_rng(42)


class TwinState(Enum):
    """Digital twin operational state."""
    INITIALIZING = "initializing"
    SYNCHRONIZED = "synchronized"
    SIMULATING = "simulating"
    DIVERGED = "diverged"
    OFFLINE = "offline"


class SimulationMode(Enum):
    """Simulation mode for digital twin."""
    REAL_TIME = "real_time"          # Mirror physical asset in real-time
    ACCELERATED = "accelerated"       # Fast-forward simulation
    WHAT_IF = "what_if"              # Hypothetical scenario
    REPLAY = "replay"                 # Historical replay


@dataclass
class ComponentModel:
    """Model of a base station component."""
    component_id: str
    component_type: str  # antenna, radio, power, cooling, etc.
    parameters: Dict[str, float] = field(default_factory=dict)
    health_score: float = 1.0
    degradation_rate: float = 0.0  # per day
    failure_probability: float = 0.0
    mtbf_hours: float = 50000.0  # Mean time between failures
    mttr_hours: float = 4.0      # Mean time to repair

    def simulate_step(self, hours: float = 1.0) -> Dict[str, Any]:
        """Simulate one time step for this component."""
        # Apply degradation
        self.health_score = max(0.0, self.health_score - (self.degradation_rate * hours / 24))

        # Calculate failure probability based on health
        base_failure_rate = 1.0 / self.mtbf_hours
        health_factor = 1.0 + (1.0 - self.health_score) * 5  # Worse health = higher failure rate
        self.failure_probability = min(1.0, base_failure_rate * health_factor * hours)

        # Check for failure
        failed = _rng.random() < self.failure_probability

        return {
            "component_id": self.component_id,
            "health_score": self.health_score,
            "failure_probability": self.failure_probability,
            "failed": failed,
            "parameters": self.parameters.copy()
        }


@dataclass
class EnvironmentModel:
    """Model of environmental conditions."""
    temperature: float = 25.0      # Celsius
    humidity: float = 50.0         # Percent
    wind_speed: float = 5.0        # km/h
    precipitation: float = 0.0     # mm/h
    solar_irradiance: float = 500  # W/m²

    def simulate_step(self, hours: float = 1.0) -> Dict[str, float]:
        """Simulate environmental changes."""
        # Add realistic noise and patterns
        hour_of_day = (datetime.now().hour + hours) % 24

        # Temperature follows daily cycle
        temp_variation = 8 * np.sin((hour_of_day - 6) * np.pi / 12)
        self.temperature = 20 + temp_variation + _rng.normal(0, 2)

        # Humidity inversely related to temperature
        self.humidity = max(20, min(95, 70 - temp_variation * 2 + _rng.normal(0, 5)))

        # Wind speed random walk
        self.wind_speed = max(0, self.wind_speed + _rng.normal(0, 2))

        # Solar follows day cycle
        if 6 <= hour_of_day <= 18:
            self.solar_irradiance = 800 * np.sin((hour_of_day - 6) * np.pi / 12)
        else:
            self.solar_irradiance = 0

        return {
            "temperature": self.temperature,
            "humidity": self.humidity,
            "wind_speed": self.wind_speed,
            "precipitation": self.precipitation,
            "solar_irradiance": self.solar_irradiance
        }


@dataclass
class TrafficModel:
    """Model of network traffic patterns."""
    base_load: float = 0.3         # Base utilization (0-1)
    peak_multiplier: float = 3.0   # Peak vs base ratio
    current_load: float = 0.3
    connected_users: int = 0
    throughput_mbps: float = 0.0

    def simulate_step(self, hours: float = 1.0) -> Dict[str, float]:
        """Simulate traffic patterns."""
        hour_of_day = (datetime.now().hour + hours) % 24

        # Traffic pattern: low at night, peaks at morning and evening
        if 7 <= hour_of_day <= 9:  # Morning peak
            load_factor = 0.8
        elif 17 <= hour_of_day <= 21:  # Evening peak
            load_factor = 1.0
        elif 23 <= hour_of_day or hour_of_day <= 5:  # Night low
            load_factor = 0.2
        else:
            load_factor = 0.5

        self.current_load = self.base_load * (1 + (self.peak_multiplier - 1) * load_factor)
        self.current_load += _rng.normal(0, 0.05)
        self.current_load = max(0.05, min(0.95, self.current_load))

        # Derive metrics from load
        max_capacity_mbps = 1000  # 1 Gbps max
        self.throughput_mbps = max_capacity_mbps * self.current_load
        self.connected_users = int(self.current_load * 500 * (1 + _rng.normal(0, 0.1)))

        return {
            "current_load": self.current_load,
            "throughput_mbps": self.throughput_mbps,
            "connected_users": self.connected_users
        }


@dataclass
class SimulationResult:
    """Result of a simulation run."""
    simulation_id: str
    twin_id: str
    station_id: str
    mode: SimulationMode
    start_time: datetime
    end_time: datetime
    duration_hours: float
    time_steps: int
    events: List[Dict[str, Any]] = field(default_factory=list)
    metrics_history: List[Dict[str, Any]] = field(default_factory=list)
    failures_detected: List[Dict[str, Any]] = field(default_factory=list)
    recommendations: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "simulation_id": self.simulation_id,
            "twin_id": self.twin_id,
            "station_id": self.station_id,
            "mode": self.mode.value,
            "start_time": self.start_time.isoformat(),
            "end_time": self.end_time.isoformat(),
            "duration_hours": self.duration_hours,
            "time_steps": self.time_steps,
            "total_events": len(self.events),
            "failures_detected": len(self.failures_detected),
            "recommendations": self.recommendations
        }


class DigitalTwin:
    """Digital twin of a single base station."""

    def __init__(self, station_id: str, station_data: Optional[Dict] = None):
        self.twin_id = str(uuid.uuid4())
        self.station_id = station_id
        self.state = TwinState.INITIALIZING
        self.created_at = datetime.now()
        self.last_sync = None
        self.simulation_clock = datetime.now()

        # Initialize models
        self.components: Dict[str, ComponentModel] = {}
        self.environment = EnvironmentModel()
        self.traffic = TrafficModel()

        # State tracking
        self.metrics_buffer: List[Dict] = []
        self.event_log: List[Dict] = []

        # Initialize from station data if provided
        if station_data:
            self._initialize_from_data(station_data)
        else:
            self._initialize_default()

        self.state = TwinState.SYNCHRONIZED
        logger.info(f"Digital twin {self.twin_id} created for station {station_id}")

    def _initialize_default(self):
        """Initialize with default component models."""
        # Radio components
        for i in range(3):  # 3 sectors
            self.components[f"radio_sector_{i}"] = ComponentModel(
                component_id=f"radio_sector_{i}",
                component_type="radio",
                parameters={"tx_power": 43.0, "frequency": 3500.0},
                mtbf_hours=100000
            )

        # Antenna components
        for i in range(3):
            self.components[f"antenna_sector_{i}"] = ComponentModel(
                component_id=f"antenna_sector_{i}",
                component_type="antenna",
                parameters={"tilt": 6.0, "azimuth": i * 120},
                mtbf_hours=200000
            )

        # Power components
        self.components["power_supply"] = ComponentModel(
            component_id="power_supply",
            component_type="power",
            parameters={"voltage": 48.0, "efficiency": 0.95},
            mtbf_hours=80000
        )

        self.components["battery_bank"] = ComponentModel(
            component_id="battery_bank",
            component_type="battery",
            parameters={"capacity_ah": 200, "soc": 100.0},
            degradation_rate=0.01,  # 1% per day degradation
            mtbf_hours=50000
        )

        # Cooling
        self.components["cooling_fan_1"] = ComponentModel(
            component_id="cooling_fan_1",
            component_type="cooling",
            parameters={"rpm": 3000, "airflow_cfm": 100},
            degradation_rate=0.005,
            mtbf_hours=30000
        )

        self.components["cooling_fan_2"] = ComponentModel(
            component_id="cooling_fan_2",
            component_type="cooling",
            parameters={"rpm": 3000, "airflow_cfm": 100},
            degradation_rate=0.005,
            mtbf_hours=30000
        )

    def _initialize_from_data(self, station_data: Dict):
        """Initialize from real station data."""
        self._initialize_default()

        # Override with actual data if available
        if "metrics" in station_data:
            metrics = station_data["metrics"]
            if "battery_soc" in metrics:
                self.components["battery_bank"].parameters["soc"] = metrics["battery_soc"]
            if "fan_speed" in metrics:
                self.components["cooling_fan_1"].parameters["rpm"] = metrics["fan_speed"]
            if "temperature" in metrics:
                self.environment.temperature = metrics["temperature"]

    def synchronize(self, real_time_data: Dict[str, Any]):
        """Synchronize twin with real-time data from physical asset."""
        self.last_sync = datetime.now()

        # Update component states from real data
        if "metrics" in real_time_data:
            for metric_name, value in real_time_data["metrics"].items():
                self._update_from_metric(metric_name, value)

        # Update environment from sensor data
        if "environment" in real_time_data:
            env = real_time_data["environment"]
            self.environment.temperature = env.get("temperature", self.environment.temperature)
            self.environment.humidity = env.get("humidity", self.environment.humidity)

        # Update traffic from network data
        if "traffic" in real_time_data:
            traffic = real_time_data["traffic"]
            self.traffic.current_load = traffic.get("load", self.traffic.current_load)
            self.traffic.throughput_mbps = traffic.get("throughput", self.traffic.throughput_mbps)

        self.state = TwinState.SYNCHRONIZED
        logger.debug(f"Twin {self.twin_id} synchronized at {self.last_sync}")

    def _update_from_metric(self, metric_name: str, value: float):
        """Update component model from a metric value."""
        metric_mappings = {
            "battery_soc": ("battery_bank", "soc"),
            "battery_voltage": ("battery_bank", "voltage"),
            "fan_speed": ("cooling_fan_1", "rpm"),
            "tx_power": ("radio_sector_0", "tx_power"),
            "temperature": None,  # Handled by environment
        }

        if metric_name in metric_mappings:
            mapping = metric_mappings[metric_name]
            if mapping:
                component_id, param_name = mapping
                if component_id in self.components:
                    self.components[component_id].parameters[param_name] = value

    def simulate(self,
                 duration_hours: float = 24.0,
                 time_step_hours: float = 1.0,
                 mode: SimulationMode = SimulationMode.ACCELERATED,
                 scenario: Optional[Dict] = None) -> SimulationResult:
        """Run a simulation on the digital twin."""
        simulation_id = str(uuid.uuid4())
        start_time = datetime.now()
        self.state = TwinState.SIMULATING

        events = []
        metrics_history = []
        failures = []

        # Apply scenario modifications if provided
        if scenario:
            self._apply_scenario(scenario)

        # Run simulation steps
        steps = int(duration_hours / time_step_hours)
        sim_time = self.simulation_clock

        for step in range(steps):
            sim_time += timedelta(hours=time_step_hours)

            # Simulate environment
            env_state = self.environment.simulate_step(time_step_hours)

            # Simulate traffic
            traffic_state = self.traffic.simulate_step(time_step_hours)

            # Simulate each component
            component_states = {}
            for comp_id, component in self.components.items():
                # Adjust degradation based on environment
                if self.environment.temperature > 40:
                    component.degradation_rate *= 1.5  # Accelerated wear in heat

                comp_state = component.simulate_step(time_step_hours)
                component_states[comp_id] = comp_state

                # Log failures
                if comp_state["failed"]:
                    failure_event = {
                        "time": sim_time.isoformat(),
                        "component_id": comp_id,
                        "component_type": component.component_type,
                        "health_at_failure": comp_state["health_score"]
                    }
                    failures.append(failure_event)
                    events.append({
                        "type": "failure",
                        "time": sim_time.isoformat(),
                        **failure_event
                    })

            # Record metrics
            metrics_snapshot = {
                "time": sim_time.isoformat(),
                "step": step,
                "environment": env_state,
                "traffic": traffic_state,
                "components": component_states
            }
            metrics_history.append(metrics_snapshot)

        end_time = datetime.now()
        self.state = TwinState.SYNCHRONIZED

        # Generate recommendations based on simulation
        recommendations = self._generate_recommendations(failures, metrics_history)

        return SimulationResult(
            simulation_id=simulation_id,
            twin_id=self.twin_id,
            station_id=self.station_id,
            mode=mode,
            start_time=start_time,
            end_time=end_time,
            duration_hours=duration_hours,
            time_steps=steps,
            events=events,
            metrics_history=metrics_history,
            failures_detected=failures,
            recommendations=recommendations
        )

    def _apply_scenario(self, scenario: Dict):
        """Apply a what-if scenario to the twin."""
        # Modify environment
        if "environment" in scenario:
            env = scenario["environment"]
            if "temperature" in env:
                self.environment.temperature = env["temperature"]
            if "humidity" in env:
                self.environment.humidity = env["humidity"]

        # Modify components
        if "components" in scenario:
            for comp_id, changes in scenario["components"].items():
                if comp_id in self.components:
                    for param, value in changes.items():
                        if param == "health_score":
                            self.components[comp_id].health_score = value
                        elif param == "degradation_rate":
                            self.components[comp_id].degradation_rate = value
                        else:
                            self.components[comp_id].parameters[param] = value

        # Modify traffic
        if "traffic" in scenario:
            traffic = scenario["traffic"]
            if "base_load" in traffic:
                self.traffic.base_load = traffic["base_load"]
            if "peak_multiplier" in traffic:
                self.traffic.peak_multiplier = traffic["peak_multiplier"]

    def _generate_recommendations(self,
                                   failures: List[Dict],
                                   metrics_history: List[Dict]) -> List[str]:
        """Generate recommendations based on simulation results."""
        recommendations = []

        # Analyze failures
        if failures:
            failure_types = {}
            for f in failures:
                comp_type = f["component_type"]
                failure_types[comp_type] = failure_types.get(comp_type, 0) + 1

            for comp_type, count in failure_types.items():
                recommendations.append(
                    f"High failure risk for {comp_type} components ({count} predicted failures). "
                    f"Consider preventive maintenance."
                )

        # Analyze component health trends
        for comp_id, component in self.components.items():
            if component.health_score < 0.7:
                recommendations.append(
                    f"Component {comp_id} health degraded to {component.health_score:.1%}. "
                    f"Schedule inspection."
                )
            if component.health_score < 0.5:
                recommendations.append(
                    f"URGENT: Component {comp_id} near failure ({component.health_score:.1%} health). "
                    f"Immediate replacement recommended."
                )

        # Analyze environmental impact
        if metrics_history:
            avg_temp = np.mean([m["environment"]["temperature"] for m in metrics_history])
            if avg_temp > 35:
                recommendations.append(
                    f"High average temperature ({avg_temp:.1f}°C) accelerates equipment degradation. "
                    f"Consider cooling system upgrade."
                )

        return recommendations

    def predict_failures(self, horizon_days: int = 30) -> List[Dict]:
        """Predict component failures within the given time horizon."""
        predictions = []

        for comp_id, component in self.components.items():
            # Calculate expected time to failure based on current health and degradation
            if component.degradation_rate > 0:
                days_to_critical = (component.health_score - 0.3) / component.degradation_rate

                if days_to_critical <= horizon_days:
                    predictions.append({
                        "component_id": comp_id,
                        "component_type": component.component_type,
                        "current_health": component.health_score,
                        "days_to_critical": max(0, days_to_critical),
                        "failure_probability_30d": min(1.0, 30 / max(days_to_critical, 1)),
                        "recommended_action": "preventive_replacement" if days_to_critical < 7 else "schedule_inspection"
                    })

        # Sort by urgency
        predictions.sort(key=lambda x: x["days_to_critical"])
        return predictions

    def get_state(self) -> Dict[str, Any]:
        """Get current state of the digital twin."""
        return {
            "twin_id": self.twin_id,
            "station_id": self.station_id,
            "state": self.state.value,
            "created_at": self.created_at.isoformat(),
            "last_sync": self.last_sync.isoformat() if self.last_sync else None,
            "simulation_clock": self.simulation_clock.isoformat(),
            "components": {
                comp_id: {
                    "type": comp.component_type,
                    "health_score": comp.health_score,
                    "parameters": comp.parameters
                }
                for comp_id, comp in self.components.items()
            },
            "environment": {
                "temperature": self.environment.temperature,
                "humidity": self.environment.humidity,
                "wind_speed": self.environment.wind_speed
            },
            "traffic": {
                "current_load": self.traffic.current_load,
                "throughput_mbps": self.traffic.throughput_mbps,
                "connected_users": self.traffic.connected_users
            }
        }


class DigitalTwinService:
    """Service for managing digital twins of base stations."""

    def __init__(self, metrics_client=None):
        self.twins: Dict[str, DigitalTwin] = {}
        self.metrics_client = metrics_client
        self.simulation_history: List[SimulationResult] = []
        self._sync_thread = None
        self._running = False

        logger.info("Digital Twin Service initialized")

    def create_twin(self, station_id: str, station_data: Optional[Dict] = None) -> DigitalTwin:
        """Create a digital twin for a station."""
        if station_id in self.twins:
            logger.warning(f"Twin already exists for station {station_id}")
            return self.twins[station_id]

        twin = DigitalTwin(station_id, station_data)
        self.twins[station_id] = twin

        logger.info(f"Created digital twin for station {station_id}")
        return twin

    def get_twin(self, station_id: str) -> Optional[DigitalTwin]:
        """Get the digital twin for a station."""
        return self.twins.get(station_id)

    def delete_twin(self, station_id: str) -> bool:
        """Delete a digital twin."""
        if station_id in self.twins:
            del self.twins[station_id]
            logger.info(f"Deleted digital twin for station {station_id}")
            return True
        return False

    def run_what_if(self,
                    station_id: str,
                    scenario: Dict,
                    duration_hours: float = 168.0) -> Optional[SimulationResult]:
        """Run a what-if scenario simulation."""
        twin = self.twins.get(station_id)
        if not twin:
            logger.error(f"No twin found for station {station_id}")
            return None

        result = twin.simulate(
            duration_hours=duration_hours,
            mode=SimulationMode.WHAT_IF,
            scenario=scenario
        )

        self.simulation_history.append(result)
        return result

    def predict_fleet_failures(self, horizon_days: int = 30) -> Dict[str, List[Dict]]:
        """Predict failures across all digital twins."""
        fleet_predictions = {}

        for station_id, twin in self.twins.items():
            predictions = twin.predict_failures(horizon_days)
            if predictions:
                fleet_predictions[station_id] = predictions

        return fleet_predictions

    def get_fleet_health(self) -> Dict[str, Any]:
        """Get health summary across all digital twins."""
        if not self.twins:
            return {"total_twins": 0}

        total_health = 0
        component_health = {}
        critical_stations = []

        for station_id, twin in self.twins.items():
            station_health = 0
            station_critical = False

            for comp_id, component in twin.components.items():
                total_health += component.health_score

                comp_type = component.component_type
                if comp_type not in component_health:
                    component_health[comp_type] = []
                component_health[comp_type].append(component.health_score)

                if component.health_score < 0.5:
                    station_critical = True

                station_health += component.health_score

            if station_critical:
                critical_stations.append({
                    "station_id": station_id,
                    "avg_health": station_health / len(twin.components)
                })

        total_components = sum(len(t.components) for t in self.twins.values())

        return {
            "total_twins": len(self.twins),
            "average_health": total_health / total_components if total_components > 0 else 1.0,
            "component_health_by_type": {
                comp_type: np.mean(scores) for comp_type, scores in component_health.items()
            },
            "critical_stations": critical_stations,
            "total_simulations": len(self.simulation_history)
        }

    def start_real_time_sync(self, interval_seconds: float = 60.0):
        """Start real-time synchronization with physical assets."""
        if self._running:
            return

        self._running = True
        self._sync_thread = threading.Thread(target=self._sync_loop, args=(interval_seconds,))
        self._sync_thread.daemon = True
        self._sync_thread.start()

        logger.info(f"Started real-time sync with interval {interval_seconds}s")

    def stop_real_time_sync(self):
        """Stop real-time synchronization."""
        self._running = False
        if self._sync_thread:
            self._sync_thread.join(timeout=5.0)
        logger.info("Stopped real-time sync")

    def _sync_loop(self, interval: float):
        """Background loop for synchronizing twins."""
        while self._running:
            for station_id, twin in self.twins.items():
                try:
                    if self.metrics_client:
                        real_time_data = self.metrics_client.get_latest(station_id)
                        twin.synchronize(real_time_data)
                except Exception as e:
                    logger.error(f"Failed to sync twin for {station_id}: {e}")

            time.sleep(interval)


# Singleton instance with thread-safe initialization
_digital_twin_service: Optional[DigitalTwinService] = None
_digital_twin_service_lock = threading.Lock()


def get_digital_twin_service() -> DigitalTwinService:
    """Get the singleton digital twin service instance (thread-safe)."""
    global _digital_twin_service
    if _digital_twin_service is None:
        with _digital_twin_service_lock:
            if _digital_twin_service is None:  # Double-check locking
                _digital_twin_service = DigitalTwinService()
    return _digital_twin_service
