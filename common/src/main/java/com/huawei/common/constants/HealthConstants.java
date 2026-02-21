package com.huawei.common.constants;

/**
 * Health status thresholds and calculation constants.
 *
 * These thresholds are synchronized with Python AI diagnostic service
 * (ai-diagnostic/service/utils/health.py) to ensure consistent health
 * status determination across the platform.
 *
 * Health Factor Scale:
 * - 1.0 = Fully healthy
 * - 0.8 = Slightly degraded
 * - 0.5 = Warning level
 * - 0.2 = Critical level
 * - 0.0 = Failed
 *
 * @see shared-thresholds.json for full configuration
 */
public final class HealthConstants {

    private HealthConstants() {
        // Prevent instantiation
    }

    // ========================================
    // HEALTH STATUS THRESHOLDS
    // ========================================

    /**
     * Health score below which status is CRITICAL.
     * Matches Python: health.py CRITICAL_THRESHOLD
     */
    public static final double CRITICAL_THRESHOLD = 0.4;

    /**
     * Health score below which status is WARNING (if above CRITICAL).
     * Matches Python: health.py WARNING_THRESHOLD
     */
    public static final double WARNING_THRESHOLD = 0.6;

    /**
     * Health score below which status is DEGRADED (if above WARNING).
     * Matches Python: health.py DEGRADED_THRESHOLD
     */
    public static final double DEGRADED_THRESHOLD = 0.8;

    // ========================================
    // HEALTH FACTOR LEVELS
    // ========================================

    /**
     * Health factor for critical condition.
     */
    public static final double HEALTH_FACTOR_CRITICAL = 0.2;

    /**
     * Health factor for warning condition.
     */
    public static final double HEALTH_FACTOR_WARNING = 0.5;

    /**
     * Health factor for degraded condition.
     */
    public static final double HEALTH_FACTOR_DEGRADED = 0.8;

    /**
     * Health factor for healthy condition.
     */
    public static final double HEALTH_FACTOR_HEALTHY = 1.0;

    // ========================================
    // HEALTH STATUS ENUM VALUES
    // ========================================

    public static final String STATUS_HEALTHY = "healthy";
    public static final String STATUS_DEGRADED = "degraded";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_FAILED = "failed";

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Determine health status from combined health score.
     * Matches Python: health.py determine_health_status()
     *
     * @param combinedHealth Health score from 0.0 to 1.0
     * @return Health status string
     */
    public static String determineHealthStatus(double combinedHealth) {
        if (combinedHealth < CRITICAL_THRESHOLD) {
            return STATUS_CRITICAL;
        } else if (combinedHealth < WARNING_THRESHOLD) {
            return STATUS_WARNING;
        } else if (combinedHealth < DEGRADED_THRESHOLD) {
            return STATUS_DEGRADED;
        }
        return STATUS_HEALTHY;
    }

    /**
     * Calculate health factor for a metric value against thresholds.
     * Matches Python: health.py assess_metric_health()
     *
     * @param value Current metric value
     * @param criticalThreshold Threshold for critical status
     * @param warningThreshold Threshold for warning status
     * @param healthyThreshold Threshold for healthy status
     * @param higherIsWorse If true, higher values = worse health
     * @return Health factor from 0.0 to 1.0
     */
    public static double assessMetricHealth(
            double value,
            double criticalThreshold,
            double warningThreshold,
            double healthyThreshold,
            boolean higherIsWorse) {

        if (higherIsWorse) {
            // Higher is worse (e.g., temperature, CPU usage)
            if (value >= criticalThreshold) {
                return HEALTH_FACTOR_CRITICAL;
            } else if (value >= warningThreshold) {
                return HEALTH_FACTOR_WARNING;
            } else if (value >= healthyThreshold) {
                return HEALTH_FACTOR_DEGRADED;
            }
            return HEALTH_FACTOR_HEALTHY;
        } else {
            // Lower is worse (e.g., battery SOC, signal strength)
            if (value <= criticalThreshold) {
                return HEALTH_FACTOR_CRITICAL;
            } else if (value <= warningThreshold) {
                return HEALTH_FACTOR_WARNING;
            } else if (value <= healthyThreshold) {
                return HEALTH_FACTOR_DEGRADED;
            }
            return HEALTH_FACTOR_HEALTHY;
        }
    }

    /**
     * Calculate combined health from multiple health factors.
     * Matches Python: health.py calculate_combined_health()
     *
     * @param healthFactors Array of health factors (0.0-1.0)
     * @return Combined health score (average)
     */
    public static double calculateCombinedHealth(double... healthFactors) {
        if (healthFactors == null || healthFactors.length == 0) {
            return HEALTH_FACTOR_HEALTHY;
        }

        double sum = 0.0;
        for (double factor : healthFactors) {
            sum += factor;
        }
        return sum / healthFactors.length;
    }

    /**
     * Convert health score to failure probability.
     * Matches Python: health.py health_to_probability()
     *
     * @param combinedHealth Health score from 0.0 to 1.0
     * @return Failure probability from 0.0 to 1.0
     */
    public static double healthToProbability(double combinedHealth) {
        return 1.0 - combinedHealth;
    }
}
