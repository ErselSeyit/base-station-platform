package com.huawei.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Internal Service Authentication Filter
 *
 * Prevents header spoofing by verifying requests come from the API Gateway
 * using HMAC-SHA256 signed tokens.
 *
 * Attack prevented:
 *   curl -H "X-User-Role: ADMIN" http://monitoring-service:8085/api/metrics
 *   → HTTP 403 Forbidden (missing or invalid X-Internal-Auth header)
 *
 * How it works:
 *   1. Gateway generates: HMAC-SHA256(username:role:timestamp, secret)
 *   2. Gateway adds: X-Internal-Auth: signature.username:role:timestamp
 *   3. Service verifies signature and timestamp (max 30s old)
 *   4. If valid, allow request through
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "security.internal.enabled", havingValue = "true", matchIfMissing = false)
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthFilter.class);
    private static final String HEADER_INTERNAL_AUTH = "X-Internal-Auth";
    private static final long MAX_TIMESTAMP_AGE_MS = 30_000; // 30 seconds
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${security.internal.secret:}")
    private String internalSecret;

    @Value("${security.internal.enabled:true}")
    private boolean authEnabled;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain chain) throws ServletException, IOException {

        // Skip auth for public endpoints (actuator, auth endpoints)
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") ||
            path.startsWith("/api/v1/auth/login") ||
            path.startsWith("/api/v1/auth/register") ||
            path.startsWith("/api/v1/auth/validate")) {
            chain.doFilter(request, response);
            return;
        }

        // Skip if disabled (for local dev without gateway)
        if (!authEnabled) {
            log.warn("Internal authentication is DISABLED - do not use in production!");
            chain.doFilter(request, response);
            return;
        }

        // Check secret is configured
        if (internalSecret == null || internalSecret.isBlank()) {
            log.error("SECURITY_INTERNAL_SECRET not configured - rejecting request");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Internal authentication not configured");
            return;
        }

        // Extract and validate auth header
        String authHeader = request.getHeader(HEADER_INTERNAL_AUTH);
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Missing X-Internal-Auth header from {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Missing internal authentication - requests must come through API Gateway");
            return;
        }

        // Parse: signature.payload
        String[] parts = authHeader.split("\\.", 2);
        if (parts.length != 2) {
            log.warn("Malformed X-Internal-Auth header");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal auth format");
            return;
        }

        String providedSignature = parts[0];
        String payload = parts[1];

        // Verify HMAC signature
        String expectedSignature = computeHmac(payload, internalSecret);
        if (!MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Invalid HMAC signature in X-Internal-Auth");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal authentication");
            return;
        }

        // Verify timestamp is recent (prevent replay attacks)
        String[] payloadParts = payload.split(":", 3);
        if (payloadParts.length < 3) {
            log.warn("Invalid payload format");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal auth payload");
            return;
        }

        try {
            long timestamp = Long.parseLong(payloadParts[2]);
            long age = System.currentTimeMillis() - timestamp;

            if (age > MAX_TIMESTAMP_AGE_MS) {
                log.warn("Expired internal auth token (age: {}ms)", age);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Internal auth token expired (max age: 30s)");
                return;
            }

            if (age < 0) {
                log.warn("Internal auth token from the future (clock skew)");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid timestamp");
                return;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp in payload");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid timestamp");
            return;
        }

        // Authentication successful - proceed
        log.debug("Internal auth verified for user: {}", payloadParts[0]);
        chain.doFilter(request, response);
    }

    private String computeHmac(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
            );
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid secret key", e);
        }
    }
}
