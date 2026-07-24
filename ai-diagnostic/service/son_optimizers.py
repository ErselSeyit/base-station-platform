"""
SON optimization algorithms.

One optimizer per SON function — Mobility Load Balancing (MLB), Mobility
Robustness Optimization (MRO), Coverage and Capacity Optimization (CCO) and
Energy Saving (ES). Each `analyze(cell_metrics)` is a pure function of the cell
snapshot that returns recommendations. Extracted from son_functions.py; the
SONEngine composes them.
"""

from collections import deque
from datetime import datetime
from typing import Dict, List, Optional, Tuple

from .son_models import (
    CellMetrics, SONRecommendation, SONFunctionType,
    RecommendationPriority, RecommendationStatus,
)


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

    def _find_offload_candidates(
        self,
        overloaded_cell: CellMetrics,
        cells: List[CellMetrics]
    ) -> List[Tuple[CellMetrics, float]]:
        """Find candidate cells to offload traffic to."""
        candidates = []
        for neighbor_id in overloaded_cell.neighbor_cells:
            neighbor = next((c for c in cells if c.cell_id == neighbor_id), None)
            if not neighbor or neighbor.prb_utilization >= self.HIGH_LOAD_THRESHOLD - 10:
                continue
            load_diff = overloaded_cell.prb_utilization - neighbor.prb_utilization
            if load_diff > self.LOAD_IMBALANCE_THRESHOLD:
                candidates.append((neighbor, load_diff))
        return sorted(candidates, key=lambda x: x[1], reverse=True)

    def _create_mlb_recommendation(
        self,
        station_id: str,
        overloaded_cell: CellMetrics,
        target_cell: CellMetrics,
        load_diff: float,
        rec_index: int
    ) -> SONRecommendation:
        """Create an MLB recommendation for load balancing."""
        cio_change = min(6.0, load_diff / 10.0)
        current_cio = self.cio_adjustments.get(target_cell.cell_id, 0)

        return SONRecommendation(
            recommendation_id=f"MLB-{station_id[:8]}-{rec_index:04d}",
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
            # Find overloaded cells
            overloaded = [c for c in cells if c.prb_utilization > self.HIGH_LOAD_THRESHOLD]

            for overloaded_cell in overloaded:
                candidates = self._find_offload_candidates(overloaded_cell, cells)
                if candidates:
                    target_cell, load_diff = candidates[0]
                    rec = self._create_mlb_recommendation(
                        station_id, overloaded_cell, target_cell, load_diff, len(recommendations)
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
        _handover_events: Optional[List[Dict]] = None,  # Reserved for detailed HO analysis
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
