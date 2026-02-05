package com.huawei.common.util;

import com.huawei.common.constants.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility methods for HTTP request processing.
 *
 * <p>Provides common request handling functions used across services,
 * such as client IP extraction considering proxy headers.
 */
public final class RequestUtils {

    /**
     * Default value returned when client IP cannot be determined.
     */
    public static final String UNKNOWN_IP = "unknown";

    /**
     * Length of shortened request/correlation IDs.
     * UUIDs are truncated to this length for readability in logs.
     */
    public static final int REQUEST_ID_LENGTH = 8;

    // Prevent instantiation
    private RequestUtils() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Extracts the client IP address from an HTTP request.
     *
     * <p>Handles the case where the application is behind a proxy or load balancer
     * by checking the X-Forwarded-For header first. If present, returns the first
     * IP address (original client). Otherwise, returns the remote address.
     *
     * @param request the HTTP servlet request
     * @return the client IP address, or "unknown" if it cannot be determined
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }

        String xForwardedFor = request.getHeader(HttpHeaders.HEADER_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs: client, proxy1, proxy2, ...
            // The first one is the original client
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : UNKNOWN_IP;
    }

    /**
     * Extracts the username from request headers set by API Gateway.
     *
     * @param request the HTTP servlet request
     * @return the username, or null if not present
     */
    public static String getUsername(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader(HttpHeaders.HEADER_USER_NAME);
    }

    /**
     * Extracts the user role from request headers set by API Gateway.
     *
     * @param request the HTTP servlet request
     * @return the user role, or null if not present
     */
    public static String getUserRole(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader(HttpHeaders.HEADER_USER_ROLE);
    }

    /**
     * Extracts the correlation ID from request headers.
     *
     * @param request the HTTP servlet request
     * @return the correlation ID, or null if not present
     */
    public static String getCorrelationId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader(HttpHeaders.HEADER_CORRELATION_ID);
    }
}
