package com.huawei.basestation.config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security configuration for base-station-service.
 * 
 * <p>This configuration:
 * - Enables method-level security (@PreAuthorize, @Secured)
 * - Extracts user info from X-User-Name and X-User-Role headers (set by API Gateway)
 * - Permits actuator endpoints for health checks
 * - Uses stateless session management (JWT-based auth)
 * 
 * <p>Security Flow:
 * 1. API Gateway validates JWT and sets X-User-Name and X-User-Role headers
 * 2. This filter extracts those headers and creates Spring Security context
 * 3. @PreAuthorize annotations on controllers enforce role-based access
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    // Role constants
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String ROLE_USER = "USER";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // CSRF disabled - stateless API with JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Permit only health check publicly, secure other actuator endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole(ROLE_ADMIN)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Station endpoints - read for all authenticated, modify for operators+
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/stations/**").hasAnyRole(ROLE_ADMIN, ROLE_OPERATOR, ROLE_USER)
                .requestMatchers("/api/v1/stations/**").hasAnyRole(ROLE_ADMIN, ROLE_OPERATOR)
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public OncePerRequestFilter headerAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                    @NonNull FilterChain filterChain) throws ServletException, IOException {
                
                String username = request.getHeader(HEADER_USER_NAME);
                String role = request.getHeader(HEADER_USER_ROLE);
                
                if (username != null && !username.isBlank()) {
                    // Create authorities from role header
                    List<SimpleGrantedAuthority> authorities;
                    if (role != null && !role.isBlank()) {
                        // Normalize role to ROLE_ prefix for Spring Security
                        String normalizedRole = role.toUpperCase();
                        if (!normalizedRole.startsWith("ROLE_")) {
                            normalizedRole = "ROLE_" + normalizedRole;
                        }
                        authorities = List.of(new SimpleGrantedAuthority(normalizedRole));
                    } else {
                        authorities = Collections.emptyList();
                    }
                    
                    // Create authentication token and set in security context
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                
                filterChain.doFilter(request, response);
            }
        };
    }
}
