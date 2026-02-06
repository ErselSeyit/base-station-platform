package com.huawei.common.constants;

/**
 * Public endpoint path constants used across services.
 *
 * <p>Centralizes public endpoint definitions to ensure consistent
 * security configuration across all services.
 *
 * <h2>Usage in SecurityConfig:</h2>
 * <pre>
 * import static com.huawei.common.constants.PublicEndpoints.*;
 *
 * http.authorizeHttpRequests(auth -> auth
 *     .requestMatchers(ACTUATOR_HEALTH, ACTUATOR_HEALTH_WILDCARD).permitAll()
 *     .requestMatchers(SWAGGER_UI_WILDCARD, API_DOCS_WILDCARD).permitAll()
 * );
 * </pre>
 */
public final class PublicEndpoints {

    // ========================================
    // ACTUATOR ENDPOINTS
    // ========================================

    /**
     * Health check endpoint path.
     */
    public static final String ACTUATOR_HEALTH = "/actuator/health";

    /**
     * Health check endpoint wildcard for nested paths.
     */
    public static final String ACTUATOR_HEALTH_WILDCARD = "/actuator/health/**";

    // ========================================
    // API DOCUMENTATION ENDPOINTS
    // ========================================

    /**
     * Swagger UI endpoint wildcard.
     */
    public static final String SWAGGER_UI_WILDCARD = "/swagger-ui/**";

    /**
     * Swagger UI HTML page.
     */
    public static final String SWAGGER_UI_HTML = "/swagger-ui.html";

    /**
     * OpenAPI 3 documentation endpoint wildcard.
     */
    public static final String API_DOCS_WILDCARD = "/v3/api-docs/**";

    // Prevent instantiation
    private PublicEndpoints() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
