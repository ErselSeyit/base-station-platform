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
from datetime import datetime
from typing import List, Dict, Optional, Any

logger = logging.getLogger(__name__)


# Value types extracted to service/son_models.py; re-exported for importers.
from .son_models import (  # noqa: F401
    SONFunctionType, RecommendationStatus, RecommendationPriority,
    CellMetrics, SONRecommendation,
)


# Optimizers extracted to service/son_optimizers.py; re-exported for importers.
from .son_optimizers import (  # noqa: F401
    MLBOptimizer, MROOptimizer, CCOOptimizer, EnergySavingOptimizer,
)


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
