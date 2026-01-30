package com.huawei.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Security Headers Filter for API Gateway.
 *
 * Adds security headers to all responses to protect against common attacks:
 * - Clickjacking (X-Frame-Options)
 * - MIME sniffing (X-Content-Type-Options)
 * - XSS attacks (Content-Security-Policy)
 * - Protocol downgrade (Strict-Transport-Security)
 * - Information leakage (Referrer-Policy)
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Value("${security.headers.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${security.headers.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${security.headers.csp.enabled:true}")
    private boolean cspEnabled;

    @Value("${security.headers.frame-options:DENY}")
    private String frameOptions;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            // Prevent MIME type sniffing
            headers.add("X-Content-Type-Options", "nosniff");

            // Prevent clickjacking
            headers.add("X-Frame-Options", frameOptions);

            // XSS protection (legacy, but still useful for older browsers)
            headers.add("X-XSS-Protection", "1; mode=block");

            // Referrer policy - don't leak URLs to external sites
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

            // Permissions policy - restrict browser features
            headers.add("Permissions-Policy",
                "accelerometer=(), camera=(), geolocation=(), gyroscope=(), " +
                "magnetometer=(), microphone=(), payment=(), usb=()");

            // HSTS - force HTTPS (only enable in production with HTTPS)
            if (hstsEnabled) {
                headers.add("Strict-Transport-Security",
                    "max-age=" + hstsMaxAge + "; includeSubDomains; preload");
            }

            // Content Security Policy
            if (cspEnabled) {
                headers.add("Content-Security-Policy", buildCspHeader());
            }

            // Prevent caching of sensitive responses
            String path = exchange.getRequest().getPath().value();
            if (isSensitivePath(path)) {
                headers.add("Cache-Control", "no-store, no-cache, must-revalidate, private");
                headers.add("Pragma", "no-cache");
                headers.add("Expires", "0");
            }
        }));
    }

    private String buildCspHeader() {
        return String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",  // Allow inline for React
            "style-src 'self' 'unsafe-inline'",   // Allow inline styles for MUI
            "img-src 'self' data: https:",
            "font-src 'self' https://fonts.gstatic.com",
            "connect-src 'self' ws: wss:",        // Allow WebSocket connections
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'"
        );
    }

    private boolean isSensitivePath(String path) {
        return path.contains("/auth/")
            || path.contains("/user")
            || path.contains("/admin");
    }

    @Override
    public int getOrder() {
        // Run after response is generated but before it's sent
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
