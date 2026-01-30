package com.huawei.notification.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication filter that extracts user info from gateway-forwarded headers.
 * The API Gateway validates JWT tokens and forwards user information via headers.
 *
 * <p>Headers processed:
 * <ul>
 *   <li>X-User-Name: The authenticated username</li>
 *   <li>X-User-Role: The user's role (validated against whitelist, normalized to ROLE_ prefix)</li>
 * </ul>
 */
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String ROLE_PREFIX = "ROLE_";

    /** Allowed roles - reject any role not in this whitelist */
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "OPERATOR", "USER", "VIEWER");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String username = request.getHeader(HEADER_USER_NAME);
        String role = request.getHeader(HEADER_USER_ROLE);

        if (username != null && !username.isBlank()) {
            List<SimpleGrantedAuthority> authorities = buildAuthorities(role);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Builds Spring Security authorities from the role header.
     * Validates role against whitelist to prevent privilege escalation.
     *
     * @param role the role from header (may be null)
     * @return list of authorities, empty if role is null, blank, or not in whitelist
     */
    private List<SimpleGrantedAuthority> buildAuthorities(String role) {
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }

        // Extract base role (without ROLE_ prefix) for whitelist check
        String baseRole = role.toUpperCase();
        if (baseRole.startsWith(ROLE_PREFIX)) {
            baseRole = baseRole.substring(ROLE_PREFIX.length());
        }

        // Validate against whitelist
        if (!ALLOWED_ROLES.contains(baseRole)) {
            log.warn("Rejected invalid role from header: {}", role);
            return Collections.emptyList();
        }

        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + baseRole));
    }
}
