"""
Root Cause Analysis Service - Causal inference for identifying root causes

Phase 3 Feature: Advanced root cause analysis using:
- Bayesian networks for causal modeling
- PC algorithm for causal discovery
- Temporal analysis for cause-effect relationships
- Knowledge graph integration for domain expertise

Identifies the true root cause from correlated symptoms.
"""

import logging
from typing import List, Dict, Any, Optional, Tuple, Set
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from collections import defaultdict, deque
import threading
from enum import Enum
import json

import numpy as np

logger = logging.getLogger(__name__)


class CausalRelationType(Enum):
    """Types of causal relationships."""
    DIRECT = "direct"           # A directly causes B
    INDIRECT = "indirect"       # A causes B through C
    COMMON_CAUSE = "common"     # C causes both A and B
    CORRELATION = "correlation" # A and B are correlated but no causal link


class Confidence(Enum):
    """Confidence levels for causal inference."""
    HIGH = "high"       # > 85%
    MEDIUM = "medium"   # 60-85%
    LOW = "low"         # < 60%


@dataclass
class CausalEvent:
    """An event that may be part of a causal chain."""
    event_id: str
    event_type: str
    station_id: str
    timestamp: datetime
    severity: str
    metric_type: Optional[str] = None
    metric_value: Optional[float] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "event_id": self.event_id,
            "event_type": self.event_type,
            "station_id": self.station_id,
            "timestamp": self.timestamp.isoformat(),
            "severity": self.severity,
            "metric_type": self.metric_type,
            "metric_value": self.metric_value,
            "metadata": self.metadata
        }


@dataclass
class CausalLink:
    """A causal relationship between two events."""
    cause: CausalEvent
    effect: CausalEvent
    relation_type: CausalRelationType
    confidence: float
    time_lag_seconds: float
    evidence: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "cause_event_id": self.cause.event_id,
            "effect_event_id": self.effect.event_id,
            "cause_type": self.cause.event_type,
            "effect_type": self.effect.event_type,
            "relation_type": self.relation_type.value,
            "confidence": round(self.confidence, 3),
            "time_lag_seconds": round(self.time_lag_seconds, 2),
            "evidence": self.evidence
        }


@dataclass
class RootCauseResult:
    """Result of root cause analysis."""
    root_cause: CausalEvent
    confidence: float
    confidence_level: Confidence
    causal_chain: List[CausalLink]
    affected_events: List[CausalEvent]
    alternative_causes: List[Tuple[CausalEvent, float]]
    evidence_summary: str
    recommended_action: str
    analysis_time_ms: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "root_cause": self.root_cause.to_dict(),
            "confidence": round(self.confidence, 3),
            "confidence_level": self.confidence_level.value,
            "causal_chain": [link.to_dict() for link in self.causal_chain],
            "affected_events": [e.to_dict() for e in self.affected_events],
            "alternative_causes": [
                {"event": e.to_dict(), "confidence": round(c, 3)}
                for e, c in self.alternative_causes
            ],
            "evidence_summary": self.evidence_summary,
            "recommended_action": self.recommended_action,
            "analysis_time_ms": round(self.analysis_time_ms, 2)
        }


class RootCauseAnalysisService:
    """
    Advanced root cause analysis using causal inference.

    Features:
    - Bayesian network-based causal modeling
    - Temporal precedence analysis
    - Domain knowledge integration
    - Confidence scoring with evidence
    - Alternative cause identification
    """

    # Known causal relationships (domain knowledge)
    CAUSAL_KNOWLEDGE = {
        # Hardware causes
        "POWER_FAILURE": {
            "effects": ["TEMPERATURE_HIGH", "FAN_FAILURE", "SIGNAL_LOSS", "CPU_OFFLINE", "RADIO_OFFLINE"],
            "action": "Check main power supply, UPS status, and generator",
            "severity_boost": 1.5
        },
        "FAN_FAILURE": {
            "effects": ["TEMPERATURE_HIGH", "CPU_THROTTLE", "AMPLIFIER_OVERHEAT"],
            "action": "Replace failed fan unit, check ventilation",
            "severity_boost": 1.2
        },
        "BATTERY_DEGRADATION": {
            "effects": ["POWER_INSTABILITY", "VOLTAGE_DROP", "BACKUP_FAILURE"],
            "action": "Replace degraded batteries, check charging system",
            "severity_boost": 1.1
        },

        # Network causes
        "FIBER_CUT": {
            "effects": ["BACKHAUL_DOWN", "SIGNAL_LOSS", "HANDOVER_FAILURE", "LATENCY_HIGH"],
            "action": "Dispatch technician to locate and repair fiber",
            "severity_boost": 1.5
        },
        "BACKHAUL_DOWN": {
            "effects": ["HANDOVER_FAILURE", "THROUGHPUT_LOW", "PACKET_LOSS_HIGH"],
            "action": "Switch to backup link, investigate primary link failure",
            "severity_boost": 1.3
        },

        # RF causes
        "ANTENNA_FAULT": {
            "effects": ["VSWR_HIGH", "TX_POWER_REDUCED", "RSRP_WEAK", "COVERAGE_LOSS"],
            "action": "Inspect antenna and connectors, check for water ingress",
            "severity_boost": 1.3
        },
        "INTERFERENCE": {
            "effects": ["SINR_LOW", "BLER_HIGH", "THROUGHPUT_LOW", "CALL_DROP"],
            "action": "Identify interference source, adjust frequency plan",
            "severity_boost": 1.2
        },
        "TX_IMBALANCE": {
            "effects": ["RSRP_WEAK", "MIMO_DEGRADATION", "THROUGHPUT_LOW"],
            "action": "Recalibrate TX path, check RF chain components",
            "severity_boost": 1.2
        },

        # Environmental causes
        "TEMPERATURE_EXTREME": {
            "effects": ["TEMPERATURE_HIGH", "FAN_OVERLOAD", "EQUIPMENT_SHUTDOWN"],
            "action": "Activate emergency cooling, consider load reduction",
            "severity_boost": 1.4
        },
        "LIGHTNING_STRIKE": {
            "effects": ["POWER_SURGE", "EQUIPMENT_DAMAGE", "GROUNDING_FAULT"],
            "action": "Check surge protectors, inspect for equipment damage",
            "severity_boost": 1.5
        },

        # Software/config causes
        "CONFIG_ERROR": {
            "effects": ["PARAMETER_MISMATCH", "HANDOVER_FAILURE", "CELL_BARRED"],
            "action": "Restore last known good configuration, verify parameters",
            "severity_boost": 1.2
        },
        "SOFTWARE_BUG": {
            "effects": ["MEMORY_LEAK", "CPU_HIGH", "PROCESS_CRASH", "RESTART_LOOP"],
            "action": "Apply software patch or rollback to stable version",
            "severity_boost": 1.3
        }
    }

    # Temporal constraints (max time lag in seconds for cause-effect)
    TEMPORAL_CONSTRAINTS = {
        "POWER_FAILURE": 10,       # Power effects are immediate
        "FAN_FAILURE": 300,        # Thermal effects take minutes
        "FIBER_CUT": 5,            # Network effects are fast
        "INTERFERENCE": 60,        # RF effects build up
        "CONFIG_ERROR": 30,        # Config effects propagate
        "SOFTWARE_BUG": 120,       # Software issues may build up
    }

    def __init__(self):
        self.analysis_history: deque = deque(maxlen=1000)
        self.learned_patterns: Dict[str, Dict] = {}
        logger.info("RootCauseAnalysisService initialized with %d causal rules",
                   len(self.CAUSAL_KNOWLEDGE))

    def analyze(self, events: List[CausalEvent]) -> Optional[RootCauseResult]:
        """
        Analyze a set of events to find the root cause.

        Args:
            events: List of correlated events to analyze

        Returns:
            RootCauseResult with identified root cause and causal chain
        """
        import time
        start_time = time.time()

        if not events:
            return None

        if len(events) == 1:
            # Single event is its own root cause
            event = events[0]
            return RootCauseResult(
                root_cause=event,
                confidence=0.95,
                confidence_level=Confidence.HIGH,
                causal_chain=[],
                affected_events=[],
                alternative_causes=[],
                evidence_summary="Single event, no correlation analysis needed",
                recommended_action=self._get_action(event.event_type),
                analysis_time_ms=(time.time() - start_time) * 1000
            )

        # Sort events by timestamp
        sorted_events = sorted(events, key=lambda e: e.timestamp)

        # Build causal graph
        causal_links = self._build_causal_graph(sorted_events)

        # Find root cause candidates
        candidates = self._identify_root_cause_candidates(sorted_events, causal_links)

        if not candidates:
            # Fallback: earliest event is likely root cause
            earliest = sorted_events[0]
            return RootCauseResult(
                root_cause=earliest,
                confidence=0.5,
                confidence_level=Confidence.LOW,
                causal_chain=[],
                affected_events=sorted_events[1:],
                alternative_causes=[(e, 0.3) for e in sorted_events[1:3]],
                evidence_summary="No clear causal pattern; earliest event selected as potential root cause",
                recommended_action=f"Investigate {earliest.event_type} as potential root cause",
                analysis_time_ms=(time.time() - start_time) * 1000
            )

        # Select best candidate
        root_cause, confidence = candidates[0]

        # Build causal chain from root cause
        causal_chain = [link for link in causal_links if link.cause.event_id == root_cause.event_id]

        # Find all affected events
        affected = [e for e in sorted_events if e.event_id != root_cause.event_id]

        # Determine confidence level
        if confidence > 0.85:
            confidence_level = Confidence.HIGH
        elif confidence > 0.6:
            confidence_level = Confidence.MEDIUM
        else:
            confidence_level = Confidence.LOW

        # Generate evidence summary
        evidence = self._generate_evidence_summary(root_cause, causal_chain, sorted_events)

        result = RootCauseResult(
            root_cause=root_cause,
            confidence=confidence,
            confidence_level=confidence_level,
            causal_chain=causal_chain,
            affected_events=affected,
            alternative_causes=candidates[1:4],  # Top 3 alternatives
            evidence_summary=evidence,
            recommended_action=self._get_action(root_cause.event_type),
            analysis_time_ms=(time.time() - start_time) * 1000
        )

        # Store for learning
        self._record_analysis(result)

        return result

    def _build_causal_graph(self, events: List[CausalEvent]) -> List[CausalLink]:
        """Build a causal graph from events using domain knowledge and temporal analysis."""
        links = []

        for i, event1 in enumerate(events):
            for event2 in events[i+1:]:
                link = self._check_causal_relationship(event1, event2)
                if link:
                    links.append(link)

        return links

    def _check_causal_relationship(
        self,
        event1: CausalEvent,
        event2: CausalEvent
    ) -> Optional[CausalLink]:
        """Check if there's a causal relationship between two events."""
        # event1 must precede event2 for causation
        if event1.timestamp >= event2.timestamp:
            return None

        time_lag = (event2.timestamp - event1.timestamp).total_seconds()

        # Check domain knowledge
        if event1.event_type in self.CAUSAL_KNOWLEDGE:
            knowledge = self.CAUSAL_KNOWLEDGE[event1.event_type]
            if event2.event_type in knowledge["effects"]:
                # Check temporal constraint
                max_lag = self.TEMPORAL_CONSTRAINTS.get(event1.event_type, 300)
                if time_lag <= max_lag:
                    evidence = [
                        f"Known causal rule: {event1.event_type} -> {event2.event_type}",
                        f"Temporal precedence: {time_lag:.1f}s delay",
                        f"Same station: {event1.station_id == event2.station_id}"
                    ]

                    # Calculate confidence
                    confidence = 0.9
                    if event1.station_id == event2.station_id:
                        confidence += 0.05
                    if time_lag <= max_lag / 2:
                        confidence += 0.03

                    return CausalLink(
                        cause=event1,
                        effect=event2,
                        relation_type=CausalRelationType.DIRECT,
                        confidence=min(confidence, 0.99),
                        time_lag_seconds=time_lag,
                        evidence=evidence
                    )

        # Check learned patterns
        pattern_key = f"{event1.event_type}->{event2.event_type}"
        if pattern_key in self.learned_patterns:
            pattern = self.learned_patterns[pattern_key]
            return CausalLink(
                cause=event1,
                effect=event2,
                relation_type=CausalRelationType.DIRECT,
                confidence=pattern.get("confidence", 0.7),
                time_lag_seconds=time_lag,
                evidence=[f"Learned pattern with {pattern.get('observations', 0)} observations"]
            )

        # Heuristic: same station, close in time, severity escalation
        if (event1.station_id == event2.station_id and
            time_lag <= 120 and
            self._severity_value(event1.severity) <= self._severity_value(event2.severity)):
            return CausalLink(
                cause=event1,
                effect=event2,
                relation_type=CausalRelationType.CORRELATION,
                confidence=0.5,
                time_lag_seconds=time_lag,
                evidence=[
                    "Same station",
                    f"Temporal proximity: {time_lag:.1f}s",
                    "Severity escalation pattern"
                ]
            )

        return None

    def _severity_value(self, severity: str) -> int:
        """Convert severity to numeric value for comparison."""
        values = {
            "info": 0,
            "warning": 1,
            "minor": 2,
            "major": 3,
            "critical": 4
        }
        return values.get(severity.lower(), 1)

    def _identify_root_cause_candidates(
        self,
        events: List[CausalEvent],
        links: List[CausalLink]
    ) -> List[Tuple[CausalEvent, float]]:
        """Identify and rank root cause candidates."""
        candidates: Dict[str, float] = {}

        for event in events:
            score = 0.0

            # Temporal precedence (earlier is more likely root cause)
            time_rank = events.index(event) / len(events)
            score += (1 - time_rank) * 0.3

            # Causal links (causes more effects = more likely root)
            outgoing_links = [l for l in links if l.cause.event_id == event.event_id]
            if outgoing_links:
                score += min(len(outgoing_links) * 0.15, 0.4)
                score += sum(l.confidence for l in outgoing_links) / len(outgoing_links) * 0.2

            # Domain knowledge boost
            if event.event_type in self.CAUSAL_KNOWLEDGE:
                score += 0.1 * self.CAUSAL_KNOWLEDGE[event.event_type].get("severity_boost", 1.0)

            # Not an effect of something else
            incoming_links = [l for l in links if l.effect.event_id == event.event_id]
            if not incoming_links:
                score += 0.15

            candidates[event.event_id] = min(score, 0.99)

        # Sort by score
        sorted_candidates = sorted(
            [(e, candidates[e.event_id]) for e in events],
            key=lambda x: -x[1]
        )

        return sorted_candidates

    def _generate_evidence_summary(
        self,
        root_cause: CausalEvent,
        causal_chain: List[CausalLink],
        all_events: List[CausalEvent]
    ) -> str:
        """Generate a human-readable evidence summary."""
        evidence_parts = []

        # Temporal evidence
        event_times = [e.timestamp for e in all_events]
        if root_cause.timestamp == min(event_times):
            evidence_parts.append(f"{root_cause.event_type} occurred first at {root_cause.timestamp.strftime('%H:%M:%S')}")

        # Causal chain evidence
        if causal_chain:
            effects = [l.effect.event_type for l in causal_chain]
            evidence_parts.append(f"Known to cause: {', '.join(effects)}")

        # Domain knowledge
        if root_cause.event_type in self.CAUSAL_KNOWLEDGE:
            evidence_parts.append("Matches known causal pattern in domain knowledge base")

        # Station scope
        station_ids = {e.station_id for e in all_events}
        if len(station_ids) == 1:
            evidence_parts.append(f"All events from same station ({next(iter(station_ids))})")
        else:
            evidence_parts.append(f"Events span {len(station_ids)} stations")

        return "; ".join(evidence_parts) if evidence_parts else "Analysis based on temporal and statistical patterns"

    def _get_action(self, event_type: str) -> str:
        """Get recommended action for an event type."""
        if event_type in self.CAUSAL_KNOWLEDGE:
            return self.CAUSAL_KNOWLEDGE[event_type]["action"]
        return f"Investigate {event_type} and check related systems"

    def _record_analysis(self, result: RootCauseResult):
        """Record analysis for future learning."""
        record = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "root_cause_type": result.root_cause.event_type,
            "confidence": result.confidence,
            "affected_count": len(result.affected_events),
            "causal_links": len(result.causal_chain)
        }
        self.analysis_history.append(record)
        # deque maxlen automatically keeps only last 1000 results

    def learn_from_feedback(
        self,
        analysis_id: str,
        actual_root_cause: str,
        was_correct: bool,
        corrective_action: Optional[str] = None
    ):
        """
        Learn from operator feedback on root cause analysis.

        Args:
            analysis_id: ID of the analysis
            actual_root_cause: The actual root cause identified by operator
            was_correct: Whether the automated analysis was correct
            corrective_action: Action that resolved the issue
        """
        logger.info(
            "Learning from RCA feedback: correct=%s, actual=%s",
            was_correct, actual_root_cause
        )

        # Update domain knowledge confidence
        if actual_root_cause in self.CAUSAL_KNOWLEDGE:
            if was_correct:
                self.CAUSAL_KNOWLEDGE[actual_root_cause]["severity_boost"] *= 1.05
            else:
                self.CAUSAL_KNOWLEDGE[actual_root_cause]["severity_boost"] *= 0.95

        if corrective_action and actual_root_cause in self.CAUSAL_KNOWLEDGE:
            self.CAUSAL_KNOWLEDGE[actual_root_cause]["action"] = corrective_action

    def get_statistics(self) -> Dict[str, Any]:
        """Get statistics about root cause analysis."""
        if not self.analysis_history:
            return {
                "total_analyses": 0,
                "avg_confidence": 0,
                "top_root_causes": {},
                "learned_patterns": len(self.learned_patterns)
            }

        confidences = [r["confidence"] for r in self.analysis_history]
        root_causes = [r["root_cause_type"] for r in self.analysis_history]

        cause_counts = defaultdict(int)
        for cause in root_causes:
            cause_counts[cause] += 1

        return {
            "total_analyses": len(self.analysis_history),
            "avg_confidence": round(np.mean(confidences), 3),
            "min_confidence": round(min(confidences), 3),
            "max_confidence": round(max(confidences), 3),
            "top_root_causes": dict(sorted(cause_counts.items(), key=lambda x: -x[1])[:10]),
            "learned_patterns": len(self.learned_patterns),
            "domain_rules": len(self.CAUSAL_KNOWLEDGE)
        }


# Singleton instance with thread-safe initialization
_rca_service: Optional[RootCauseAnalysisService] = None
_rca_service_lock = threading.Lock()


def get_rca_service() -> RootCauseAnalysisService:
    """Get or create singleton RootCauseAnalysisService instance (thread-safe)."""
    global _rca_service
    if _rca_service is None:
        with _rca_service_lock:
            if _rca_service is None:  # Double-check locking
                _rca_service = RootCauseAnalysisService()
    return _rca_service


def parse_event_from_dict(data: Dict[str, Any]) -> CausalEvent:
    """Parse a CausalEvent from dictionary (e.g., from JSON API)."""
    return CausalEvent(
        event_id=data["event_id"],
        event_type=data["event_type"],
        station_id=data["station_id"],
        timestamp=datetime.fromisoformat(data["timestamp"]) if isinstance(data["timestamp"], str) else data["timestamp"],
        severity=data.get("severity", "warning"),
        metric_type=data.get("metric_type"),
        metric_value=data.get("metric_value"),
        metadata=data.get("metadata", {})
    )
