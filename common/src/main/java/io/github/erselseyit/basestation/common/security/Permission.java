package io.github.erselseyit.basestation.common.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Fine-grained permissions for resource-level access control.
 *
 * <p>These permissions complement role-based access (RBAC) with more granular
 * resource-level controls. Use with custom PermissionEvaluator for
 * {@code @PreAuthorize("hasPermission(#id, 'BaseStation', 'STATION_UPDATE')")}.
 *
 * <h2>Permission Naming Convention:</h2>
 * <pre>
 * {RESOURCE}_{ACTION}
 * Examples: STATION_READ, STATION_UPDATE, ALERT_RESOLVE
 * </pre>
 *
 * <h2>Usage:</h2>
 * <pre>
 * // In controller:
 * &#64;PreAuthorize("hasPermission(#stationId, 'BaseStation', 'STATION_UPDATE')")
 * public ResponseEntity&lt;BaseStation&gt; update(@PathVariable Long stationId, ...) { }
 *
 * // Check programmatically:
 * if (Permission.STATION_UPDATE.isGrantedTo(userRole)) { ... }
 * </pre>
 */
public enum Permission {

    // ========================================================================
    // Station Permissions
    // ========================================================================

    /** Read station details and status */
    STATION_READ("station", "read"),

    /** Create new stations */
    STATION_CREATE("station", "create"),

    /** Update station configuration */
    STATION_UPDATE("station", "update"),

    /** Delete stations (dangerous operation) */
    STATION_DELETE("station", "delete"),

    /** Execute commands on stations */
    STATION_EXECUTE("station", "execute"),

    // ========================================================================
    // Metrics Permissions
    // ========================================================================

    /** View metrics and dashboards */
    METRICS_READ("metrics", "read"),

    /** Export metrics data */
    METRICS_EXPORT("metrics", "export"),

    /** Configure metric thresholds */
    METRICS_CONFIGURE("metrics", "configure"),

    // ========================================================================
    // Alert Permissions
    // ========================================================================

    /** View alerts and notifications */
    ALERT_READ("alert", "read"),

    /** Acknowledge alerts */
    ALERT_ACKNOWLEDGE("alert", "acknowledge"),

    /** Resolve/close alerts */
    ALERT_RESOLVE("alert", "resolve"),

    /** Create manual alerts */
    ALERT_CREATE("alert", "create"),

    // ========================================================================
    // Diagnostic Permissions
    // ========================================================================

    /** View diagnostic sessions and results */
    DIAGNOSTIC_READ("diagnostic", "read"),

    /** Execute AI diagnostics */
    DIAGNOSTIC_EXECUTE("diagnostic", "execute"),

    /** Approve/reject diagnostic recommendations */
    DIAGNOSTIC_APPROVE("diagnostic", "approve"),

    /** Provide feedback on diagnostics */
    DIAGNOSTIC_FEEDBACK("diagnostic", "feedback"),

    // ========================================================================
    // Report Permissions
    // ========================================================================

    /** View reports */
    REPORT_READ("report", "read"),

    /** Generate new reports */
    REPORT_GENERATE("report", "generate"),

    /** Export reports */
    REPORT_EXPORT("report", "export"),

    // ========================================================================
    // Admin Permissions
    // ========================================================================

    /** Manage users (create, update, delete) */
    USER_MANAGE("user", "manage"),

    /** View audit logs */
    AUDIT_READ("audit", "read"),

    /** Modify system configuration */
    SYSTEM_CONFIGURE("system", "configure"),

    /** Access actuator endpoints */
    ACTUATOR_ACCESS("actuator", "access");

    private final String resource;
    private final String action;

    Permission(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    /**
     * Returns the permission key in format "resource:action".
     */
    public String getKey() {
        return resource + ":" + action;
    }

    /**
     * Check if this permission is granted to a specific role.
     * Uses RolePermissions mapping.
     *
     * @param role the role to check
     * @return true if the role has this permission
     */
    public boolean isGrantedTo(String role) {
        return RolePermissions.hasPermission(role, this);
    }

    /**
     * Finds a permission by its resource and action, case-insensitively.
     *
     * @param resource the resource name; may be null
     * @param action the action name; may be null
     * @return the matching permission, or empty if there is none
     */
    public static Optional<Permission> findByResourceAndAction(String resource, String action) {
        if (resource == null || action == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.resource.equalsIgnoreCase(resource) && p.action.equalsIgnoreCase(action))
                .findFirst();
    }

    /**
     * Finds a permission by its key, e.g. {@code "station:update"}.
     *
     * @param key the permission key; may be null
     * @return the matching permission, or empty if the key is malformed or unknown
     */
    public static Optional<Permission> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String[] parts = key.split(":");
        if (parts.length != 2) {
            return Optional.empty();
        }
        return findByResourceAndAction(parts[0], parts[1]);
    }

    /**
     * Finds a permission by enum constant name, case-insensitively.
     *
     * <p>Preferred over {@link #valueOf(String)} when the name comes from
     * outside the codebase: an unrecognised name is an expected outcome, and
     * <em>Effective Java</em> item 69 warns against using exceptions for
     * ordinary control flow.
     *
     * @param name the constant name; may be null
     * @return the matching permission, or empty if there is none
     */
    public static Optional<Permission> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalised = name.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(p -> p.name().equals(normalised))
                .findFirst();
    }
}
