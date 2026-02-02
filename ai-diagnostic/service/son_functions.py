#!/usr/bin/env python3
"""
Self-Organizing Network (SON) Functions

Implements 3GPP SON functions for 5G network optimization:
- MLB (Mobility Load Balancing)
- MRO (Mobility Robustness Optimization)
- CCO (Coverage and Capacity Optimization)
- ES (Energy Saving)
- ANR (Automatic Neighbor Relation)
- RAO (Random Access Optimization)
- ICIC (Inter-Cell Interference Coordination)

Based on 3GPP TS 32.500 and TS 28.313 specifications.
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


class MLBOptimizer:
    """
    Mobility Load Balancing (MLB) optimizer.

    Balances load across cells by adjusting:
    - Cell Individual Offset (CIO)
    - Handover hysteresis
    - Time-to-Trigger (TTT)
    """

    # Load thresholds
    HIGH_LOAD_THRESHOLD = 80.0  # %
    LOW_LOAD_THRESHOLD = 30.0   # %
    LOAD_IMBALANCE_THRESHOLD = 20.0  # % difference

    def __init__(self):
        self.cell_history: Dict[str, deque] = {}
        self.cio_adjustments: Dict[str, float] = {}  # cell_id -> current CIO offset

    def analyze(
        self,
        cell_metrics: List[CellMetrics],
    ) -> List[SONRecommendation]:
        """
        Analyze cell load and generate MLB recommendations.

        Returns:
            List of MLB recommendations
        """
        recommendations = []

        # Group by station
        station_cells: Dict[str, List[CellMetrics]] = {}
        for cm in cell_metrics:
            if cm.station_id not in station_cells:
                station_cells[cm.station_id] = []
            station_cells[cm.station_id].append(cm)

        for station_id, cells in station_cells.items():
            # Find overloaded and underloaded cells
            overloaded = [c for c in cells if c.prb_utilization > self.HIGH_LOAD_THRESHOLD]
            underloaded = [c for c in cells if c.prb_utilization < self.LOW_LOAD_THRESHOLD]

            for overloaded_cell in overloaded:
                # Find candidate cells to offload to
                candidates = []
                for neighbor_id in overloaded_cell.neighbor_cells:
                    neighbor = next((c for c in cells if c.cell_id == neighbor_id), None)
                    if neighbor and neighbor.prb_utilization < self.HIGH_LOAD_THRESHOLD - 10:
                        load_diff = overloaded_cell.prb_utilization - neighbor.prb_utilization
                        if load_diff > self.LOAD_IMBALANCE_THRESHOLD:
                            candidates.append((neighbor, load_diff))

                if candidates:
                    # Sort by load difference (highest first)
                    candidates.sort(key=lambda x: x[1], reverse=True)
                    target_cell, load_diff = candidates[0]

                    # Calculate CIO adjustment
                    cio_change = min(6.0, load_diff / 10.0)  # Max 6dB change

                    current_cio = self.cio_adjustments.get(target_cell.cell_id, 0)

                    rec = SONRecommendation(
                        recommendation_id=f"MLB-{station_id[:8]}-{len(recommendations):04d}",
                        function_type=SONFunctionType.MLB,
                        station_id=station_id,
                        cell_id=overloaded_cell.cell_id,
                        priority=RecommendationPriority.HIGH if overloaded_cell.prb_utilization > 90 else RecommendationPriority.MEDIUM,
                        status=RecommendationStatus.PENDING,
                        created_at=datetime.now(),
                        description=(
                            f"Offload traffic from {overloaded_cell.cell_id} "
                            f"({overloaded_cell.prb_utilization:.1f}% load) to "
                            f"{target_cell.cell_id} ({target_cell.prb_utilization:.1f}% load)"
                        ),
                        parameters={
                            "source_cell": overloaded_cell.cell_id,
                            "target_cell": target_cell.cell_id,
                            "action": "adjust_cio",
                            "cio_change": cio_change,
                            "new_cio": current_cio + cio_change,
                        },
                        expected_impact={
                            "load_reduction": min(15.0, load_diff * 0.3),
                            "affected_users_estimate": int(overloaded_cell.active_users * 0.15),
                        },
                        risk_level="low",
                        requires_approval=True,
                        auto_rollback=True,
                        rollback_params={
                            "cell_id": target_cell.cell_id,
                            "cio_value": current_cio,
                        },
                    )
                    recommendations.append(rec)

        return recommendations


class MROOptimizer:
    """
    Mobility Robustness Optimization (MRO) optimizer.

    Reduces handover failures by adjusting:
    - A3 offset
    - Handover hysteresis
    - Time-to-Trigger (TTT)

    Handles:
    - Too-late handovers (RLF before HO)
    - Too-early handovers (RLF after HO)
    - Wrong cell handovers
    - Ping-pong handovers
    """

    # Thresholds
    HO_SUCCESS_RATE_THRESHOLD = 95.0  # %
    PING_PONG_THRESHOLD = 5.0  # %

    def analyze(
        self,
        cell_metrics: List[CellMetrics],
        handover_events: Optional[List[Dict]] = None,
    ) -> List[SONRecommendation]:
        """
        Analyze handover performance and generate MRO recommendations.
        """
        recommendations = []

        for cm in cell_metrics:
            # Check handover success rate
            if cm.handover_success_rate < self.HO_SUCCESS_RATE_THRESHOLD:
                failure_rate = cm.handover_failure_rate

                # Determine likely cause based on failure patterns
                if failure_rate > 10:
                    # High failure rate - likely too-late HO
                    rec = SONRecommendation(
                        recommendation_id=f"MRO-{cm.station_id[:8]}-{len(recommendations):04d}",
                        function_type=SONFunctionType.MRO,
                        station_id=cm.station_id,
                        cell_id=cm.cell_id,
                        priority=RecommendationPriority.HIGH,
                        status=RecommendationStatus.PENDING,
                        created_at=datetime.now(),
                        description=(
                            f"High handover failure rate ({failure_rate:.1f}%) in {cm.cell_id}. "
                            f"Recommend reducing TTT to trigger earlier handovers."
                        ),
                        parameters={
                            "action": "adjust_ttt",
                            "current_ho_success": cm.handover_success_rate,
                            "ttt_reduction_ms": 40,  # Reduce by 40ms
                            "a3_offset_change": -1.0,  # Reduce A3 offset by 1dB
                        },
                        expected_impact={
                            "ho_success_improvement": 3.0,  # % improvement
                            "rlf_reduction": 2.0,  # % reduction
                        },
                        risk_level="medium",
                        requires_approval=True,
                        auto_rollback=True,
                    )
                    recommendations.append(rec)

                elif failure_rate > 5:
                    # Moderate failure - check for specific patterns
                    rec = SONRecommendation(
                        recommendation_id=f"MRO-{cm.station_id[:8]}-{len(recommendations):04d}",
                        function_type=SONFunctionType.MRO,
                        station_id=cm.station_id,
                        cell_id=cm.cell_id,
                        priority=RecommendationPriority.MEDIUM,
                        status=RecommendationStatus.PENDING,
                        created_at=datetime.now(),
                        description=(
                            f"Elevated handover failure rate ({failure_rate:.1f}%) in {cm.cell_id}. "
                            f"Recommend adjusting hysteresis."
                        ),
                        parameters={
                            "action": "adjust_hysteresis",
                            "hysteresis_change": -0.5,  # dB
                        },
                        expected_impact={
                            "ho_success_improvement": 1.5,
                        },
                        risk_level="low",
                        requires_approval=True,
                        auto_rollback=True,
                    )
                    recommendations.append(rec)

        return recommendations


class CCOOptimizer:
    """
    Coverage and Capacity Optimization (CCO) optimizer.

    Optimizes:
    - Antenna tilts (electrical and mechanical)
    - Transmit power
    - Pilot power

    Balances coverage and capacity based on:
    - RSRP distribution
    - Interference levels
    - Throughput requirements
    """

    # Thresholds
    RSRP_POOR_THRESHOLD = -110.0  # dBm
    SINR_POOR_THRESHOLD = 0.0  # dB
    INTERFERENCE_HIGH_THRESHOLD = -90.0  # dBm

    def analyze(
        self,
        cell_metrics: List[CellMetrics],
    ) -> List[SONRecommendation]:
        """
        Analyze coverage/capacity and generate CCO recommendations.
        """
        recommendations = []

        for cm in cell_metrics:
            # Check for coverage issues
            if cm.rsrp_avg < self.RSRP_POOR_THRESHOLD:
                rec = SONRecommendation(
                    recommendation_id=f"CCO-{cm.station_id[:8]}-{len(recommendations):04d}",
                    function_type=SONFunctionType.CCO,
                    station_id=cm.station_id,
                    cell_id=cm.cell_id,
                    priority=RecommendationPriority.MEDIUM,
                    status=RecommendationStatus.PENDING,
                    created_at=datetime.now(),
                    description=(
                        f"Poor coverage in {cm.cell_id} (RSRP: {cm.rsrp_avg:.1f} dBm). "
                        f"Recommend increasing transmit power or adjusting tilt."
                    ),
                    parameters={
                        "action": "increase_coverage",
                        "current_rsrp": cm.rsrp_avg,
                        "power_increase_db": 1.0,
                        "tilt_decrease_deg": 1.0,  # Uptilt to extend coverage
                    },
                    expected_impact={
                        "rsrp_improvement_db": 2.0,
                        "coverage_area_increase_percent": 5.0,
                    },
                    risk_level="medium",
                    requires_approval=True,
                    auto_rollback=True,
                )
                recommendations.append(rec)

            # Check for interference issues
            if cm.interference_level > self.INTERFERENCE_HIGH_THRESHOLD:
                rec = SONRecommendation(
                    recommendation_id=f"CCO-{cm.station_id[:8]}-{len(recommendations):04d}",
                    function_type=SONFunctionType.CCO,
                    station_id=cm.station_id,
                    cell_id=cm.cell_id,
                    priority=RecommendationPriority.HIGH,
                    status=RecommendationStatus.PENDING,
                    created_at=datetime.now(),
                    description=(
                        f"High interference in {cm.cell_id} ({cm.interference_level:.1f} dBm). "
                        f"Recommend downtilt to reduce overshooting."
                    ),
                    parameters={
                        "action": "reduce_interference",
                        "current_interference": cm.interference_level,
                        "tilt_increase_deg": 2.0,  # Downtilt
                        "power_reduction_db": 1.0,
                    },
                    expected_impact={
                        "interference_reduction_db": 3.0,
                        "sinr_improvement_db": 2.0,
                    },
                    risk_level="medium",
                    requires_approval=True,
                    auto_rollback=True,
                )
                recommendations.append(rec)

        return recommendations


class EnergySavingOptimizer:
    """
    Energy Saving (ES) optimizer.

    Implements intelligent cell sleep modes:
    - Capacity-based dormancy
    - Time-based scheduling
    - Dynamic carrier shutdown
    """

    LOW_TRAFFIC_THRESHOLD = 20.0  # % PRB utilization
    MINIMUM_ACTIVE_CELLS = 1

    def analyze(
        self,
        cell_metrics: List[CellMetrics],
        time_of_day: Optional[datetime] = None,
    ) -> List[SONRecommendation]:
        """
        Analyze traffic patterns and generate energy saving recommendations.
        """
        recommendations = []
        current_time = time_of_day or datetime.now()

        # Group cells by station
        station_cells: Dict[str, List[CellMetrics]] = {}
        for cm in cell_metrics:
            if cm.station_id not in station_cells:
                station_cells[cm.station_id] = []
            station_cells[cm.station_id].append(cm)

        for station_id, cells in station_cells.items():
            if len(cells) <= self.MINIMUM_ACTIVE_CELLS:
                continue

            # Sort by utilization
            cells_sorted = sorted(cells, key=lambda c: c.prb_utilization)

            # Check if lowest utilized cells can be shut down
            total_users = sum(c.active_users for c in cells)
            lowest_cell = cells_sorted[0]

            # Only recommend shutdown during low traffic periods
            is_low_traffic_period = (
                current_time.hour < 6 or current_time.hour >= 23 or
                (lowest_cell.prb_utilization < self.LOW_TRAFFIC_THRESHOLD and total_users < 50)
            )

            if is_low_traffic_period and lowest_cell.prb_utilization < self.LOW_TRAFFIC_THRESHOLD:
                # Calculate potential savings
                power_saved = lowest_cell.power_consumption

                rec = SONRecommendation(
                    recommendation_id=f"ES-{station_id[:8]}-{len(recommendations):04d}",
                    function_type=SONFunctionType.ES,
                    station_id=station_id,
                    cell_id=lowest_cell.cell_id,
                    priority=RecommendationPriority.LOW,
                    status=RecommendationStatus.PENDING,
                    created_at=datetime.now(),
                    description=(
                        f"Low traffic on {lowest_cell.cell_id} ({lowest_cell.prb_utilization:.1f}% util, "
                        f"{lowest_cell.active_users} users). Recommend cell dormancy."
                    ),
                    parameters={
                        "action": "cell_dormancy",
                        "current_utilization": lowest_cell.prb_utilization,
                        "active_users": lowest_cell.active_users,
                        "sleep_duration_minutes": 30,
                        "wake_trigger": "capacity_threshold",
                        "wake_threshold_users": 10,
                    },
                    expected_impact={
                        "power_savings_watts": power_saved,
                        "energy_savings_kwh_per_day": power_saved * 0.024,  # Assuming 1 hour sleep
                        "user_impact": lowest_cell.active_users,
                    },
                    risk_level="low",
                    requires_approval=True,
                    auto_rollback=True,
                )
                recommendations.append(rec)

        return recommendations


class SONEngine:
    """
    Main SON engine that orchestrates all SON functions.
    """

    def __init__(self):
        self.mlb = MLBOptimizer()
        self.mro = MROOptimizer()
        self.cco = CCOOptimizer()
        self.es = EnergySavingOptimizer()

        # Recommendation storage
        self.recommendations: Dict[str, SONRecommendation] = {}
        self.recommendation_count = 0

        logger.info("SON Engine initialized")

    def analyze(
        self,
        cell_metrics: List[CellMetrics],
        functions: Optional[List[SONFunctionType]] = None,
    ) -> List[SONRecommendation]:
        """
        Run SON analysis and generate recommendations.

        Args:
            cell_metrics: List of cell metrics
            functions: Optional list of SON functions to run (default: all)

        Returns:
            List of recommendations
        """
        if functions is None:
            functions = [SONFunctionType.MLB, SONFunctionType.MRO,
                        SONFunctionType.CCO, SONFunctionType.ES]

        all_recommendations = []

        if SONFunctionType.MLB in functions:
            all_recommendations.extend(self.mlb.analyze(cell_metrics))

        if SONFunctionType.MRO in functions:
            all_recommendations.extend(self.mro.analyze(cell_metrics))

        if SONFunctionType.CCO in functions:
            all_recommendations.extend(self.cco.analyze(cell_metrics))

        if SONFunctionType.ES in functions:
            all_recommendations.extend(self.es.analyze(cell_metrics))

        # Store recommendations
        for rec in all_recommendations:
            self.recommendations[rec.recommendation_id] = rec

        logger.info(f"SON analysis generated {len(all_recommendations)} recommendations")

        return all_recommendations

    def approve_recommendation(self, recommendation_id: str) -> bool:
        """Approve a pending recommendation."""
        if recommendation_id not in self.recommendations:
            return False

        rec = self.recommendations[recommendation_id]
        if rec.status != RecommendationStatus.PENDING:
            return False

        rec.status = RecommendationStatus.APPROVED
        logger.info(f"Recommendation {recommendation_id} approved")
        return True

    def reject_recommendation(self, recommendation_id: str, reason: str = "") -> bool:
        """Reject a pending recommendation."""
        if recommendation_id not in self.recommendations:
            return False

        rec = self.recommendations[recommendation_id]
        if rec.status != RecommendationStatus.PENDING:
            return False

        rec.status = RecommendationStatus.REJECTED
        rec.result = {"rejection_reason": reason}
        logger.info(f"Recommendation {recommendation_id} rejected: {reason}")
        return True

    def execute_recommendation(self, recommendation_id: str) -> Dict[str, Any]:
        """
        Execute an approved recommendation.

        In production, this would integrate with network management systems.
        """
        if recommendation_id not in self.recommendations:
            return {"success": False, "error": "Recommendation not found"}

        rec = self.recommendations[recommendation_id]
        if rec.status != RecommendationStatus.APPROVED:
            return {"success": False, "error": "Recommendation not approved"}

        # Simulate execution
        rec.status = RecommendationStatus.EXECUTED
        rec.executed_at = datetime.now()
        rec.result = {
            "execution_status": "success",
            "applied_parameters": rec.parameters,
            "timestamp": datetime.now().isoformat(),
        }

        logger.info(f"Recommendation {recommendation_id} executed")

        return {"success": True, "result": rec.result}

    def get_recommendation(self, recommendation_id: str) -> Optional[SONRecommendation]:
        """Get a specific recommendation."""
        return self.recommendations.get(recommendation_id)

    def get_pending_recommendations(
        self,
        station_id: Optional[str] = None,
        function_type: Optional[SONFunctionType] = None,
    ) -> List[SONRecommendation]:
        """Get all pending recommendations, optionally filtered."""
        recs = [
            r for r in self.recommendations.values()
            if r.status == RecommendationStatus.PENDING
        ]

        if station_id:
            recs = [r for r in recs if r.station_id == station_id]

        if function_type:
            recs = [r for r in recs if r.function_type == function_type]

        return sorted(recs, key=lambda r: r.priority.value, reverse=True)


# Singleton instance
_son_engine: Optional[SONEngine] = None


def get_son_engine() -> SONEngine:
    """Get or create the SON engine singleton."""
    global _son_engine
    if _son_engine is None:
        _son_engine = SONEngine()
    return _son_engine


# Convenience functions for API integration
def analyze_cells(
    cell_data: List[Dict[str, Any]],
    functions: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """
    API-friendly function to analyze cells and generate SON recommendations.

    Args:
        cell_data: List of cell metric dicts
        functions: Optional list of function names ("mlb", "mro", "cco", "es")

    Returns:
        List of recommendation dicts
    """
    engine = get_son_engine()

    # Convert to CellMetrics
    metrics = []
    for cd in cell_data:
        try:
            cm = CellMetrics(
                cell_id=cd["cell_id"],
                station_id=cd["station_id"],
                timestamp=datetime.fromisoformat(cd.get("timestamp", datetime.now().isoformat())),
                prb_utilization=cd.get("prb_utilization", 50.0),
                active_users=cd.get("active_users", 0),
                dl_throughput=cd.get("dl_throughput", 0.0),
                ul_throughput=cd.get("ul_throughput", 0.0),
                rsrp_avg=cd.get("rsrp_avg", -90.0),
                sinr_avg=cd.get("sinr_avg", 10.0),
                handover_success_rate=cd.get("handover_success_rate", 99.0),
                handover_failure_rate=cd.get("handover_failure_rate", 1.0),
                rrc_setup_success_rate=cd.get("rrc_setup_success_rate", 99.0),
                paging_success_rate=cd.get("paging_success_rate", 99.0),
                interference_level=cd.get("interference_level", -100.0),
                cqi_avg=cd.get("cqi_avg", 10.0),
                power_consumption=cd.get("power_consumption", 500.0),
                neighbor_cells=cd.get("neighbor_cells", []),
            )
            metrics.append(cm)
        except Exception as e:
            logger.error(f"Failed to parse cell data: {e}")
            continue

    # Parse function types
    func_types = None
    if functions:
        func_types = []
        for f in functions:
            try:
                func_types.append(SONFunctionType(f.lower()))
            except ValueError:
                logger.warning(f"Unknown SON function: {f}")

    recommendations = engine.analyze(metrics, func_types)

    return [r.to_dict() for r in recommendations]


def approve_recommendation(recommendation_id: str) -> bool:
    """Approve a SON recommendation."""
    engine = get_son_engine()
    return engine.approve_recommendation(recommendation_id)


def reject_recommendation(recommendation_id: str, reason: str = "") -> bool:
    """Reject a SON recommendation."""
    engine = get_son_engine()
    return engine.reject_recommendation(recommendation_id, reason)


def execute_recommendation(recommendation_id: str) -> Dict[str, Any]:
    """Execute an approved SON recommendation."""
    engine = get_son_engine()
    return engine.execute_recommendation(recommendation_id)


def get_pending(
    station_id: Optional[str] = None,
    function_type: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """Get pending SON recommendations."""
    engine = get_son_engine()

    func_type = None
    if function_type:
        try:
            func_type = SONFunctionType(function_type.lower())
        except ValueError:
            pass

    recs = engine.get_pending_recommendations(station_id, func_type)
    return [r.to_dict() for r in recs]
