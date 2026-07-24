"""
Learning engine for the AI diagnostic service.

Tracks patterns learned from operator feedback and adjusts confidence scores
based on historical success rates. Pure in-memory logic (thread-safe), extracted
from diagnostic_service.py so it can be tested and reused in isolation.
"""

import threading
from typing import Any, Dict, List, Optional

from service.models import LearnedPattern

# Confidence blend once enough feedback exists: 30% of the base prior plus 70%
# of the observed success rate. Kept as named constants so the weighting is
# explicit rather than a magic number.
_BASE_CONFIDENCE_PRIOR = 0.85
_BASE_WEIGHT = 0.3
_SUCCESS_RATE_WEIGHT = 0.7
# Minimum feedback cases before the learned confidence overrides the caller's.
_MIN_CASES_FOR_LEARNED_CONFIDENCE = 3


class LearningEngine:
    """
    Manages learned patterns from operator feedback.
    Adjusts confidence scores based on historical success rates.
    """

    def __init__(self):
        self.patterns: Dict[str, LearnedPattern] = {}
        self._lock = threading.Lock()

    def get_pattern(self, problem_code: str) -> Optional[LearnedPattern]:
        """Get learned pattern for a problem code."""
        with self._lock:
            return self.patterns.get(problem_code)

    def update_pattern(self, problem_code: str, category: str,
                       was_effective: bool, action: str) -> LearnedPattern:
        """Update pattern based on feedback."""
        with self._lock:
            if problem_code not in self.patterns:
                self.patterns[problem_code] = LearnedPattern(
                    problem_code=problem_code,
                    category=category
                )

            pattern = self.patterns[problem_code]

            if was_effective:
                pattern.resolved_count += 1
                if action and action not in pattern.successful_actions:
                    pattern.successful_actions.append(action)
            else:
                pattern.failed_count += 1
                if action and action not in pattern.failed_actions:
                    pattern.failed_actions.append(action)

            # Recalculate confidence based on success rate
            total = pattern.resolved_count + pattern.failed_count
            if total > 0:
                success_rate = pattern.resolved_count / total
                pattern.adjusted_confidence = (
                    _BASE_WEIGHT * _BASE_CONFIDENCE_PRIOR
                    + _SUCCESS_RATE_WEIGHT * success_rate
                )

            return pattern

    def get_adjusted_confidence(self, problem_code: str,
                                 base_confidence: float) -> float:
        """Get confidence adjusted by learned patterns."""
        pattern = self.get_pattern(problem_code)
        if pattern and (pattern.resolved_count + pattern.failed_count) >= _MIN_CASES_FOR_LEARNED_CONFIDENCE:
            # Only use learned confidence if we have enough data
            return pattern.adjusted_confidence
        return base_confidence

    def get_all_patterns(self) -> List[LearnedPattern]:
        """Get all learned patterns."""
        with self._lock:
            return list(self.patterns.values())

    def get_stats(self) -> Dict[str, Any]:
        """Get learning statistics."""
        with self._lock:
            total_resolved = sum(p.resolved_count for p in self.patterns.values())
            total_failed = sum(p.failed_count for p in self.patterns.values())
            total = total_resolved + total_failed

            return {
                "total_patterns": len(self.patterns),
                "total_feedback": total,
                "total_resolved": total_resolved,
                "total_failed": total_failed,
                "overall_success_rate": (total_resolved / total * 100) if total > 0 else 0.0,
                "top_patterns": [
                    {
                        "problem_code": p.problem_code,
                        "success_rate": p.success_rate(),
                        "total_cases": p.resolved_count + p.failed_count,
                        "adjusted_confidence": p.adjusted_confidence
                    }
                    for p in sorted(
                        self.patterns.values(),
                        key=lambda x: x.resolved_count + x.failed_count,
                        reverse=True
                    )[:5]
                ]
            }
