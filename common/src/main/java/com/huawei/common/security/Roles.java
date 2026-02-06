package com.huawei.common.security;

/**
 * Centralized role definitions for consistent RBAC across all services.
 *
 * <p>All services should use these constants instead of hardcoding role names.
 * This ensures consistent role naming and makes refactoring easier.
 *
 * <h2>Role Hierarchy:</h2>
 * <pre>
 * ADMIN > OPERATOR > USER
 *   │        │        │
 *   │        │        └── Read-only access to stations/metrics
 *   │        └── Can modify stations, acknowledge alerts, run diagnostics
 *   └── Full access including actuator, user management
 * </pre>
 *
 * <h2>Usage in SecurityConfig:</h2>
 * <pre>
 * import static com.huawei.common.security.Roles.*;
 *
 * http.authorizeHttpRequests(auth -> auth
 *     .requestMatchers(HttpMethod.GET, "/api/v1/stations/**").hasAnyRole(ADMIN, OPERATOR, USER)
 *     .requestMatchers("/api/v1/stations/**").hasAnyRole(ADMIN, OPERATOR)
 *     .requestMatchers("/actuator/**").hasRole(ADMIN)
 * );
 * </pre>
 *
 * <h2>Usage with @PreAuthorize:</h2>
 * <pre>
 * &#64;PreAuthorize(Roles.HAS_OPERATOR)
 * public void modifyStation() { ... }
 * </pre>
 */
public final class Roles {

    /**
     * Spring Security role prefix.
     */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * Default username for unauthenticated requests.
     */
    public static final String ANONYMOUS_USER = "anonymous";

    // Role names (without ROLE_ prefix - Spring Security adds it automatically)
    public static final String ADMIN = "ADMIN";
    public static final String OPERATOR = "OPERATOR";
    public static final String USER = "USER";
    public static final String SERVICE = "SERVICE";  // For edge-bridge and internal services
    public static final String VIEWER = "VIEWER";    // Read-only access role

    // Full role names (with ROLE_ prefix - for explicit checks)
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_SERVICE = "ROLE_SERVICE";
    public static final String ROLE_VIEWER = "ROLE_VIEWER";

    // SpEL expressions for @PreAuthorize annotations
    public static final String HAS_ADMIN = "hasRole('ADMIN')";
    public static final String HAS_OPERATOR = "hasAnyRole('ADMIN', 'OPERATOR')";
    public static final String HAS_USER = "hasAnyRole('ADMIN', 'OPERATOR', 'USER')";
    public static final String HAS_SERVICE = "hasAnyRole('ADMIN', 'SERVICE')";

    // Prevent instantiation
    private Roles() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Check if a role string represents an admin role (case-insensitive).
     */
    public static boolean isAdmin(String role) {
        return ROLE_ADMIN.equalsIgnoreCase(role) || ADMIN.equalsIgnoreCase(role);
    }

    /**
     * Check if a role string represents an operator or higher (case-insensitive).
     */
    public static boolean isOperatorOrHigher(String role) {
        return isAdmin(role) || ROLE_OPERATOR.equalsIgnoreCase(role) || OPERATOR.equalsIgnoreCase(role);
    }

    /**
     * Check if a role string represents any valid user role.
     */
    public static boolean isValidRole(String role) {
        return isOperatorOrHigher(role) || ROLE_USER.equals(role) || USER.equals(role)
                || ROLE_SERVICE.equals(role) || SERVICE.equals(role);
    }
}
