package com.huawei.common.constants;

/**
 * Service name constants used for identification across the platform.
 *
 * <p>Centralizes service identifiers to ensure consistency in:
 * <ul>
 *   <li>Health check responses</li>
 *   <li>Audit logging and tracing</li>
 *   <li>Circuit breaker names</li>
 *   <li>Creator/source attribution in entities</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.constants.ServiceNames.*;
 *
 * health.withDetail(KEY_SERVICE, AI_DIAGNOSTIC_SERVICE);
 * command.setCreatedBy(AI_DIAGNOSTIC_SERVICE);
 * </pre>
 */
public final class ServiceNames {

    // ========================================
    // CORE SERVICES
    // ========================================

    /**
     * AI Diagnostic service identifier.
     * Used for health checks and entity attribution.
     */
    public static final String AI_DIAGNOSTIC_SERVICE = "ai-diagnostic";

    /**
     * Monitoring service identifier (for circuit breaker naming).
     */
    public static final String MONITORING_SERVICE = "monitoringService";

    /**
     * Base Station service identifier.
     */
    public static final String BASE_STATION_SERVICE = "base-station";

    /**
     * Notification service identifier.
     */
    public static final String NOTIFICATION_SERVICE = "notification";

    /**
     * Auth service identifier.
     */
    public static final String AUTH_SERVICE = "auth";

    // ========================================
    // SYSTEM ACTORS
    // ========================================

    /**
     * System actor identifier for audit logging.
     * Used when actions are performed by automated processes rather than users.
     */
    public static final String SYSTEM_ACTOR = "SYSTEM";

    // Prevent instantiation
    private ServiceNames() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
