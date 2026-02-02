package com.huawei.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.huawei.notification.filter.HeaderAuthenticationFilter;

import static com.huawei.common.security.Roles.*;

/**
 * Security configuration for notification-service.
 *
 * <p>This configuration:
 * - Enables method-level security (@PreAuthorize, @Secured)
 * - Extracts user info from X-User-Name and X-User-Role headers (set by API Gateway)
 * - Permits actuator endpoints for health checks
 * - Uses stateless session management (JWT-based auth)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;

    public SecurityConfig(HeaderAuthenticationFilter headerAuthenticationFilter) {
        this.headerAuthenticationFilter = headerAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // CSRF disabled - stateless API with JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Permit only health check publicly, secure other actuator endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole(ADMIN)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Notifications - read for all authenticated, send/manage for operators+
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/notifications/**").hasAnyRole(ADMIN, OPERATOR, USER)
                .requestMatchers("/api/v1/notifications/**").hasAnyRole(ADMIN, OPERATOR)
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
