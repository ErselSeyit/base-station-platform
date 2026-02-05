package com.huawei.common.constants;

/**
 * Standard JSON response key names used across services.
 *
 * <p>Centralizes commonly-used JSON response field names to ensure consistency
 * across all service responses and prevent hardcoded string duplication.
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.constants.JsonResponseKeys.*;
 *
 * return Map.of(
 *     KEY_STATUS, "success",
 *     KEY_MESSAGE, "Operation completed"
 * );
 * </pre>
 */
public final class JsonResponseKeys {

    // ========================================
    // STANDARD RESPONSE KEYS
    // ========================================

    /**
     * Key for status field in responses.
     * Example: {"status": "success"}
     */
    public static final String KEY_STATUS = "status";

    /**
     * Key for message field in responses.
     * Example: {"message": "Operation completed successfully"}
     */
    public static final String KEY_MESSAGE = "message";

    /**
     * Key for error field in error responses.
     * Example: {"error": "Invalid credentials"}
     */
    public static final String KEY_ERROR = "error";

    /**
     * Key for error ID field in error responses (for support reference).
     * Example: {"errorId": "abc12345"}
     */
    public static final String KEY_ERROR_ID = "errorId";

    // ========================================
    // ENTITY/DATA KEYS
    // ========================================

    /**
     * Key for station ID in station-related responses.
     * Example: {"stationId": 123}
     */
    public static final String KEY_STATION_ID = "stationId";

    /**
     * Key for service name in health/status responses.
     * Example: {"service": "ai-diagnostic"}
     */
    public static final String KEY_SERVICE = "service";

    /**
     * Key for data payload in responses.
     * Example: {"data": {...}}
     */
    public static final String KEY_DATA = "data";

    // ========================================
    // RESILIENCE/FALLBACK KEYS
    // ========================================

    /**
     * Key indicating response is from a fallback mechanism.
     * Example: {"fallback": true}
     */
    public static final String KEY_FALLBACK = "fallback";

    /**
     * Key for source of data (e.g., "cached", "live").
     * Example: {"source": "cached"}
     */
    public static final String KEY_SOURCE = "source";

    /**
     * Key for reason/explanation in audit logs and responses.
     * Example: {"reason": "Invalid credentials"}
     */
    public static final String KEY_REASON = "reason";

    // ========================================
    // COMMON STATUS VALUES
    // ========================================

    /**
     * Status value indicating service is unavailable.
     */
    public static final String STATUS_UNAVAILABLE = "unavailable";

    /**
     * Status value indicating success.
     */
    public static final String STATUS_SUCCESS = "success";

    /**
     * Status value indicating an error occurred.
     */
    public static final String STATUS_ERROR = "error";

    // Prevent instantiation
    private JsonResponseKeys() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
