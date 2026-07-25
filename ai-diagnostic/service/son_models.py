"""
Value types for the SON (Self-Organizing Network) functions.

The function/status/priority enums and the CellMetrics / SONRecommendation
records, extracted from son_functions.py so the optimizers and engine can share
them without a circular import.
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional


class SONFunctionType(Enum):
    """Types of SON functions."""
    MLB = "mlb"   # Mobility Load Balancing
    MRO = "mro"   # Mobility Robustness Optimization
    CCO = "cco"   # Coverage and Capacity Optimization
    ES = "es"     # Energy Saving
    ANR = "anr"   # Automatic Neighbor Relation
    RAO = "rao"   # Random Access Optimization
    ICIC = "icic" # Inter-Cell Interference Coordination


class RecommendationStatus(Enum):
    """Status of SON recommendations."""
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"
    EXECUTED = "executed"
    FAILED = "failed"
    ROLLED_BACK = "rolled_back"


class RecommendationPriority(Enum):
    """Priority levels for recommendations."""
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


@dataclass
class CellMetrics:
    """Metrics for a cell."""
    cell_id: str
    station_id: str
    timestamp: datetime
    prb_utilization: float  # 0-100%
    active_users: int
    dl_throughput: float  # Mbps
    ul_throughput: float  # Mbps
    rsrp_avg: float  # dBm
    sinr_avg: float  # dB
    handover_success_rate: float  # 0-100%
    handover_failure_rate: float  # 0-100%
    rrc_setup_success_rate: float  # 0-100%
    paging_success_rate: float  # 0-100%
    interference_level: float  # dBm
    cqi_avg: float  # 0-15
    power_consumption: float  # Watts
    neighbor_cells: List[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class SONRecommendation:
    """A SON function recommendation."""
    recommendation_id: str
    function_type: SONFunctionType
    station_id: str
    cell_id: str
    priority: RecommendationPriority
    status: RecommendationStatus
    created_at: datetime
    description: str
    parameters: Dict[str, Any]
    expected_impact: Dict[str, Any]
    risk_level: str
    requires_approval: bool
    auto_rollback: bool
    rollback_params: Optional[Dict[str, Any]] = None
    executed_at: Optional[datetime] = None
    result: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "recommendation_id": self.recommendation_id,
            "function_type": self.function_type.value,
            "station_id": self.station_id,
            "cell_id": self.cell_id,
            "priority": self.priority.value,
            "status": self.status.value,
            "created_at": self.created_at.isoformat(),
            "description": self.description,
            "parameters": self.parameters,
            "expected_impact": self.expected_impact,
            "risk_level": self.risk_level,
            "requires_approval": self.requires_approval,
            "auto_rollback": self.auto_rollback,
            "rollback_params": self.rollback_params,
            "executed_at": self.executed_at.isoformat() if self.executed_at else None,
            "result": self.result,
        }
