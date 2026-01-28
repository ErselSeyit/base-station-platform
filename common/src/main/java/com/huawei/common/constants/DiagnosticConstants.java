package com.huawei.common.constants;

/**
 * Constants for AI diagnostic confidence levels and automation thresholds.
 *
 * These thresholds determine when automatic actions can be taken without
 * operator confirmation based on AI confidence levels.
 *
 * @see Best_practices.md section "CONFIDENCE-BASED AUTOMATION THRESHOLDS"
 */
public final class DiagnosticConstants {

    private DiagnosticConstants() {
        // Prevent instantiation
    }

    // ========================================
    // CONFIDENCE THRESHOLDS
    // ========================================

    /**
     * Confidence level at or above which solutions are automatically applied
     * without requiring operator confirmation.
     * 95%+ confidence indicates very high certainty in the diagnosis.
     */
    public static final double CONFIDENCE_AUTO_APPLY = 0.95;

    /**
     * Confidence level for suggesting solution with operator confirmation.
     * 85-94% confidence - suggest the solution but require explicit approval.
     */
    public static final double CONFIDENCE_SUGGEST_APPLY = 0.85;

    /**
     * Confidence level requiring manual review before any action.
     * 70-84% confidence - present diagnosis but require detailed review.
     */
    public static final double CONFIDENCE_MANUAL_REVIEW = 0.70;

    /**
     * Threshold below which confidence is considered low.
     * Below 70% - low confidence, needs investigation.
     */
    public static final double CONFIDENCE_LOW = 0.70;

    // ========================================
    // RISK LEVEL ADJUSTMENTS
    // ========================================

    /**
     * Risk level: LOW - Actions that have minimal impact and are easily reversible.
     * Examples: Restarting a service, clearing cache, adjusting non-critical settings.
     * Can auto-apply at 90%+ confidence.
     */
    public static final String RISK_LEVEL_LOW = "LOW";

    /**
     * Risk level: MEDIUM - Actions that may affect operations but are recoverable.
     * Examples: Restarting the device, changing network configuration.
     * Requires 95%+ confidence for auto-apply.
     */
    public static final String RISK_LEVEL_MEDIUM = "MEDIUM";

    /**
     * Risk level: HIGH - Actions that could cause significant impact.
     * Examples: Factory reset, firmware update, hardware reconfiguration.
     * Always requires operator confirmation regardless of confidence.
     */
    public static final String RISK_LEVEL_HIGH = "HIGH";

    /**
     * Minimum confidence for auto-apply on LOW risk actions.
     */
    public static final double CONFIDENCE_AUTO_APPLY_LOW_RISK = 0.90;

    /**
     * Minimum confidence for auto-apply on MEDIUM risk actions.
     */
    public static final double CONFIDENCE_AUTO_APPLY_MEDIUM_RISK = 0.95;

    // ========================================
    // SESSION TIMING
    // ========================================

    /**
     * Default timeout for pending confirmation (in hours).
     * Sessions awaiting confirmation will expire after this period.
     */
    public static final int PENDING_CONFIRMATION_TIMEOUT_HOURS = 24;

    /**
     * Minimum number of successful feedback instances before adjusting confidence.
     */
    public static final int MIN_FEEDBACK_FOR_CONFIDENCE_ADJUSTMENT = 5;

    // ========================================
    // LEARNING THRESHOLDS
    // ========================================

    /**
     * Success rate threshold above which a pattern is considered reliable.
     */
    public static final double PATTERN_HIGH_SUCCESS_RATE = 0.80;

    /**
     * Success rate threshold below which a pattern needs attention.
     */
    public static final double PATTERN_LOW_SUCCESS_RATE = 0.50;

    /**
     * Maximum confidence boost from learning (prevents overconfidence).
     */
    public static final double MAX_CONFIDENCE_BOOST = 0.10;

    /**
     * Maximum confidence penalty from learning (limits negative adjustment).
     */
    public static final double MAX_CONFIDENCE_PENALTY = 0.20;
}
