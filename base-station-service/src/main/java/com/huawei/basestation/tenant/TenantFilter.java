package com.huawei.basestation.tenant;

import static com.huawei.common.constants.HttpHeaders.*;
import static com.huawei.common.constants.PublicEndpoints.*;

import com.huawei.basestation.model.Organization;
import com.huawei.basestation.repository.OrganizationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that extracts tenant information from request and sets TenantContext.
 *
 * Tenant can be identified by:
 * 1. X-Organization-ID header
 * 2. X-Organization-Slug header
 * 3. Organization claim in JWT token (handled by gateway)
 *
 * Admin users can optionally specify a tenant to operate in,
 * or operate without tenant context to see all data.
 */
@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private final OrganizationRepository organizationRepository;

    public TenantFilter(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            TenantContext.TenantInfo tenantInfo = extractTenantInfo(request);

            if (tenantInfo != null) {
                TenantContext.setCurrentTenant(tenantInfo);
                log.debug("Tenant context set: org={}, user={}",
                        tenantInfo.organizationSlug(), tenantInfo.username());
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request
            TenantContext.clear();
        }
    }

    private TenantContext.TenantInfo extractTenantInfo(HttpServletRequest request) {
        String username = request.getHeader(HEADER_USER_NAME);
        String role = request.getHeader(HEADER_USER_ROLE);

        // Try to get organization by ID first
        String orgIdHeader = request.getHeader(HEADER_ORGANIZATION_ID);
        if (orgIdHeader != null && !orgIdHeader.isBlank()) {
            try {
                Long orgId = Long.parseLong(orgIdHeader);
                Optional<Organization> org = organizationRepository.findById(orgId);
                if (org.isPresent() && org.get().isActive()) {
                    return new TenantContext.TenantInfo(
                            org.get().getId(),
                            org.get().getSlug(),
                            username,
                            role
                    );
                }
                log.warn("Organization not found or inactive: id={}", orgId);
            } catch (NumberFormatException e) {
                log.warn("Invalid organization ID header: {}", orgIdHeader);
            }
        }

        // Try to get organization by slug
        String orgSlugHeader = request.getHeader(HEADER_ORGANIZATION_SLUG);
        if (orgSlugHeader != null && !orgSlugHeader.isBlank()) {
            Optional<Organization> org = organizationRepository.findBySlug(orgSlugHeader);
            if (org.isPresent() && org.get().isActive()) {
                return new TenantContext.TenantInfo(
                        org.get().getId(),
                        org.get().getSlug(),
                        username,
                        role
                );
            }
            log.warn("Organization not found or inactive: slug={}", orgSlugHeader);
        }

        // No organization context - return user info only (for admins or public endpoints)
        if (username != null) {
            return new TenantContext.TenantInfo(null, null, username, role);
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip filtering for health checks and public endpoints
        return path.startsWith(ACTUATOR_HEALTH)
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }
}
