package com.huawei.auth.config;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static com.huawei.common.constants.PublicEndpoints.*;
import static com.huawei.common.security.Roles.ADMIN;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Authentication required\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - accessible without authentication
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Permit only health check publicly, secure other actuator endpoints
                .requestMatchers(ACTUATOR_HEALTH, ACTUATOR_HEALTH_WILDCARD).permitAll()
                .requestMatchers("/actuator/**").hasRole(ADMIN)
                // OpenAPI / Swagger UI endpoints
                .requestMatchers(API_DOCS_WILDCARD).permitAll()
                .requestMatchers(SWAGGER_UI_WILDCARD).permitAll()
                .requestMatchers(SWAGGER_UI_HTML).permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
