package com.huawei.common.constants;

/**
 * Security-related constants used across services.
 *
 * <p>Centralizes cryptographic algorithm names and security configuration
 * to ensure consistency across the platform.
 */
public final class SecurityConstants {

    // ========================================
    // CRYPTOGRAPHIC ALGORITHMS
    // ========================================

    /**
     * HMAC algorithm used for signing internal authentication tokens
     * and service-to-service request signatures.
     */
    public static final String HMAC_ALGORITHM = "HmacSHA256";

    // ========================================
    // TOKEN VALIDATION
    // ========================================

    /**
     * Maximum age (in milliseconds) for internal auth token timestamps.
     * Tokens older than this are rejected to prevent replay attacks.
     * Default: 30 seconds
     */
    public static final long MAX_TIMESTAMP_AGE_MS = 30_000L;

    /**
     * Minimum required length for JWT secrets.
     * Secrets shorter than this are considered insecure.
     */
    public static final int MIN_SECRET_LENGTH = 32;

    // ========================================
    // CIRCUIT BREAKER / RESILIENCE
    // ========================================

    /**
     * Default maximum consecutive failures before circuit opens.
     */
    public static final int DEFAULT_MAX_FAILURES = 5;

    // Prevent instantiation
    private SecurityConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
