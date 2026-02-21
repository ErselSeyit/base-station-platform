package com.huawei.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;

/**
 * Custom permission evaluator for fine-grained resource-level access control.
 *
 * <p>This evaluator works with Spring Security's {@code @PreAuthorize} annotation
 * to provide resource-level permission checks beyond simple role-based access.
 *
 * <h2>Usage Examples:</h2>
 * <pre>
 * // Check permission by resource type and permission name
 * &#64;PreAuthorize("hasPermission(#stationId, 'BaseStation', 'STATION_UPDATE')")
 * public void updateStation(Long stationId, ...) { }
 *
 * // Check permission with target object
 * &#64;PreAuthorize("hasPermission(#station, 'STATION_DELETE')")
 * public void deleteStation(BaseStation station) { }
 *
 * // Check using permission key format
 * &#64;PreAuthorize("hasPermission(null, 'station:create')")
 * public void createStation(...) { }
 * </pre>
 *
 * <h2>Configuration:</h2>
 * <pre>
 * &#64;Configuration
 * &#64;EnableMethodSecurity
 * public class SecurityConfig {
 *     &#64;Bean
 *     public PermissionEvaluator permissionEvaluator() {
 *         return new ResourcePermissionEvaluator();
 *     }
 * }
 * </pre>
 */
public class ResourcePermissionEvaluator implements PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ResourcePermissionEvaluator.class);

    /**
     * Evaluates if the user has permission on the target object.
     *
     * @param authentication the current authentication
     * @param targetDomainObject the domain object being accessed (can be null)
     * @param permission the permission required (String or Permission enum)
     * @return true if permission is granted
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || permission == null) {
            log.debug("Permission denied: authentication or permission is null");
            return false;
        }

        Permission perm = resolvePermission(permission);
        if (perm == null) {
            log.warn("Unknown permission: {}", permission);
            return false;
        }

        // Check if user's role has the permission
        boolean granted = hasPermissionForAuthentication(authentication, perm);

        if (log.isDebugEnabled()) {
            log.debug("Permission check: user={}, permission={}, target={}, granted={}",
                    authentication.getName(), perm, targetDomainObject, granted);
        }

        return granted;
    }

    /**
     * Evaluates if the user has permission on a target identified by ID and type.
     *
     * @param authentication the current authentication
     * @param targetId the ID of the target object
     * @param targetType the type of the target object (e.g., "BaseStation")
     * @param permission the permission required
     * @return true if permission is granted
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                  String targetType, Object permission) {
        if (authentication == null || permission == null) {
            log.debug("Permission denied: authentication or permission is null");
            return false;
        }

        Permission perm = resolvePermission(permission);
        if (perm == null) {
            log.warn("Unknown permission: {}", permission);
            return false;
        }

        // Check if user's role has the permission
        boolean granted = hasPermissionForAuthentication(authentication, perm);

        if (log.isDebugEnabled()) {
            log.debug("Permission check: user={}, permission={}, targetType={}, targetId={}, granted={}",
                    authentication.getName(), perm, targetType, targetId, granted);
        }

        return granted;
    }

    /**
     * Check if authentication has the specified permission based on roles.
     */
    private boolean hasPermissionForAuthentication(Authentication authentication, Permission permission) {
        // Check each granted authority (role)
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (RolePermissions.hasPermission(role, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve a permission object to a Permission enum.
     * Supports:
     * - Permission enum directly
     * - String enum name (e.g., "STATION_UPDATE")
     * - String key format (e.g., "station:update")
     */
    private Permission resolvePermission(Object permission) {
        if (permission instanceof Permission) {
            return (Permission) permission;
        }

        if (permission instanceof String permStr) {
            // Try as enum name first
            try {
                return Permission.valueOf(permStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Try as key format (resource:action)
                return Permission.findByKey(permStr.toLowerCase());
            }
        }

        return null;
    }
}
