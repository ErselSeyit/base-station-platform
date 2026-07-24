package io.github.erselseyit.basestation.tmf.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static io.github.erselseyit.basestation.common.constants.HttpHeaders.HEADER_USER_NAME;
import static io.github.erselseyit.basestation.common.constants.HttpHeaders.HEADER_USER_ROLE;
import static io.github.erselseyit.basestation.common.constants.PublicEndpoints.ACTUATOR_HEALTH;
import static io.github.erselseyit.basestation.common.constants.PublicEndpoints.ACTUATOR_HEALTH_WILDCARD;
import static io.github.erselseyit.basestation.common.constants.PublicEndpoints.API_DOCS_WILDCARD;
import static io.github.erselseyit.basestation.common.constants.PublicEndpoints.SWAGGER_UI_WILDCARD;
import static io.github.erselseyit.basestation.common.security.Roles.ADMIN;
import static io.github.erselseyit.basestation.common.security.Roles.OPERATOR;
import static io.github.erselseyit.basestation.common.security.Roles.ROLE_PREFIX;
import static io.github.erselseyit.basestation.common.security.Roles.SERVICE;
import static io.github.erselseyit.basestation.common.security.Roles.USER;

/**
 * Security configuration for the TMF APIs.
 *
 * <p>The TMF endpoints are no longer public. Authentication follows the same
 * gateway-fronted model as the other services: the API Gateway validates the
 * JWT, signs an {@code X-Internal-Auth} header (verified by the shared
 * {@code InternalAuthFilter}, which runs first and blocks spoofed requests),
 * and forwards the user identity in {@code X-User-Name}/{@code X-User-Role}
 * headers, which this config turns into an
 * {@link org.springframework.security.core.Authentication}.
 *
 * <p>Reads are open to any authenticated role; writes require operator-level
 * roles. Only the actuator health endpoint is public. CORS origins are
 * configurable rather than a wildcard.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Allowed CORS origins, comma-separated. Defaults to the local frontend
     * dev server; set {@code TMF_CORS_ALLOWED_ORIGINS} in deployment.
     */
    @Value("${tmf.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // stateless API with gateway-issued auth
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Only health is public; the rest of actuator is admin-only.
                .requestMatchers(ACTUATOR_HEALTH, ACTUATOR_HEALTH_WILDCARD).permitAll()
                .requestMatchers("/actuator/**").hasRole(ADMIN)
                .requestMatchers(SWAGGER_UI_WILDCARD, API_DOCS_WILDCARD).permitAll()
                // TMF resources: read for any authenticated role, write for operators+.
                .requestMatchers(HttpMethod.GET, "/tmf-api/**").hasAnyRole(ADMIN, OPERATOR, USER, SERVICE)
                .requestMatchers("/tmf-api/**").hasAnyRole(ADMIN, OPERATOR, SERVICE)
                .anyRequest().authenticated()
            )
            .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Establishes the Spring Security authentication from the gateway-set
     * {@code X-User-Name}/{@code X-User-Role} headers. These headers are only
     * trustworthy because {@code InternalAuthFilter} has already verified the
     * request came from the gateway (HMAC), so it must run before this filter.
     */
    @Bean
    public OncePerRequestFilter headerAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request,
                    @NonNull HttpServletResponse response,
                    @NonNull FilterChain filterChain) throws ServletException, IOException {

                String username = request.getHeader(HEADER_USER_NAME);
                String role = request.getHeader(HEADER_USER_ROLE);

                if (username != null && !username.isBlank()) {
                    List<SimpleGrantedAuthority> authorities;
                    if (role != null && !role.isBlank()) {
                        String normalizedRole = role.toUpperCase();
                        if (!normalizedRole.startsWith(ROLE_PREFIX)) {
                            normalizedRole = ROLE_PREFIX + normalizedRole;
                        }
                        authorities = List.of(new SimpleGrantedAuthority(normalizedRole));
                    } else {
                        authorities = Collections.emptyList();
                    }

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "X-Total-Count",
            "X-Result-Count"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count",
            "X-Result-Count"
        ));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
