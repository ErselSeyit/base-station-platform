package com.huawei.gateway.config;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Request correlation filter for distributed tracing in reactive gateway.
 * 
 * Generates or propagates correlation IDs across service boundaries,
 * enabling end-to-end request tracking in logs.
 * 
 * <p>This is a reactive WebFilter for Spring Cloud Gateway (reactive stack),
 * unlike the servlet Filter used in other services.
 */
@Component
public class LoggingConfig implements WebFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_LOG_KEY = "correlationId";
    private static final String REQUEST_ID_LOG_KEY = "requestId";

    @Override
    @SuppressWarnings("null") // Mono<Void> is always non-null in reactive streams
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // Add to MDC for logging
        MDC.put(CORRELATION_ID_LOG_KEY, correlationId);
        MDC.put(REQUEST_ID_LOG_KEY, requestId);
        
        // Add correlation ID to response headers
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // Clean up MDC after request processing
                    MDC.remove(CORRELATION_ID_LOG_KEY);
                    MDC.remove(REQUEST_ID_LOG_KEY);
                });
    }
}
