package com.huawei.common.constants;

/**
 * Custom HTTP header names used across services.
 *
 * <p>Centralizes header names to ensure consistency between API Gateway
 * (which sets these headers) and downstream services (which read them).
 *
 * <h2>Header Flow:</h2>
 * <pre>
 * Client → API Gateway → Downstream Services
 *          ↓ Sets:
 *          X-User-Name
 *          X-User-Role
 *          X-Internal-Auth
 *          X-Correlation-ID
 * </pre>
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.constants.HttpHeaders.*;
 *
 * String username = request.getHeader(HEADER_USER_NAME);
 * String role = request.getHeader(HEADER_USER_ROLE);
 * </pre>
 */
public final class HttpHeaders {

    // ========================================
    // STANDARD HTTP HEADERS
    // ========================================

    /**
     * Standard HTTP Authorization header.
     * Used for Bearer token authentication.
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Standard HTTP Content-Type header.
     */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    // ========================================
    // USER CONTEXT HEADERS (set by API Gateway after JWT validation)
    // ========================================

    /**
     * Header containing the authenticated username.
     * Set by API Gateway after JWT token validation.
     */
    public static final String HEADER_USER_NAME = "X-User-Name";

    /**
     * Header containing the user's role.
     * Set by API Gateway after JWT token validation.
     */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    // ========================================
    // MULTI-TENANT ORGANIZATION HEADERS
    // ========================================

    /**
     * Header containing the organization ID for multi-tenant requests.
     */
    public static final String HEADER_ORGANIZATION_ID = "X-Organization-ID";

    /**
     * Header containing the organization slug for multi-tenant requests.
     */
    public static final String HEADER_ORGANIZATION_SLUG = "X-Organization-Slug";

    /**
     * Header containing HMAC-signed internal authentication token.
     * Used to verify requests originated from trusted API Gateway.
     */
    public static final String HEADER_INTERNAL_AUTH = "X-Internal-Auth";

    // ========================================
    // REQUEST TRACING HEADERS
    // ========================================

    /**
     * Header for distributed request tracing.
     * Passed through all services for log correlation.
     */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    /**
     * Header containing client IP when behind proxy/load balancer.
     * First IP in comma-separated list is the original client.
     */
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * Header indicating the protocol (http/https) used by the original client.
     * Set by load balancers/proxies to indicate the original request protocol.
     */
    public static final String HEADER_FORWARDED_PROTO = "X-Forwarded-Proto";

    // ========================================
    // RESPONSE HEADERS
    // ========================================

    /**
     * Header for total count in paginated responses.
     */
    public static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    /**
     * Header for result count (items in current page) in paginated responses.
     */
    public static final String HEADER_RESULT_COUNT = "X-Result-Count";

    /**
     * Header for error messages in non-200 responses.
     */
    public static final String HEADER_ERROR_MESSAGE = "X-Error-Message";

    // ========================================
    // SECURITY HEADERS
    // ========================================

    /**
     * Prevents MIME type sniffing attacks.
     * Value should be "nosniff".
     */
    public static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    /**
     * Prevents clickjacking by controlling frame embedding.
     * Values: "DENY", "SAMEORIGIN"
     */
    public static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";

    /**
     * Legacy XSS protection header.
     * Value should be "1; mode=block".
     */
    public static final String HEADER_XSS_PROTECTION = "X-XSS-Protection";

    /**
     * HTTP Strict Transport Security header.
     * Forces HTTPS connections.
     */
    public static final String HEADER_HSTS = "Strict-Transport-Security";

    /**
     * Content Security Policy header.
     * Controls resource loading to prevent XSS.
     */
    public static final String HEADER_CSP = "Content-Security-Policy";

    // ========================================
    // SECURITY HEADER VALUES
    // ========================================

    /**
     * Value for X-Content-Type-Options to prevent MIME sniffing.
     */
    public static final String VALUE_NOSNIFF = "nosniff";

    /**
     * Value for X-Frame-Options to deny all framing.
     */
    public static final String VALUE_FRAME_DENY = "DENY";

    /**
     * Value for X-Frame-Options to allow same-origin framing.
     */
    public static final String VALUE_FRAME_SAMEORIGIN = "SAMEORIGIN";

    /**
     * Value for X-XSS-Protection to enable blocking mode.
     */
    public static final String VALUE_XSS_BLOCK = "1; mode=block";

    // ========================================
    // HMAC SIGNATURE HEADER
    // ========================================

    /**
     * Header for HMAC signature in service-to-service calls.
     */
    public static final String HEADER_HMAC_SIGNATURE = "X-HMAC-Signature";

    // Prevent instantiation
    private HttpHeaders() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
