"""
Characterisation tests for the SON optimizers and engine in son_functions.py,
which had no coverage. They lock the observable behaviour (which cell scenarios
produce which recommendations, the recommendation shape, the enum vocabulary) so
the optimizers and models can be split into their own modules without changing
what the SON engine recommends.
"""

from datetime import datetime

from service.son_functions import (
    CellMetrics,
    MLBOptimizer,
    RecommendationPriority,
    RecommendationStatus,
    SONEngine,
    SONFunctionType,
    SONRecommendation,
)


def _cell(cell_id, station_id="BS-1", *, prb=50.0, users=100, neighbors=None):
    """A healthy cell; override prb/users/neighbors to shape a scenario."""
    return CellMetrics(
        cell_id=cell_id,
        station_id=station_id,
        timestamp=datetime(2026, 1, 1),
        prb_utilization=prb,
        active_users=users,
        dl_throughput=500.0,
        ul_throughput=100.0,
        rsrp_avg=-85.0,
        sinr_avg=18.0,
        handover_success_rate=99.0,
        handover_failure_rate=1.0,
        rrc_setup_success_rate=99.0,
        paging_success_rate=99.0,
        interference_level=-100.0,
        cqi_avg=12.0,
        power_consumption=800.0,
        neighbor_cells=neighbors or [],
    )


class TestModels:
    def test_recommendation_to_dict_serialises_enums(self):
        rec = SONRecommendation(
            recommendation_id="MLB-BS-0000",
            function_type=SONFunctionType.MLB,
            station_id="BS-1",
            cell_id="C1",
            priority=RecommendationPriority.HIGH,
            status=RecommendationStatus.PENDING,
            created_at=datetime(2026, 1, 1),
            description="d",
            parameters={},
            expected_impact={},
            risk_level="low",
            requires_approval=True,
            auto_rollback=True,
        )
        d = rec.to_dict()
        assert d["function_type"] == "mlb"
        assert d["priority"] == RecommendationPriority.HIGH.value
        assert d["status"] == "pending"
        assert d["executed_at"] is None

    def test_cell_metrics_defaults(self):
        c = _cell("C1")
        assert c.neighbor_cells == []
        assert c.metadata == {}


class TestMLBOptimizer:
    def test_offloads_overloaded_cell_to_underloaded_neighbor(self):
        cells = [
            _cell("C1", prb=92.0, users=300, neighbors=["C2"]),
            _cell("C2", prb=30.0),
        ]
        recs = MLBOptimizer().analyze(cells)
        assert len(recs) == 1
        rec = recs[0]
        assert rec.function_type == SONFunctionType.MLB
        assert rec.parameters["source_cell"] == "C1"
        assert rec.parameters["target_cell"] == "C2"
        # >90% load is treated as high priority.
        assert rec.priority == RecommendationPriority.HIGH

    def test_no_recommendation_when_balanced(self):
        cells = [_cell("C1", prb=50.0, neighbors=["C2"]), _cell("C2", prb=45.0)]
        assert MLBOptimizer().analyze(cells) == []

    def test_no_recommendation_when_neighbor_also_loaded(self):
        cells = [
            _cell("C1", prb=90.0, neighbors=["C2"]),
            _cell("C2", prb=85.0),  # too loaded to receive offload
        ]
        assert MLBOptimizer().analyze(cells) == []


class TestSONEngine:
    def test_analyze_runs_all_functions_and_stores_recommendations(self):
        engine = SONEngine()
        cells = [
            _cell("C1", prb=92.0, users=300, neighbors=["C2"]),
            _cell("C2", prb=30.0),
        ]
        recs = engine.analyze(cells)
        assert isinstance(recs, list)
        # The overload scenario yields at least the MLB offload recommendation,
        # and every returned recommendation is retained in engine storage.
        assert any(r.function_type == SONFunctionType.MLB for r in recs)
        assert all(r.recommendation_id in engine.recommendations for r in recs)

    def test_analyze_can_restrict_to_selected_functions(self):
        engine = SONEngine()
        cells = [
            _cell("C1", prb=92.0, users=300, neighbors=["C2"]),
            _cell("C2", prb=30.0),
        ]
        recs = engine.analyze(cells, functions=[SONFunctionType.MLB])
        assert all(r.function_type == SONFunctionType.MLB for r in recs)
