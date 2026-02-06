package com.huawei.monitoring.config;

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

import static com.huawei.common.security.Roles.*;
import static com.huawei.common.constants.HttpHeaders.*;
import static com.huawei.common.constants.PublicEndpoints.*;

/**
 * Security configuration for monitoring-service.
 * 
 * <p>This configuration:
 * - Enables method-level security (@PreAuthorize, @Secured)
 * - Extracts user info from X-User-Name and X-User-Role headers (set by API Gateway)
 * - Permits actuator and WebSocket endpoints
 * - Uses stateless session management (JWT-based auth)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // CSRF disabled - stateless API with JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Permit only health check publicly, secure other actuator endpoints
                .requestMatchers(ACTUATOR_HEALTH, ACTUATOR_HEALTH_WILDCARD).permitAll()
                .requestMatchers("/actuator/**").hasRole(ADMIN)
                .requestMatchers(SWAGGER_UI_WILDCARD, API_DOCS_WILDCARD).permitAll()
                // Permit WebSocket endpoints (WebSocket has its own auth via origin validation)
                .requestMatchers("/ws/**").permitAll()
                // Metrics endpoints - read for all, write for operators+ and services
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/metrics/**").hasAnyRole(ADMIN, OPERATOR, USER, SERVICE)
                .requestMatchers("/api/v1/metrics/**").hasAnyRole(ADMIN, OPERATOR, SERVICE)
                // Alerts - read for all, acknowledge for operators+
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/alerts/**").hasAnyRole(ADMIN, OPERATOR, USER)
                .requestMatchers("/api/v1/alerts/**").hasAnyRole(ADMIN, OPERATOR)
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public OncePerRequestFilter headerAuthenticationFilter() {
        return new OncePerRequestFilter() {
            
            private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(getClass());
            
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                    @NonNull FilterChain filterChain) throws ServletException, IOException {
                
                String username = request.getHeader(HEADER_USER_NAME);
                String role = request.getHeader(HEADER_USER_ROLE);
                
                log.debug("Header auth filter - path: {}, username: {}, role: {}", request.getRequestURI(), username, role);
                
                if (username != null && !username.isBlank()) {
                    // Create authorities from role header
                    List<SimpleGrantedAuthority> authorities;
                    if (role != null && !role.isBlank()) {
                        // Normalize role to ROLE_ prefix for Spring Security
                        String normalizedRole = role.toUpperCase();
                        if (!normalizedRole.startsWith(ROLE_PREFIX)) {
                            normalizedRole = ROLE_PREFIX + normalizedRole;
                        }
                        authorities = List.of(new SimpleGrantedAuthority(normalizedRole));
                    } else {
                        authorities = Collections.emptyList();
                    }
                    
                    // Create authentication token and set in security context
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authentication set for user: {} with authorities: {}", username, authorities);
                } else {
                    log.warn("No username header found for path: {}", request.getRequestURI());
                }
                
                filterChain.doFilter(request, response);
            }
        };
    }
}
