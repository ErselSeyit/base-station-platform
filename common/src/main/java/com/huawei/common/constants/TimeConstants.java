package com.huawei.common.constants;

/**
 * Time-related constants for consistent time calculations across services.
 *
 * <p>Provides named constants for common time conversions to avoid magic numbers
 * and improve code readability.
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.constants.TimeConstants.*;
 *
 * long expiresInSeconds = expirationMs / MILLIS_PER_SECOND;
 * Instant cutoff = Instant.now().minusSeconds(SECONDS_PER_DAY);
 * </pre>
 */
public final class TimeConstants {

    // ========================================
    // MILLISECOND CONVERSIONS
    // ========================================

    /**
     * Milliseconds in one second.
     */
    public static final long MILLIS_PER_SECOND = 1000L;

    /**
     * Milliseconds in one minute.
     */
    public static final long MILLIS_PER_MINUTE = 60_000L;

    /**
     * Milliseconds in one hour.
     */
    public static final long MILLIS_PER_HOUR = 3_600_000L;

    /**
     * Milliseconds in one day (24 hours).
     */
    public static final long MILLIS_PER_DAY = 86_400_000L;

    // ========================================
    // SECOND CONVERSIONS
    // ========================================

    /**
     * Seconds in one minute.
     */
    public static final long SECONDS_PER_MINUTE = 60L;

    /**
     * Seconds in one hour.
     */
    public static final long SECONDS_PER_HOUR = 3600L;

    /**
     * Seconds in one day (24 hours).
     */
    public static final long SECONDS_PER_DAY = 86400L;

    /**
     * Seconds in one week (7 days).
     */
    public static final long SECONDS_PER_WEEK = 604800L;

    /**
     * Seconds in one year (365 days, approximate).
     */
    public static final long SECONDS_PER_YEAR = 31536000L;

    // Prevent instantiation
    private TimeConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
