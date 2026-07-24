package io.github.erselseyit.basestation.common.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps roles to their granted permissions.
 *
 * <p>This class defines which permissions each role has, enabling fine-grained
 * access control beyond simple role checks.
 *
 * <h2>Role Hierarchy:</h2>
 * <pre>
 * ADMIN    - Full access to all permissions
 * OPERATOR - Can manage stations, alerts, diagnostics, and reports
 * USER     - Read access to most resources, can acknowledge alerts
 * VIEWER   - Read-only access
 * SERVICE  - Machine-to-machine permissions for edge devices
 * </pre>
 */
public final class RolePermissions {

    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS;

    static {
        Map<String, Set<Permission>> permissions = new HashMap<>();

        // ADMIN - Full access
        permissions.put(Roles.ADMIN, EnumSet.allOf(Permission.class));

        // OPERATOR - Can manage stations, alerts, diagnostics
        permissions.put(Roles.OPERATOR, EnumSet.of(
                // Station permissions
                Permission.STATION_READ,
                Permission.STATION_CREATE,
                Permission.STATION_UPDATE,
                Permission.STATION_EXECUTE,
                // Metrics permissions
                Permission.METRICS_READ,
                Permission.METRICS_EXPORT,
                Permission.METRICS_CONFIGURE,
                // Alert permissions
                Permission.ALERT_READ,
                Permission.ALERT_ACKNOWLEDGE,
                Permission.ALERT_RESOLVE,
                Permission.ALERT_CREATE,
                // Diagnostic permissions
                Permission.DIAGNOSTIC_READ,
                Permission.DIAGNOSTIC_EXECUTE,
                Permission.DIAGNOSTIC_APPROVE,
                Permission.DIAGNOSTIC_FEEDBACK,
                // Report permissions
                Permission.REPORT_READ,
                Permission.REPORT_GENERATE,
                Permission.REPORT_EXPORT
        ));

        // USER - Can read and acknowledge, limited write access
        permissions.put(Roles.USER, EnumSet.of(
                // Station permissions
                Permission.STATION_READ,
                // Metrics permissions
                Permission.METRICS_READ,
                // Alert permissions
                Permission.ALERT_READ,
                Permission.ALERT_ACKNOWLEDGE,
                // Diagnostic permissions
                Permission.DIAGNOSTIC_READ,
                Permission.DIAGNOSTIC_FEEDBACK,
                // Report permissions
                Permission.REPORT_READ
        ));

        // VIEWER - Read-only access
        permissions.put(Roles.VIEWER, EnumSet.of(
                Permission.STATION_READ,
                Permission.METRICS_READ,
                Permission.ALERT_READ,
                Permission.DIAGNOSTIC_READ,
                Permission.REPORT_READ
        ));

        // SERVICE - Edge device and service-to-service permissions
        permissions.put(Roles.SERVICE, EnumSet.of(
                // Station permissions - can report metrics and status
                Permission.STATION_READ,
                Permission.STATION_UPDATE,
                Permission.STATION_EXECUTE,
                // Metrics permissions
                Permission.METRICS_READ,
                // Alert permissions
                Permission.ALERT_READ,
                Permission.ALERT_CREATE,
                // Diagnostic permissions
                Permission.DIAGNOSTIC_READ,
                Permission.DIAGNOSTIC_EXECUTE
        ));

        ROLE_PERMISSIONS = Collections.unmodifiableMap(permissions);
    }

    private RolePermissions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Check if a role has a specific permission.
     *
     * @param role the role name (case-insensitive)
     * @param permission the permission to check
     * @return true if the role has the permission
     */
    public static boolean hasPermission(String role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }

        // Normalize role name (remove ROLE_ prefix if present)
        String normalizedRole = role.toUpperCase();
        if (normalizedRole.startsWith(Roles.ROLE_PREFIX)) {
            normalizedRole = normalizedRole.substring(Roles.ROLE_PREFIX.length());
        }

        Set<Permission> permissions = ROLE_PERMISSIONS.get(normalizedRole);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Get all permissions for a role.
     *
     * @param role the role name (case-insensitive)
     * @return immutable set of permissions, or empty set if role not found
     */
    public static Set<Permission> getPermissions(String role) {
        if (role == null) {
            return Collections.emptySet();
        }

        // Normalize role name
        String normalizedRole = role.toUpperCase();
        if (normalizedRole.startsWith(Roles.ROLE_PREFIX)) {
            normalizedRole = normalizedRole.substring(Roles.ROLE_PREFIX.length());
        }

        Set<Permission> permissions = ROLE_PERMISSIONS.get(normalizedRole);
        return permissions != null
                ? Collections.unmodifiableSet(permissions)
                : Collections.emptySet();
    }

    /**
     * Check if a role has any of the specified permissions.
     *
     * @param role the role name
     * @param permissions the permissions to check
     * @return true if the role has at least one of the permissions
     */
    public static boolean hasAnyPermission(String role, Permission... permissions) {
        for (Permission p : permissions) {
            if (hasPermission(role, p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a role has all of the specified permissions.
     *
     * @param role the role name
     * @param permissions the permissions to check
     * @return true if the role has all of the permissions
     */
    public static boolean hasAllPermissions(String role, Permission... permissions) {
        for (Permission p : permissions) {
            if (!hasPermission(role, p)) {
                return false;
            }
        }
        return true;
    }
}
