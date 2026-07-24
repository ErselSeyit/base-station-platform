"""
Characterisation tests for the HTTPAdapter Flask surface in diagnostic_service.py.

The adapter registers ~50 routes and is coupled to many optional downstream
services; these tests drive only the self-contained core routes (health,
diagnose, HMAC auth, learning stats) through a real Flask test client. That is
enough of a behavioural net to move the adapter into its own module without
changing its HTTP contract — registration binds every handler, so an import or
wiring break surfaces as soon as the app is built.
"""

import hashlib
import hmac
import json

import pytest

flask = pytest.importorskip("flask")

from service.diagnostic_service import HTTPAdapter, LearningEngine, Solution


_PROBLEM = {
    "id": "PRB-1",
    "timestamp": "2026-01-01T00:00:00",
    "station_id": "STATION-1",
    "category": "hardware",
    "severity": "critical",
    "code": "CPU_OVERHEAT",
    "message": "hot",
    "metrics": {},
}


def _build_client(monkeypatch, *, secret=None, on_problem=None, learning_engine=None):
    if secret is None:
        monkeypatch.setenv("DIAGNOSTIC_REQUIRE_AUTH", "false")
        monkeypatch.delenv("DIAGNOSTIC_SECRET", raising=False)
    else:
        monkeypatch.setenv("DIAGNOSTIC_REQUIRE_AUTH", "true")
        monkeypatch.setenv("DIAGNOSTIC_SECRET", secret)

    adapter = HTTPAdapter(on_problem=on_problem)
    adapter.learning_engine = learning_engine
    adapter.app = flask.Flask(__name__)
    adapter._register_routes()
    return adapter.app.test_client()


def _solver(problem):
    return Solution(
        problem_id=problem.id,
        action="cool down",
        commands=["fan-max"],
        expected_outcome="temp drops",
        risk_level="low",
        confidence=0.92,
    )


class TestHealth:
    def test_health_is_ok_and_reports_auth_state(self, monkeypatch):
        client = _build_client(monkeypatch)
        resp = client.get("/health")
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["status"] == "ok"
        assert body["authenticated"] is False


class TestDiagnose:
    def test_diagnose_returns_the_solvers_solution(self, monkeypatch):
        client = _build_client(monkeypatch, on_problem=_solver)
        resp = client.post("/diagnose", json=_PROBLEM)
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["action"] == "cool down"
        assert body["commands"] == ["fan-max"]
        assert body["confidence"] == 0.92

    def test_diagnose_without_handler_is_unavailable(self, monkeypatch):
        client = _build_client(monkeypatch, on_problem=None)
        resp = client.post("/diagnose", json=_PROBLEM)
        assert resp.status_code == 503


class TestHmacAuth:
    def test_missing_signature_is_rejected(self, monkeypatch):
        client = _build_client(monkeypatch, secret="s3cret", on_problem=_solver)
        resp = client.post("/diagnose", json=_PROBLEM)
        assert resp.status_code == 401

    def test_wrong_signature_is_forbidden(self, monkeypatch):
        client = _build_client(monkeypatch, secret="s3cret", on_problem=_solver)
        resp = client.post(
            "/diagnose",
            data=json.dumps(_PROBLEM),
            headers={"Content-Type": "application/json", "X-HMAC-Signature": "deadbeef"},
        )
        assert resp.status_code == 403

    def test_valid_signature_is_accepted(self, monkeypatch):
        secret = "s3cret"
        client = _build_client(monkeypatch, secret=secret, on_problem=_solver)
        body = json.dumps(_PROBLEM).encode()
        sig = hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
        resp = client.post(
            "/diagnose",
            data=body,
            headers={"Content-Type": "application/json", "X-HMAC-Signature": sig},
        )
        assert resp.status_code == 200
        assert resp.get_json()["action"] == "cool down"


class TestLearningStats:
    def test_stats_returned_when_engine_present(self, monkeypatch):
        engine = LearningEngine()
        engine.update_pattern("CPU_OVERHEAT", "hardware", True, "fan-max")
        client = _build_client(monkeypatch, learning_engine=engine)
        resp = client.get("/learning/stats")
        assert resp.status_code == 200
        assert resp.get_json()["total_patterns"] == 1

    def test_stats_unavailable_without_engine(self, monkeypatch):
        client = _build_client(monkeypatch, learning_engine=None)
        resp = client.get("/learning/stats")
        assert resp.status_code == 503
