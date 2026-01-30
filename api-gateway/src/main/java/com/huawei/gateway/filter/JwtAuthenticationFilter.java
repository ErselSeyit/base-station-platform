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
import java.util.Objects;

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

    @Value("${security.actuator.allowed-ips:127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
    private String actuatorAllowedIps;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // Handle actuator endpoints separately
            if (path.startsWith("/actuator")) {
                return handleActuatorRequest(exchange, chain, request, path);
            }

            // Skip validation for public endpoints
            if (isPublicEndpoint(path)) {
                log.debug("Skipping JWT validation for public endpoint: {}", path);
                return chain.filter(exchange);
            }

            // Extract and validate token
            String token = extractToken(request, path);
            if (token == null) {
                return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
            }

            JwtValidator.ValidationResult validationResult = jwtValidator.validateToken(token);
            if (!validationResult.isValid()) {
                log.warn("Token validation failed for path {}: {}", path, validationResult.getErrorMessage());
                return unauthorizedResponse(exchange, "Invalid token: " + validationResult.getErrorMessage());
            }

            // Add user context to request
            ServerWebExchange modifiedExchange = addUserHeaders(exchange, request, validationResult);
            log.debug("Token validated successfully for path: {}, user: {}", path, validationResult.getUsername());
            return chain.filter(modifiedExchange);
        };
    }

    /**
     * Handles actuator endpoint requests with IP-based access control.
     */
    private Mono<Void> handleActuatorRequest(ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
            ServerHttpRequest request, String path) {
        String clientIp = getClientIp(request);
        if (!isAllowedActuatorIp(clientIp)) {
            log.warn("Actuator access denied from IP: {} for path: {}", clientIp, path);
            return forbiddenResponse(exchange, "Actuator access denied");
        }
        log.debug("Actuator access allowed from internal IP: {}", clientIp);
        return chain.filter(exchange);
    }

    /**
     * Extracts JWT token from Authorization header.
     * Returns null if header is missing, invalid, or token is empty.
     */
    private String extractToken(ServerHttpRequest request, String path) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            log.warn("Empty token in Authorization header for path: {}", path);
            return null;
        }
        return token;
    }

    /**
     * Adds user context headers to the request for downstream services.
     */
    private ServerWebExchange addUserHeaders(ServerWebExchange exchange,
            ServerHttpRequest request, JwtValidator.ValidationResult validationResult) {
        String username = validationResult.getUsername();
        if (username == null) {
            return exchange;
        }

        String role = validationResult.getRole();
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .header("X-User-Name", username);

        if (role != null) {
            requestBuilder.header("X-User-Role", role);
        }

        String internalAuthToken = generateInternalAuthToken(username, role);
        requestBuilder.header("X-Internal-Auth", internalAuthToken);

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    /**
     * Checks if the endpoint is public (does not require authentication).
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/logout")
                || path.startsWith("/api/reports")
                || path.startsWith("/reports")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    /**
     * Checks if the client IP is allowed to access actuator endpoints.
     */
    private boolean isAllowedActuatorIp(String clientIp) {
        if (clientIp == null) return false;

        for (String allowed : actuatorAllowedIps.split(",")) {
            allowed = allowed.trim();
            if (allowed.contains("/")) {
                // CIDR notation - simplified check for common ranges
                if (isIpInCidr(clientIp, allowed)) {
                    return true;
                }
            } else if (clientIp.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simplified CIDR check for common private IP ranges.
     */
    private boolean isIpInCidr(String ip, String cidr) {
        if (cidr.equals("10.0.0.0/8")) {
            return ip.startsWith("10.");
        } else if (cidr.equals("172.16.0.0/12")) {
            if (ip.startsWith("172.")) {
                try {
                    int second = Integer.parseInt(ip.split("\\.")[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        } else if (cidr.equals("192.168.0.0/16")) {
            return ip.startsWith("192.168.");
        }
        return false;
    }

    /**
     * Extracts client IP from request, considering X-Forwarded-For header.
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return null;
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
     * Creates a forbidden response.
     */
    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
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
            log.error("SECURITY_INTERNAL_SECRET not configured - this is a critical security misconfiguration!");
            throw new IllegalStateException("SECURITY_INTERNAL_SECRET environment variable is required for secure operation");
        }

        long timestamp = System.currentTimeMillis();
        String payload = username + ":" + Objects.requireNonNullElse(role, "USER") + ":" + timestamp;
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
