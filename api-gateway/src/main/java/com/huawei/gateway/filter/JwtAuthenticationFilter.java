package com.huawei.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.huawei.gateway.util.JwtValidator;

import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * JWT Authentication Filter for API Gateway.
 * 
 * <p>This filter validates JWT tokens before forwarding requests to downstream services.
 * It performs:
 * - Token presence check
 * - Token format validation
 * - Token signature verification
 * - Token expiration check
 * - Claims extraction for downstream services
 * 
 * <p>Security: Only validates tokens, does not authenticate users.
 * Authentication is handled by auth-service.
 */
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;

    @Value("${security.internal.secret:}")
    private String internalSecret;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // Skip validation for public endpoints
            if (isPublicEndpoint(path)) {
                log.debug("Skipping JWT validation for public endpoint: {}", path);
                return chain.filter(exchange);
            }

            // Extract token from Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(BEARER_PREFIX.length());
            if (token.isBlank()) {
                log.warn("Empty token in Authorization header for path: {}", path);
                return unauthorizedResponse(exchange, "Token cannot be empty");
            }

            // Validate token
            JwtValidator.ValidationResult validationResult = jwtValidator.validateToken(token);
            
            if (!validationResult.isValid()) {
                log.warn("Token validation failed for path {}: {}", path, validationResult.getErrorMessage());
                return unauthorizedResponse(exchange, "Invalid token: " + validationResult.getErrorMessage());
            }

            // Token is valid - add username and role to request headers for downstream services
            String username = validationResult.getUsername();
            String role = validationResult.getRole();
            if (username != null) {
                ServerHttpRequest.Builder requestBuilder = request.mutate()
                        .header("X-User-Name", username);
                if (role != null) {
                    requestBuilder.header("X-User-Role", role);
                }

                // Add internal authentication token to prevent header spoofing
                String internalAuthToken = generateInternalAuthToken(username, role);
                requestBuilder.header("X-Internal-Auth", internalAuthToken);

                ServerHttpRequest modifiedRequest = requestBuilder.build();
                exchange = exchange.mutate().request(modifiedRequest).build();
            }

            log.debug("Token validated successfully for path: {}, user: {}", path, username);
            return chain.filter(exchange);
        };
    }

    /**
     * Checks if the endpoint is public (does not require authentication).
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/v1/auth/login") 
                || path.startsWith("/api/v1/auth/register") 
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    /**
     * Creates an unauthorized response.
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("X-Error-Message", message);
        return response.setComplete();
    }

    /**
     * Generates HMAC-signed internal authentication token.
     * Format: signature.username:role:timestamp
     *
     * Prevents header spoofing attacks where attackers bypass gateway
     * and send fake X-User-Role headers directly to services.
     */
    private String generateInternalAuthToken(String username, String role) {
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("SECURITY_INTERNAL_SECRET not configured - internal auth disabled!");
            return "MISSING_SECRET";
        }

        long timestamp = System.currentTimeMillis();
        String payload = username + ":" + (role != null ? role : "USER") + ":" + timestamp;
        String signature = computeHmac(payload, internalSecret);

        return signature + "." + payload;
    }

    private String computeHmac(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to compute HMAC", e);
            return "ERROR";
        }
    }

    /**
     * Configuration class for JwtAuthenticationFilter.
     * Currently uses default configuration, but can be extended with custom options if needed.
     */
    public static class Config {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
