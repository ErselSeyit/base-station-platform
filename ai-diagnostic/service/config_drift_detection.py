"""
Configuration Drift Detection Service

Quick Win #4: Configuration Drift Detection
- Monitors configuration parameters against baseline
- Detects unauthorized or unexpected changes
- Supports auto-remediation recommendations

Features:
- Baseline configuration storage
- Change detection with severity classification
- Compliance checking against golden config
- Rollback recommendations
"""

import logging
from typing import List, Dict, Any, Optional, Set
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
import json
import hashlib
import threading

logger = logging.getLogger(__name__)


class DriftSeverity(Enum):
    """Severity of configuration drift."""
    CRITICAL = "critical"   # Security-related or service-impacting
    MAJOR = "major"         # Performance-impacting
    MINOR = "minor"         # Non-critical parameter
    INFO = "info"           # Informational only


class DriftType(Enum):
    """Type of configuration drift."""
    VALUE_CHANGED = "value_changed"
    PARAM_ADDED = "param_added"
    PARAM_REMOVED = "param_removed"
    TYPE_CHANGED = "type_changed"


@dataclass
class ConfigParameter:
    """A configuration parameter."""
    name: str
    value: Any
    category: str
    critical: bool = False
    description: str = ""


@dataclass
class ConfigDrift:
    """A detected configuration drift."""
    parameter: str
    drift_type: DriftType
    severity: DriftSeverity
    baseline_value: Any
    current_value: Any
    category: str
    description: str
    detected_at: datetime = field(default_factory=datetime.utcnow)
    auto_remediation_available: bool = False
    remediation_command: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "parameter": self.parameter,
            "drift_type": self.drift_type.value,
            "severity": self.severity.value,
            "baseline_value": str(self.baseline_value),
            "current_value": str(self.current_value),
            "category": self.category,
            "description": self.description,
            "detected_at": self.detected_at.isoformat(),
            "auto_remediation_available": self.auto_remediation_available,
            "remediation_command": self.remediation_command
        }


@dataclass
class DriftReport:
    """Complete drift detection report."""
    station_id: str
    baseline_timestamp: datetime
    current_timestamp: datetime
    total_parameters: int
    drifts: List[ConfigDrift]
    compliance_score: float  # 0-100
    requires_attention: bool

    def to_dict(self) -> Dict[str, Any]:
        return {
            "station_id": self.station_id,
            "baseline_timestamp": self.baseline_timestamp.isoformat(),
            "current_timestamp": self.current_timestamp.isoformat(),
            "total_parameters": self.total_parameters,
            "drift_count": len(self.drifts),
            "drifts_by_severity": {
                "critical": sum(1 for d in self.drifts if d.severity == DriftSeverity.CRITICAL),
                "major": sum(1 for d in self.drifts if d.severity == DriftSeverity.MAJOR),
                "minor": sum(1 for d in self.drifts if d.severity == DriftSeverity.MINOR),
                "info": sum(1 for d in self.drifts if d.severity == DriftSeverity.INFO)
            },
            "compliance_score": round(self.compliance_score, 1),
            "requires_attention": self.requires_attention,
            "drifts": [d.to_dict() for d in self.drifts]
        }


class ConfigDriftDetectionService:
    """
    Configuration drift detection service.

    Monitors configuration parameters against a baseline and detects
    unauthorized or unexpected changes.
    """

    # Critical parameters that affect security or service
    CRITICAL_PARAMS = {
        # Security
        "admin_password_hash", "ssh_enabled", "firewall_rules",
        "access_control_list", "encryption_enabled", "auth_method",

        # RF/Network
        "tx_power", "frequency", "bandwidth", "pci", "tac",
        "antenna_tilt", "antenna_azimuth",

        # Core
        "mme_ip", "sgw_ip", "enb_id", "cell_id",
    }

    # Parameters that affect performance
    PERFORMANCE_PARAMS = {
        "max_connections", "buffer_size", "timeout_values",
        "retry_count", "qos_settings", "scheduler_type",
        "handover_threshold", "power_control_params",
    }

    # Parameter categories for reporting
    CATEGORIES = {
        "security": ["password", "auth", "access", "firewall", "encryption", "ssh", "ssl", "tls"],
        "rf": ["tx", "rx", "power", "frequency", "bandwidth", "antenna", "rsrp", "sinr"],
        "network": ["ip", "port", "gateway", "dns", "vlan", "mme", "sgw"],
        "system": ["hostname", "ntp", "timezone", "logging", "snmp"],
        "performance": ["buffer", "timeout", "retry", "max_", "limit", "threshold"],
    }

    def __init__(self):
        self.baselines: Dict[str, Dict[str, ConfigParameter]] = {}
        self.baseline_timestamps: Dict[str, datetime] = {}
        logger.info("ConfigDriftDetectionService initialized")

    def set_baseline(
        self,
        station_id: str,
        config: Dict[str, Any],
        timestamp: Optional[datetime] = None
    ):
        """
        Set the baseline configuration for a station.

        Args:
            station_id: Base station identifier
            config: Dictionary of configuration parameters
            timestamp: When the baseline was captured
        """
        baseline = {}
        for name, value in config.items():
            baseline[name] = ConfigParameter(
                name=name,
                value=value,
                category=self._determine_category(name),
                critical=self._is_critical(name),
                description=""
            )

        self.baselines[station_id] = baseline
        self.baseline_timestamps[station_id] = timestamp or datetime.now(timezone.utc)

        logger.info(
            "Set baseline for station %s with %d parameters",
            station_id, len(baseline)
        )

    def detect_drift(
        self,
        station_id: str,
        current_config: Dict[str, Any]
    ) -> DriftReport:
        """
        Detect configuration drift between baseline and current config.

        Args:
            station_id: Base station identifier
            current_config: Current configuration dictionary

        Returns:
            DriftReport with all detected drifts
        """
        if station_id not in self.baselines:
            # No baseline - create one from current config
            self.set_baseline(station_id, current_config)
            return DriftReport(
                station_id=station_id,
                baseline_timestamp=self.baseline_timestamps[station_id],
                current_timestamp=datetime.now(timezone.utc),
                total_parameters=len(current_config),
                drifts=[],
                compliance_score=100.0,
                requires_attention=False
            )

        baseline = self.baselines[station_id]
        drifts = []

        baseline_params = set(baseline.keys())
        current_params = set(current_config.keys())

        # Check for removed parameters
        for param in baseline_params - current_params:
            drifts.append(self._create_drift(
                param,
                DriftType.PARAM_REMOVED,
                baseline[param].value,
                None,
                baseline[param]
            ))

        # Check for added parameters
        for param in current_params - baseline_params:
            drifts.append(self._create_drift(
                param,
                DriftType.PARAM_ADDED,
                None,
                current_config[param],
                ConfigParameter(
                    name=param,
                    value=current_config[param],
                    category=self._determine_category(param),
                    critical=self._is_critical(param)
                )
            ))

        # Check for changed parameters
        for param in baseline_params & current_params:
            baseline_value = baseline[param].value
            current_value = current_config[param]

            if not isinstance(current_value, type(baseline_value)):
                drifts.append(self._create_drift(
                    param,
                    DriftType.TYPE_CHANGED,
                    baseline_value,
                    current_value,
                    baseline[param]
                ))
            elif baseline_value != current_value:
                drifts.append(self._create_drift(
                    param,
                    DriftType.VALUE_CHANGED,
                    baseline_value,
                    current_value,
                    baseline[param]
                ))

        # Calculate compliance score
        total_params = len(baseline_params | current_params)
        drift_penalty = sum(self._get_drift_penalty(d) for d in drifts)
        compliance_score = max(0, 100 - drift_penalty)

        # Determine if attention is required
        requires_attention = any(
            d.severity in [DriftSeverity.CRITICAL, DriftSeverity.MAJOR]
            for d in drifts
        )

        return DriftReport(
            station_id=station_id,
            baseline_timestamp=self.baseline_timestamps[station_id],
            current_timestamp=datetime.now(timezone.utc),
            total_parameters=total_params,
            drifts=drifts,
            compliance_score=compliance_score,
            requires_attention=requires_attention
        )

    def _create_drift(
        self,
        param: str,
        drift_type: DriftType,
        baseline_value: Any,
        current_value: Any,
        config_param: ConfigParameter
    ) -> ConfigDrift:
        """Create a ConfigDrift object."""
        severity = self._determine_severity(param, drift_type, config_param.critical)
        description = self._generate_description(param, drift_type, baseline_value, current_value)
        remediation = self._get_remediation(param, drift_type, baseline_value)

        return ConfigDrift(
            parameter=param,
            drift_type=drift_type,
            severity=severity,
            baseline_value=baseline_value,
            current_value=current_value,
            category=config_param.category,
            description=description,
            auto_remediation_available=remediation is not None,
            remediation_command=remediation
        )

    def _determine_category(self, param: str) -> str:
        """Determine the category of a parameter."""
        param_lower = param.lower()
        for category, keywords in self.CATEGORIES.items():
            if any(kw in param_lower for kw in keywords):
                return category
        return "other"

    def _is_critical(self, param: str) -> bool:
        """Check if a parameter is critical."""
        param_lower = param.lower()
        return any(cp in param_lower for cp in self.CRITICAL_PARAMS)

    def _determine_severity(
        self,
        param: str,
        drift_type: DriftType,
        is_critical: bool
    ) -> DriftSeverity:
        """Determine the severity of a drift."""
        param_lower = param.lower()

        if is_critical:
            return DriftSeverity.CRITICAL

        if any(pp in param_lower for pp in self.PERFORMANCE_PARAMS):
            return DriftSeverity.MAJOR

        if drift_type == DriftType.PARAM_REMOVED:
            return DriftSeverity.MAJOR

        if drift_type == DriftType.TYPE_CHANGED:
            return DriftSeverity.MAJOR

        return DriftSeverity.MINOR

    def _generate_description(
        self,
        param: str,
        drift_type: DriftType,
        baseline_value: Any,
        current_value: Any
    ) -> str:
        """Generate a human-readable description of the drift."""
        if drift_type == DriftType.VALUE_CHANGED:
            return f"Parameter '{param}' changed from '{baseline_value}' to '{current_value}'"
        elif drift_type == DriftType.PARAM_ADDED:
            return f"New parameter '{param}' added with value '{current_value}'"
        elif drift_type == DriftType.PARAM_REMOVED:
            return f"Parameter '{param}' was removed (was '{baseline_value}')"
        elif drift_type == DriftType.TYPE_CHANGED:
            return f"Parameter '{param}' type changed from {type(baseline_value).__name__} to {type(current_value).__name__}"
        return f"Unknown drift for parameter '{param}'"

    def _get_remediation(
        self,
        param: str,
        drift_type: DriftType,
        baseline_value: Any
    ) -> Optional[str]:
        """Get remediation command if available."""
        if drift_type == DriftType.PARAM_REMOVED:
            return f"set {param} {baseline_value}"
        elif drift_type == DriftType.VALUE_CHANGED:
            return f"set {param} {baseline_value}"
        return None

    def _get_drift_penalty(self, drift: ConfigDrift) -> float:
        """Calculate compliance score penalty for a drift."""
        penalties = {
            DriftSeverity.CRITICAL: 25,
            DriftSeverity.MAJOR: 10,
            DriftSeverity.MINOR: 3,
            DriftSeverity.INFO: 1
        }
        return penalties.get(drift.severity, 0)

    def update_baseline(
        self,
        station_id: str,
        param: str,
        new_value: Any,
        reason: str = ""
    ):
        """
        Update a single parameter in the baseline (after approved change).

        Args:
            station_id: Base station identifier
            param: Parameter name to update
            new_value: New baseline value
            reason: Reason for the change (for audit)
        """
        if station_id not in self.baselines:
            logger.warning("No baseline exists for station %s", station_id)
            return

        self.baselines[station_id][param] = ConfigParameter(
            name=param,
            value=new_value,
            category=self._determine_category(param),
            critical=self._is_critical(param),
            description=f"Updated: {reason}"
        )

        logger.info(
            "Updated baseline for %s.%s to '%s' (reason: %s)",
            station_id, param, new_value, reason
        )

    def get_baseline_hash(self, station_id: str) -> Optional[str]:
        """Get a hash of the baseline configuration for integrity checking."""
        if station_id not in self.baselines:
            return None

        config_str = json.dumps(
            {k: str(v.value) for k, v in sorted(self.baselines[station_id].items())},
            sort_keys=True
        )
        return hashlib.sha256(config_str.encode()).hexdigest()[:16]

    def export_baseline(self, station_id: str) -> Optional[Dict[str, Any]]:
        """Export baseline configuration for backup/transfer."""
        if station_id not in self.baselines:
            return None

        return {
            "station_id": station_id,
            "timestamp": self.baseline_timestamps[station_id].isoformat(),
            "hash": self.get_baseline_hash(station_id),
            "parameters": {
                name: {
                    "value": param.value,
                    "category": param.category,
                    "critical": param.critical
                }
                for name, param in self.baselines[station_id].items()
            }
        }

    def import_baseline(self, baseline_data: Dict[str, Any]):
        """Import a baseline configuration from backup."""
        station_id = baseline_data["station_id"]
        timestamp = datetime.fromisoformat(baseline_data["timestamp"])

        baseline = {}
        for name, param_data in baseline_data["parameters"].items():
            baseline[name] = ConfigParameter(
                name=name,
                value=param_data["value"],
                category=param_data["category"],
                critical=param_data["critical"]
            )

        self.baselines[station_id] = baseline
        self.baseline_timestamps[station_id] = timestamp

        logger.info(
            "Imported baseline for station %s (%d parameters)",
            station_id, len(baseline)
        )


# Singleton instance with thread-safe initialization
_config_service: Optional[ConfigDriftDetectionService] = None
_config_service_lock = threading.Lock()


def get_config_drift_service() -> ConfigDriftDetectionService:
    """Get or create singleton ConfigDriftDetectionService instance (thread-safe)."""
    global _config_service
    if _config_service is None:
        with _config_service_lock:
            if _config_service is None:  # Double-check locking
                _config_service = ConfigDriftDetectionService()
    return _config_service
