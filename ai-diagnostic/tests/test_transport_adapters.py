"""
Characterisation tests for the transport adapters in diagnostic_service.py
(TCP / Serial / the shared ProtocolAdapter base).

These use fake connections rather than real sockets/serial ports (GOOS: fakes
over mocks) to lock the parse -> dispatch -> serialise-response behaviour, so the
adapters can be moved into their own module without changing what goes on or off
the wire.
"""

import json

import pytest

from service.diagnostic_service import (
    ProtocolAdapter,
    SerialAdapter,
    Solution,
    TCPAdapter,
)

_PROBLEM_JSON = {
    "id": "PRB-9",
    "timestamp": "2026-01-01T00:00:00",
    "station_id": "STATION-9",
    "category": "hardware",
    "severity": "critical",
    "code": "CPU_OVERHEAT",
    "message": "hot",
    "metrics": {"temperature": 90.0},
}


def _solver(problem):
    return Solution(
        problem_id=problem.id,
        action="cool it down",
        commands=["fan-max"],
        expected_outcome="temp drops",
        risk_level="low",
        confidence=0.9,
    )


class _FakeConn:
    """Duck-typed stand-in for a socket connection."""

    def __init__(self, incoming: bytes):
        self._chunks = [incoming]
        self.sent = b""
        self.closed = False

    def recv(self, _n):
        return self._chunks.pop(0) if self._chunks else b""

    def sendall(self, data):
        self.sent += data

    def close(self):
        self.closed = True


class TestProtocolAdapterBase:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            ProtocolAdapter()  # abstract methods not implemented


class TestTCPAdapter:
    def test_handle_client_parses_dispatches_and_responds(self):
        adapter = TCPAdapter(on_problem=_solver)
        conn = _FakeConn(json.dumps(_PROBLEM_JSON).encode() + b"\n")

        adapter._handle_client(conn, ("127.0.0.1", 5555))

        assert conn.closed is True
        reply = json.loads(conn.sent.decode().strip())
        assert reply["problem_id"] == "PRB-9"
        assert reply["action"] == "cool it down"
        assert reply["commands"] == ["fan-max"]

    def test_handle_client_without_solver_sends_nothing(self):
        adapter = TCPAdapter(on_problem=None)
        conn = _FakeConn(json.dumps(_PROBLEM_JSON).encode() + b"\n")

        adapter._handle_client(conn, ("127.0.0.1", 5555))

        assert conn.sent == b""
        assert conn.closed is True

    def test_send_solution_serialises_json_with_newline(self):
        adapter = TCPAdapter()
        conn = _FakeConn(b"")

        adapter.send_solution(_solver_solution(), conn)

        assert conn.sent.endswith(b"\n")
        assert json.loads(conn.sent.decode().strip())["risk_level"] == "low"


def _solver_solution():
    return Solution("PRB-9", "a", ["c"], "o", "low", confidence=0.5)


class TestSerialAdapterParsing:
    def test_process_buffer_dispatches_complete_lines_and_keeps_remainder(self):
        seen = []
        adapter = SerialAdapter(on_problem=lambda p: seen.append(p.id) or _solver(p))
        line = json.dumps(_PROBLEM_JSON)

        remainder = adapter._process_buffer(line + "\n" + '{"partial":')

        assert seen == ["PRB-9"]
        assert remainder == '{"partial":'

    def test_process_message_ignores_invalid_json(self):
        called = []
        adapter = SerialAdapter(on_problem=lambda p: called.append(p) or _solver(p))

        adapter._process_message("not json at all")

        assert called == []  # invalid frame swallowed, solver never invoked
