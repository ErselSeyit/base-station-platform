package com.huawei.tmf.constants;

/**
 * Constants for TMF Open API implementations.
 *
 * <p>Centralizes field names, state values, and event types used across
 * TMF service implementations (TMF638 Service Inventory, TMF639 Resource Inventory,
 * TMF642 Alarm Management).
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.tmf.constants.TMFConstants.*;
 *
 * query.addCriteria(Criteria.where(FIELD_STATE).is(STATE_ACTIVE));
 * </pre>
 */
public final class TMFConstants {

    // ========================================
    // COMMON FIELD NAMES (MongoDB/Query fields)
    // ========================================

    /**
     * State field name used in queries.
     */
    public static final String FIELD_STATE = "state";

    /**
     * Category field name used in queries.
     */
    public static final String FIELD_CATEGORY = "category";

    /**
     * Name field name used in queries.
     */
    public static final String FIELD_NAME = "name";

    /**
     * External ID field name used in queries.
     */
    public static final String FIELD_EXTERNAL_ID = "externalId";

    // ========================================
    // SERVICE STATES (TMF638 Service Inventory)
    // ========================================

    /**
     * Service state: designed (not yet deployed).
     */
    public static final String STATE_DESIGNED = "designed";

    /**
     * Service/Resource state: active and operational.
     */
    public static final String STATE_ACTIVE = "active";

    /**
     * Service state: inactive (temporarily suspended).
     */
    public static final String STATE_INACTIVE = "inactive";

    /**
     * Service state: terminated (end of life).
     */
    public static final String STATE_TERMINATED = "terminated";

    /**
     * Resource state: disabled.
     */
    public static final String STATE_DISABLED = "disabled";

    // ========================================
    // EVENT TYPES
    // ========================================

    /**
     * Event type for service state changes.
     */
    public static final String EVENT_SERVICE_STATE_CHANGE = "ServiceStateChangeEvent";

    /**
     * Event type for alarm state changes.
     */
    public static final String EVENT_ALARM_STATE_CHANGE = "AlarmStateChangeEvent";

    // Prevent instantiation
    private TMFConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
