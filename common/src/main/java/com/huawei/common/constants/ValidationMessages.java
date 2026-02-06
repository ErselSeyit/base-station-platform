package com.huawei.common.constants;

/**
 * Common validation messages used across services.
 *
 * <p>Centralizes null-check messages to ensure consistency in error handling
 * and reduce duplication across controllers.
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.constants.ValidationMessages.*;
 *
 * Objects.requireNonNull(response, RESPONSE_NULL_MESSAGE);
 * Objects.requireNonNull(stationId, STATION_ID_NULL_MESSAGE);
 * </pre>
 */
public final class ValidationMessages {

    // ========================================
    // NULL CHECK MESSAGES
    // ========================================

    /**
     * Message for null response validation.
     */
    public static final String RESPONSE_NULL_MESSAGE = "Response cannot be null";

    /**
     * Message for null station ID validation.
     */
    public static final String STATION_ID_NULL_MESSAGE = "Station ID cannot be null";

    /**
     * Message for null rule ID validation.
     */
    public static final String RULE_ID_NULL_MESSAGE = "Rule ID cannot be null";

    /**
     * Message for null start time validation.
     */
    public static final String START_TIME_NULL_MESSAGE = "Start time cannot be null";

    /**
     * Message for null DTO validation.
     */
    public static final String DTO_NULL_MESSAGE = "DTO cannot be null";

    // Prevent instantiation
    private ValidationMessages() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
