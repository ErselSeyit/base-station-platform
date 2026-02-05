package com.huawei.gateway.config;

import com.huawei.common.constants.HttpHeaders;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    /** CORS preflight cache duration: 1 hour in seconds */
    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:80,http://localhost}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Environment-specific allowed origins (configurable via application.yml or environment variables)
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Restrict allowed headers to only what's needed (security best practice)
        config.setAllowedHeaders(List.of(
            HttpHeaders.HEADER_CONTENT_TYPE,
            HttpHeaders.HEADER_AUTHORIZATION,
            "X-Requested-With",
            HttpHeaders.HEADER_CORRELATION_ID
        ));

        config.setExposedHeaders(List.of(HttpHeaders.HEADER_CONTENT_TYPE, HttpHeaders.HEADER_AUTHORIZATION, HttpHeaders.HEADER_TOTAL_COUNT, HttpHeaders.HEADER_CORRELATION_ID));
        config.setAllowCredentials(true);
        config.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
