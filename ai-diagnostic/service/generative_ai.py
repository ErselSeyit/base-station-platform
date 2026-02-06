"""
Generative AI Service for Failure Scenario Synthesis.

Uses generative models to:
- Synthesize realistic failure scenarios for training
- Generate augmented training data for ML models
- Create novel fault patterns for testing
- Produce natural language explanations and reports
"""

import json
import logging
import random
import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)

# Modern RNG (replaces deprecated np.random functions)
_rng = np.random.default_rng(42)


class ScenarioType(Enum):
    """Types of failure scenarios that can be generated."""
    HARDWARE_FAILURE = "hardware_failure"
    ENVIRONMENTAL = "environmental"
    POWER_OUTAGE = "power_outage"
    NETWORK_CONGESTION = "network_congestion"
    RF_INTERFERENCE = "rf_interference"
    SECURITY_INCIDENT = "security_incident"
    CASCADE_FAILURE = "cascade_failure"
    GRADUAL_DEGRADATION = "gradual_degradation"


class GenerationMethod(Enum):
    """Methods for generating synthetic data."""
    STATISTICAL = "statistical"      # Based on statistical distributions
    TEMPLATE = "template"            # Template-based generation
    MARKOV = "markov"               # Markov chain for sequences
    GAN_BASED = "gan_based"         # GAN-style generation (simulated)
    VAE_BASED = "vae_based"         # VAE-style generation (simulated)


@dataclass
class SyntheticScenario:
    """A generated failure scenario."""
    scenario_id: str
    scenario_type: ScenarioType
    generation_method: GenerationMethod
    timestamp: datetime
    duration_minutes: int
    severity: float  # 0-1
    affected_components: List[str]
    metrics_sequence: List[Dict[str, float]]
    alarm_sequence: List[Dict[str, Any]]
    root_cause: str
    description: str
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "scenario_id": self.scenario_id,
            "scenario_type": self.scenario_type.value,
            "generation_method": self.generation_method.value,
            "timestamp": self.timestamp.isoformat(),
            "duration_minutes": self.duration_minutes,
            "severity": self.severity,
            "affected_components": self.affected_components,
            "metrics_sequence": self.metrics_sequence,
            "alarm_sequence": self.alarm_sequence,
            "root_cause": self.root_cause,
            "description": self.description,
            "metadata": self.metadata
        }


@dataclass
class TrainingDataBatch:
    """Batch of synthetic training data."""
    batch_id: str
    created_at: datetime
    scenarios: List[SyntheticScenario]
    total_samples: int
    scenario_distribution: Dict[str, int]
    quality_score: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "batch_id": self.batch_id,
            "created_at": self.created_at.isoformat(),
            "total_samples": self.total_samples,
            "scenario_distribution": self.scenario_distribution,
            "quality_score": self.quality_score
        }


class FailurePatternLibrary:
    """Library of known failure patterns for template-based generation."""

    PATTERNS = {
        ScenarioType.HARDWARE_FAILURE: {
            "fan_failure": {
                "precursors": ["fan_speed_decrease", "temperature_increase"],
                "symptoms": ["high_temperature", "thermal_throttling", "performance_degradation"],
                "affected_metrics": ["fan_speed", "temperature", "cpu_usage"],
                "typical_duration": (30, 180),  # minutes
                "severity_range": (0.5, 0.9)
            },
            "power_supply_failure": {
                "precursors": ["voltage_fluctuation", "efficiency_drop"],
                "symptoms": ["unstable_power", "equipment_reset", "service_outage"],
                "affected_metrics": ["voltage", "current", "power_efficiency"],
                "typical_duration": (5, 60),
                "severity_range": (0.7, 1.0)
            },
            "antenna_degradation": {
                "precursors": ["vswr_increase", "return_loss_decrease"],
                "symptoms": ["poor_coverage", "dropped_calls", "low_throughput"],
                "affected_metrics": ["vswr", "rsrp", "sinr", "throughput"],
                "typical_duration": (1440, 10080),  # 1 day to 1 week
                "severity_range": (0.3, 0.7)
            },
            "radio_module_failure": {
                "precursors": ["tx_power_drift", "temperature_spike"],
                "symptoms": ["no_service", "alarm_storm", "sector_down"],
                "affected_metrics": ["tx_power", "temperature", "traffic"],
                "typical_duration": (1, 30),
                "severity_range": (0.8, 1.0)
            }
        },
        ScenarioType.ENVIRONMENTAL: {
            "heat_wave": {
                "precursors": ["temperature_rise", "humidity_change"],
                "symptoms": ["thermal_throttling", "fan_overwork", "battery_stress"],
                "affected_metrics": ["temperature", "humidity", "fan_speed", "battery_temp"],
                "typical_duration": (360, 2880),
                "severity_range": (0.4, 0.8)
            },
            "storm": {
                "precursors": ["wind_increase", "precipitation_start"],
                "symptoms": ["antenna_misalignment", "microwave_fade", "power_outage_risk"],
                "affected_metrics": ["wind_speed", "precipitation", "rsrp", "mw_rsl"],
                "typical_duration": (60, 360),
                "severity_range": (0.5, 0.9)
            },
            "flooding": {
                "precursors": ["water_level_rise", "humidity_spike"],
                "symptoms": ["equipment_damage", "power_shutdown", "site_unreachable"],
                "affected_metrics": ["water_level", "humidity", "door_status"],
                "typical_duration": (120, 1440),
                "severity_range": (0.7, 1.0)
            }
        },
        ScenarioType.POWER_OUTAGE: {
            "grid_failure": {
                "precursors": ["voltage_drop", "frequency_deviation"],
                "symptoms": ["battery_discharge", "generator_start", "load_shedding"],
                "affected_metrics": ["utility_voltage", "battery_soc", "generator_status"],
                "typical_duration": (30, 480),
                "severity_range": (0.6, 0.95)
            },
            "battery_exhaustion": {
                "precursors": ["soc_decline", "backup_runtime_decrease"],
                "symptoms": ["service_shutdown", "data_loss_risk", "cold_start_needed"],
                "affected_metrics": ["battery_soc", "battery_voltage", "load_power"],
                "typical_duration": (60, 240),
                "severity_range": (0.8, 1.0)
            }
        },
        ScenarioType.RF_INTERFERENCE: {
            "external_interference": {
                "precursors": ["noise_floor_rise", "sinr_degradation"],
                "symptoms": ["throughput_drop", "increased_retransmissions", "coverage_hole"],
                "affected_metrics": ["sinr", "throughput", "error_rate", "prb_utilization"],
                "typical_duration": (30, 720),
                "severity_range": (0.3, 0.7)
            },
            "pim_issue": {
                "precursors": ["pim_level_increase", "intermod_detection"],
                "symptoms": ["uplink_degradation", "sensitivity_loss", "dropped_calls"],
                "affected_metrics": ["pim_level", "rssi", "call_drop_rate"],
                "typical_duration": (1440, 10080),
                "severity_range": (0.4, 0.8)
            }
        },
        ScenarioType.CASCADE_FAILURE: {
            "multi_site_outage": {
                "precursors": ["transport_fault", "backhaul_congestion"],
                "symptoms": ["multiple_sites_down", "traffic_overflow", "neighbor_overload"],
                "affected_metrics": ["site_availability", "traffic_volume", "handover_success"],
                "typical_duration": (15, 180),
                "severity_range": (0.85, 1.0)
            }
        }
    }

    ALARM_TEMPLATES = {
        "high_temperature": {"severity": "major", "category": "environmental"},
        "fan_failure": {"severity": "critical", "category": "hardware"},
        "low_battery": {"severity": "major", "category": "power"},
        "power_outage": {"severity": "critical", "category": "power"},
        "vswr_high": {"severity": "minor", "category": "rf"},
        "low_coverage": {"severity": "major", "category": "performance"},
        "site_down": {"severity": "critical", "category": "availability"},
        "interference_detected": {"severity": "warning", "category": "rf"},
    }


class MetricsGenerator:
    """Generates realistic metric sequences for scenarios."""

    def __init__(self):
        self.base_values = {
            "temperature": 35.0,
            "fan_speed": 3000,
            "battery_soc": 100.0,
            "voltage": 48.0,
            "rsrp": -85.0,
            "sinr": 15.0,
            "throughput": 500.0,
            "vswr": 1.2,
            "humidity": 50.0,
            "wind_speed": 10.0,
            "tx_power": 43.0,
        }

    def generate_sequence(self,
                          metric_name: str,
                          duration_minutes: int,
                          anomaly_type: str,
                          severity: float) -> List[float]:
        """Generate a metric sequence with anomaly pattern."""
        steps = max(1, duration_minutes // 5)  # 5-minute intervals
        base_value = self.base_values.get(metric_name, 50.0)

        sequence = []
        for i in range(steps):
            progress = i / max(1, steps - 1)

            # Different anomaly patterns
            if anomaly_type == "spike":
                # Sudden spike
                if 0.4 < progress < 0.6:
                    anomaly_factor = severity * 2.0
                else:
                    anomaly_factor = 0
            elif anomaly_type == "gradual_increase":
                # Gradual degradation
                anomaly_factor = progress * severity
            elif anomaly_type == "gradual_decrease":
                # Gradual decrease
                anomaly_factor = -progress * severity
            elif anomaly_type == "oscillation":
                # Unstable oscillation
                anomaly_factor = severity * np.sin(progress * 10) * (1 + progress)
            elif anomaly_type == "step_change":
                # Step change at midpoint
                anomaly_factor = severity if progress > 0.5 else 0
            else:
                anomaly_factor = severity * progress

            # Apply anomaly to base value
            if metric_name in ["temperature", "vswr", "humidity"]:
                value = base_value * (1 + anomaly_factor * 0.5)
            elif metric_name in ["fan_speed", "battery_soc", "throughput", "sinr"]:
                value = base_value * (1 - anomaly_factor * 0.5)
            else:
                value = base_value + anomaly_factor * base_value * 0.2

            # Add noise
            noise = _rng.normal(0, base_value * 0.02)
            value += noise

            sequence.append(round(value, 2))

        return sequence


class AlarmSequenceGenerator:
    """Generates realistic alarm sequences for scenarios."""

    def __init__(self):
        self.library = FailurePatternLibrary()

    def generate_sequence(self,
                          scenario_type: ScenarioType,
                          pattern_name: str,
                          start_time: datetime,
                          duration_minutes: int,
                          severity: float) -> List[Dict[str, Any]]:
        """Generate an alarm sequence for a scenario."""
        alarms = []

        pattern = self.library.PATTERNS.get(scenario_type, {}).get(pattern_name)
        if not pattern:
            return alarms

        # Generate precursor alarms
        precursor_time = start_time - timedelta(minutes=random.randint(5, 30))
        for precursor in pattern.get("precursors", []):
            if random.random() < 0.8:  # 80% chance of precursor
                alarms.append({
                    "timestamp": precursor_time.isoformat(),
                    "alarm_type": precursor,
                    "severity": "warning",
                    "category": "precursor",
                    "message": f"Precursor detected: {precursor.replace('_', ' ')}"
                })
                precursor_time += timedelta(minutes=random.randint(1, 10))

        # Generate main symptom alarms
        symptom_time = start_time
        for symptom in pattern.get("symptoms", []):
            alarm_template = self.library.ALARM_TEMPLATES.get(
                symptom,
                {"severity": "major", "category": "unknown"}
            )

            alarms.append({
                "timestamp": symptom_time.isoformat(),
                "alarm_type": symptom,
                "severity": alarm_template["severity"] if severity > 0.5 else "minor",
                "category": alarm_template["category"],
                "message": f"Alert: {symptom.replace('_', ' ').title()}"
            })
            symptom_time += timedelta(minutes=random.randint(1, 15))

        # Sort by timestamp
        alarms.sort(key=lambda x: x["timestamp"])
        return alarms


class ScenarioDescriptionGenerator:
    """Generates natural language descriptions for scenarios."""

    TEMPLATES = {
        ScenarioType.HARDWARE_FAILURE: [
            "A {component} failure occurred at the site, beginning with {precursor}. "
            "This led to {symptom}, affecting service quality for approximately {duration} minutes.",
            "The {component} experienced degradation, manifesting as {symptom}. "
            "Early indicators included {precursor}. Recovery took {duration} minutes.",
        ],
        ScenarioType.ENVIRONMENTAL: [
            "Environmental conditions ({condition}) impacted site operations. "
            "{symptom} was observed, with the event lasting approximately {duration} minutes.",
            "Adverse weather conditions caused {symptom}. "
            "The {condition} event persisted for {duration} minutes.",
        ],
        ScenarioType.POWER_OUTAGE: [
            "A power event ({cause}) triggered battery backup operation. "
            "{symptom} occurred during the {duration}-minute outage period.",
            "Power supply interruption led to {symptom}. "
            "The site operated on backup power for {duration} minutes.",
        ],
        ScenarioType.CASCADE_FAILURE: [
            "A cascading failure originating from {cause} affected multiple systems. "
            "The incident resulted in {symptom} across {affected_count} components over {duration} minutes.",
        ]
    }

    def generate(self,
                 scenario_type: ScenarioType,
                 pattern_name: str,
                 duration_minutes: int,
                 affected_components: List[str],
                 symptoms: List[str]) -> str:
        """Generate a natural language description."""
        templates = self.TEMPLATES.get(scenario_type, [
            "A {severity} incident occurred affecting {component}. "
            "Duration: {duration} minutes."
        ])

        template = random.choice(templates)

        description = template.format(
            component=pattern_name.replace("_", " "),
            precursor=symptoms[0] if symptoms else "anomaly",
            symptom=symptoms[-1] if symptoms else "degradation",
            condition=pattern_name.replace("_", " "),
            cause=pattern_name.replace("_", " "),
            duration=duration_minutes,
            affected_count=len(affected_components),
            severity="significant" if len(affected_components) > 2 else "moderate"
        )

        return description


class GenerativeAIService:
    """Main service for generative AI capabilities."""

    def __init__(self):
        self.pattern_library = FailurePatternLibrary()
        self.metrics_generator = MetricsGenerator()
        self.alarm_generator = AlarmSequenceGenerator()
        self.description_generator = ScenarioDescriptionGenerator()

        self.generated_scenarios: List[SyntheticScenario] = []
        self.training_batches: List[TrainingDataBatch] = []

        logger.info("Generative AI Service initialized")

    def generate_scenario(self,
                          scenario_type: ScenarioType,
                          pattern_name: Optional[str] = None,
                          method: GenerationMethod = GenerationMethod.TEMPLATE,
                          custom_params: Optional[Dict] = None) -> SyntheticScenario:
        """Generate a single synthetic failure scenario."""
        scenario_id = str(uuid.uuid4())
        timestamp = datetime.now()

        # Select pattern
        patterns = self.pattern_library.PATTERNS.get(scenario_type, {})
        if not pattern_name:
            pattern_name = random.choice(list(patterns.keys())) if patterns else "generic"

        pattern = patterns.get(pattern_name, {})

        # Determine parameters
        duration_range = pattern.get("typical_duration", (30, 180))
        duration_minutes = random.randint(*duration_range)

        severity_range = pattern.get("severity_range", (0.3, 0.8))
        severity = random.uniform(*severity_range)

        # Apply custom parameters
        if custom_params:
            duration_minutes = custom_params.get("duration_minutes", duration_minutes)
            severity = custom_params.get("severity", severity)

        # Generate affected components
        affected_metrics = pattern.get("affected_metrics", ["temperature", "status"])
        affected_components = [f"component_{m}" for m in affected_metrics]

        # Generate metrics sequence
        anomaly_types = ["gradual_increase", "spike", "gradual_decrease", "oscillation"]
        metrics_sequence = []

        for i in range(max(1, duration_minutes // 5)):
            step_metrics = {}
            for metric in affected_metrics:
                anomaly_type = random.choice(anomaly_types)
                sequence = self.metrics_generator.generate_sequence(
                    metric, duration_minutes, anomaly_type, severity
                )
                if i < len(sequence):
                    step_metrics[metric] = sequence[i]
            step_metrics["timestamp_offset_minutes"] = i * 5
            metrics_sequence.append(step_metrics)

        # Generate alarm sequence
        symptoms = pattern.get("symptoms", [])
        alarm_sequence = self.alarm_generator.generate_sequence(
            scenario_type, pattern_name, timestamp, duration_minutes, severity
        )

        # Generate description
        description = self.description_generator.generate(
            scenario_type, pattern_name, duration_minutes, affected_components, symptoms
        )

        # Create scenario
        scenario = SyntheticScenario(
            scenario_id=scenario_id,
            scenario_type=scenario_type,
            generation_method=method,
            timestamp=timestamp,
            duration_minutes=duration_minutes,
            severity=severity,
            affected_components=affected_components,
            metrics_sequence=metrics_sequence,
            alarm_sequence=alarm_sequence,
            root_cause=pattern_name.replace("_", " ").title(),
            description=description,
            metadata={
                "pattern_name": pattern_name,
                "precursors": pattern.get("precursors", []),
                "symptoms": symptoms
            }
        )

        self.generated_scenarios.append(scenario)
        logger.info(f"Generated scenario {scenario_id} of type {scenario_type.value}")

        return scenario

    def generate_training_batch(self,
                                 batch_size: int = 100,
                                 scenario_distribution: Optional[Dict[ScenarioType, float]] = None
                                 ) -> TrainingDataBatch:
        """Generate a batch of synthetic training data."""
        batch_id = str(uuid.uuid4())

        # Default distribution
        if scenario_distribution is None:
            scenario_distribution = {
                ScenarioType.HARDWARE_FAILURE: 0.35,
                ScenarioType.ENVIRONMENTAL: 0.20,
                ScenarioType.POWER_OUTAGE: 0.20,
                ScenarioType.RF_INTERFERENCE: 0.15,
                ScenarioType.CASCADE_FAILURE: 0.10,
            }

        scenarios = []
        type_counts = {}

        for scenario_type, proportion in scenario_distribution.items():
            count = int(batch_size * proportion)
            type_counts[scenario_type.value] = count

            patterns = list(self.pattern_library.PATTERNS.get(scenario_type, {}).keys())

            for _ in range(count):
                pattern = random.choice(patterns) if patterns else None
                scenario = self.generate_scenario(scenario_type, pattern)
                scenarios.append(scenario)

        # Calculate quality score (based on diversity and realism)
        unique_patterns = len(set(s.metadata.get("pattern_name") for s in scenarios))
        severity_variance = np.var([s.severity for s in scenarios])
        quality_score = min(1.0, (unique_patterns / 10) * 0.5 + (severity_variance * 2) * 0.5)

        batch = TrainingDataBatch(
            batch_id=batch_id,
            created_at=datetime.now(),
            scenarios=scenarios,
            total_samples=len(scenarios),
            scenario_distribution=type_counts,
            quality_score=quality_score
        )

        self.training_batches.append(batch)
        logger.info(f"Generated training batch {batch_id} with {len(scenarios)} scenarios")

        return batch

    def augment_dataset(self,
                        existing_scenarios: List[Dict],
                        augmentation_factor: int = 2) -> List[SyntheticScenario]:
        """Augment existing dataset with synthetic variations."""
        augmented = []

        for existing in existing_scenarios:
            # Parse existing scenario
            scenario_type = ScenarioType(existing.get("type", "hardware_failure"))
            pattern = existing.get("pattern", "generic")
            original_severity = existing.get("severity", 0.5)
            original_duration = existing.get("duration_minutes", 60)

            for i in range(augmentation_factor):
                # Vary parameters
                severity_variation = _rng.normal(0, 0.1)
                duration_variation = _rng.normal(0, 0.2)

                custom_params = {
                    "severity": max(0.1, min(1.0, original_severity + severity_variation)),
                    "duration_minutes": int(max(5, original_duration * (1 + duration_variation)))
                }

                augmented_scenario = self.generate_scenario(
                    scenario_type,
                    pattern,
                    method=GenerationMethod.STATISTICAL,
                    custom_params=custom_params
                )
                augmented.append(augmented_scenario)

        logger.info(f"Augmented dataset with {len(augmented)} synthetic scenarios")
        return augmented

    def generate_edge_cases(self, count: int = 20) -> List[SyntheticScenario]:
        """Generate edge case scenarios for testing model robustness."""
        edge_cases = []

        edge_case_configs = [
            # Extreme severity
            (ScenarioType.HARDWARE_FAILURE, {"severity": 0.99, "duration_minutes": 5}),
            (ScenarioType.POWER_OUTAGE, {"severity": 1.0, "duration_minutes": 480}),

            # Minimal impact
            (ScenarioType.RF_INTERFERENCE, {"severity": 0.05, "duration_minutes": 10}),

            # Very long duration
            (ScenarioType.GRADUAL_DEGRADATION, {"severity": 0.3, "duration_minutes": 10080}),

            # Rapid succession
            (ScenarioType.CASCADE_FAILURE, {"severity": 0.85, "duration_minutes": 2}),
        ]

        for _ in range(count):
            config = random.choice(edge_case_configs)
            scenario_type, params = config

            patterns = list(self.pattern_library.PATTERNS.get(scenario_type, {}).keys())
            pattern = random.choice(patterns) if patterns else None

            scenario = self.generate_scenario(
                scenario_type,
                pattern,
                method=GenerationMethod.TEMPLATE,
                custom_params=params
            )
            scenario.metadata["is_edge_case"] = True
            edge_cases.append(scenario)

        logger.info(f"Generated {len(edge_cases)} edge case scenarios")
        return edge_cases

    def get_statistics(self) -> Dict[str, Any]:
        """Get statistics about generated scenarios."""
        if not self.generated_scenarios:
            return {"total_generated": 0}

        severities = [s.severity for s in self.generated_scenarios]
        durations = [s.duration_minutes for s in self.generated_scenarios]
        type_counts = {}

        for s in self.generated_scenarios:
            t = s.scenario_type.value
            type_counts[t] = type_counts.get(t, 0) + 1

        return {
            "total_generated": len(self.generated_scenarios),
            "total_batches": len(self.training_batches),
            "average_severity": np.mean(severities),
            "severity_std": np.std(severities),
            "average_duration_minutes": np.mean(durations),
            "scenario_type_distribution": type_counts,
            "unique_patterns": len(set(
                s.metadata.get("pattern_name") for s in self.generated_scenarios
            ))
        }


# Singleton instance with thread-safe initialization
_generative_ai_service: Optional[GenerativeAIService] = None
_generative_ai_service_lock = threading.Lock()


def get_generative_ai_service() -> GenerativeAIService:
    """Get the singleton generative AI service instance (thread-safe)."""
    global _generative_ai_service
    if _generative_ai_service is None:
        with _generative_ai_service_lock:
            if _generative_ai_service is None:  # Double-check locking
                _generative_ai_service = GenerativeAIService()
    return _generative_ai_service
