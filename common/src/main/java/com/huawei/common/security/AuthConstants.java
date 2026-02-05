package com.huawei.common.security;

/**
 * Authentication-related constants used across services.
 *
 * <p>Centralizes Bearer token handling constants to ensure consistency
 * between auth-service (token generation) and api-gateway (token validation).
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.security.AuthConstants.*;
 *
 * if (authHeader.startsWith(BEARER_PREFIX)) {
 *     String token = authHeader.substring(BEARER_PREFIX_LENGTH);
 * }
 * </pre>
 */
public final class AuthConstants {

    /**
     * Standard Bearer token prefix as per RFC 6750.
     * Format: "Bearer " (with trailing space)
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * Length of the Bearer prefix for efficient substring operations.
     */
    public static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /**
     * Token type value returned in token responses.
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * HTTP header name for Authorization.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Cookie name for storing auth token (HttpOnly).
     */
    public static final String AUTH_COOKIE_NAME = "auth_token";

    // Prevent instantiation
    private AuthConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
