package com.huawei.common.config;

import com.huawei.common.constants.HttpHeaders;
import com.huawei.common.util.RequestUtils;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Request correlation filter for distributed tracing.
 * 
 * <p>Generates or propagates correlation IDs across service boundaries,
 * enabling end-to-end request tracking in logs.
 * 
 * <p>Usage: Add this module as a dependency and enable component scanning
 * for the com.huawei.common.config package, or import this class directly.
 * 
 * <p>The filter:
 * <ul>
 *   <li>Extracts X-Correlation-ID from incoming requests (if present)</li>
 *   <li>Generates a new correlation ID if not present</li>
 *   <li>Adds correlation ID to MDC for logging</li>
 *   <li>Propagates correlation ID in response headers</li>
 * </ul>
 */
@Component
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_LOG_KEY = "correlationId";
    public static final String REQUEST_ID_LOG_KEY = "requestId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(HttpHeaders.HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        String requestId = UUID.randomUUID().toString().substring(0, RequestUtils.REQUEST_ID_LENGTH);

        try {
            MDC.put(CORRELATION_ID_LOG_KEY, correlationId);
            MDC.put(REQUEST_ID_LOG_KEY, requestId);
            
            httpResponse.setHeader(HttpHeaders.HEADER_CORRELATION_ID, correlationId);
            
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_LOG_KEY);
            MDC.remove(REQUEST_ID_LOG_KEY);
        }
    }
    
    /**
     * Gets the current correlation ID from MDC.
     * @return the correlation ID or null if not set
     */
    public static String getCurrentCorrelationId() {
        return MDC.get(CORRELATION_ID_LOG_KEY);
    }
    
    /**
     * Gets the current request ID from MDC.
     * @return the request ID or null if not set
     */
    public static String getCurrentRequestId() {
        return MDC.get(REQUEST_ID_LOG_KEY);
    }
}
