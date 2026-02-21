"""
Confidence thresholds and automation constants.

These thresholds are synchronized with Java backend
(common/constants/DiagnosticConstants.java) to ensure consistent
automation decisions across the platform.

See shared-thresholds.json for full configuration.
"""

from enum import Enum
from typing import Tuple


# ========================================
# CONFIDENCE THRESHOLDS
# ========================================

# Confidence at or above which solutions are automatically applied
# without requiring operator confirmation. 95%+ indicates very high certainty.
CONFIDENCE_AUTO_APPLY = 0.95

# Confidence for suggesting solution with operator confirmation.
# 85-94% - suggest the solution but require explicit approval.
CONFIDENCE_SUGGEST_APPLY = 0.85

# Confidence requiring manual review before any action.
# 70-84% - present diagnosis but require detailed review.
CONFIDENCE_MANUAL_REVIEW = 0.70

# Threshold below which confidence is considered low.
# Below 70% - low confidence, needs investigation.
CONFIDENCE_LOW = 0.70

# ========================================
# RISK-ADJUSTED THRESHOLDS
# ========================================

# Minimum confidence for auto-apply on LOW risk actions
CONFIDENCE_AUTO_APPLY_LOW_RISK = 0.90

# Minimum confidence for auto-apply on MEDIUM risk actions
CONFIDENCE_AUTO_APPLY_MEDIUM_RISK = 0.95

# HIGH risk actions always require confirmation regardless of confidence


class RiskLevel(Enum):
    """Risk level for diagnostic actions."""
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


# ========================================
# LEARNING THRESHOLDS
# ========================================

# Success rate threshold above which a pattern is considered reliable
PATTERN_HIGH_SUCCESS_RATE = 0.80

# Success rate threshold below which a pattern needs attention
PATTERN_LOW_SUCCESS_RATE = 0.50

# Maximum confidence boost from learning (prevents overconfidence)
MAX_CONFIDENCE_BOOST = 0.10

# Maximum confidence penalty from learning (limits negative adjustment)
MAX_CONFIDENCE_PENALTY = 0.20

# Minimum number of feedback instances before adjusting confidence
MIN_FEEDBACK_FOR_CONFIDENCE_ADJUSTMENT = 5

# ========================================
# SESSION TIMING
# ========================================

# Default timeout for pending confirmation (in hours)
PENDING_CONFIRMATION_TIMEOUT_HOURS = 24


# ========================================
# UTILITY FUNCTIONS
# ========================================

def get_automation_action(confidence: float, risk_level: RiskLevel) -> Tuple[str, bool]:
    """
    Determine automation action based on confidence and risk level.

    Args:
        confidence: Confidence score from 0.0 to 1.0
        risk_level: Risk level of the proposed action

    Returns:
        Tuple of (action_type, requires_confirmation)
        action_type: "auto_apply", "suggest", "manual_review", "low_confidence"
    """
    # HIGH risk always requires confirmation
    if risk_level == RiskLevel.HIGH:
        if confidence >= CONFIDENCE_SUGGEST_APPLY:
            return "suggest", True
        elif confidence >= CONFIDENCE_MANUAL_REVIEW:
            return "manual_review", True
        return "low_confidence", True

    # MEDIUM risk
    if risk_level == RiskLevel.MEDIUM:
        if confidence >= CONFIDENCE_AUTO_APPLY_MEDIUM_RISK:
            return "auto_apply", False
        elif confidence >= CONFIDENCE_SUGGEST_APPLY:
            return "suggest", True
        elif confidence >= CONFIDENCE_MANUAL_REVIEW:
            return "manual_review", True
        return "low_confidence", True

    # LOW risk
    if confidence >= CONFIDENCE_AUTO_APPLY_LOW_RISK:
        return "auto_apply", False
    elif confidence >= CONFIDENCE_SUGGEST_APPLY:
        return "suggest", True
    elif confidence >= CONFIDENCE_MANUAL_REVIEW:
        return "manual_review", True
    return "low_confidence", True


def adjust_confidence_from_feedback(
    base_confidence: float,
    success_count: int,
    failure_count: int
) -> float:
    """
    Adjust confidence based on historical feedback.

    Args:
        base_confidence: Original confidence score
        success_count: Number of successful applications
        failure_count: Number of failed applications

    Returns:
        Adjusted confidence score
    """
    total = success_count + failure_count

    if total < MIN_FEEDBACK_FOR_CONFIDENCE_ADJUSTMENT:
        return base_confidence

    success_rate = success_count / total

    if success_rate >= PATTERN_HIGH_SUCCESS_RATE:
        # Boost confidence
        boost = (success_rate - PATTERN_HIGH_SUCCESS_RATE) * 0.5
        adjustment = min(boost, MAX_CONFIDENCE_BOOST)
        return min(1.0, base_confidence + adjustment)
    elif success_rate <= PATTERN_LOW_SUCCESS_RATE:
        # Penalize confidence
        penalty = (PATTERN_LOW_SUCCESS_RATE - success_rate) * 0.5
        adjustment = min(penalty, MAX_CONFIDENCE_PENALTY)
        return max(0.0, base_confidence - adjustment)

    return base_confidence
