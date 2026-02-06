package com.huawei.basestation.tenant;

import com.huawei.common.security.Roles;

/**
 * Thread-local holder for the current tenant context.
 *
 * This context is set by the TenantFilter and used throughout the request
 * lifecycle to filter data by organization.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    /**
     * Sets the current tenant for this thread.
     *
     * @param tenantInfo the tenant information
     */
    public static void setCurrentTenant(TenantInfo tenantInfo) {
        CURRENT_TENANT.set(tenantInfo);
    }

    /**
     * Gets the current tenant for this thread.
     *
     * @return the current tenant info, or null if not set
     */
    public static TenantInfo getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Gets the current organization ID.
     *
     * @return the organization ID, or null if not set
     */
    public static Long getCurrentOrganizationId() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.organizationId() : null;
    }

    /**
     * Gets the current organization slug.
     *
     * @return the organization slug, or null if not set
     */
    public static String getCurrentOrganizationSlug() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.organizationSlug() : null;
    }

    /**
     * Checks if a tenant context is currently set.
     *
     * @return true if tenant context is available
     */
    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * Clears the current tenant context.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Tenant information record.
     *
     * @param organizationId   the organization ID
     * @param organizationSlug the organization slug
     * @param username         the current user's username
     * @param role             the current user's role
     */
    public record TenantInfo(
            Long organizationId,
            String organizationSlug,
            String username,
            String role
    ) {
        /**
         * Checks if this is a system/admin context (no specific tenant).
         *
         * @return true if this is a system context
         */
        public boolean isSystemContext() {
            return organizationId == null && Roles.isAdmin(role);
        }
    }
}
