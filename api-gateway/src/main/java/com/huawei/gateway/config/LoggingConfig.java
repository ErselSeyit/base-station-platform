package com.huawei.gateway.config;

import com.huawei.common.config.CorrelationIdFilter;
import com.huawei.common.constants.HttpHeaders;
import com.huawei.common.util.RequestUtils;

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

    @Override
    @SuppressWarnings("null") // Mono<Void> is always non-null in reactive streams
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        String requestId = UUID.randomUUID().toString().substring(0, RequestUtils.REQUEST_ID_LENGTH);

        // Add to MDC for logging
        MDC.put(CorrelationIdFilter.CORRELATION_ID_LOG_KEY, correlationId);
        MDC.put(CorrelationIdFilter.REQUEST_ID_LOG_KEY, requestId);
        
        // Add correlation ID to response headers
        exchange.getResponse().getHeaders().add(HttpHeaders.HEADER_CORRELATION_ID, correlationId);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // Clean up MDC after request processing
                    MDC.remove(CorrelationIdFilter.CORRELATION_ID_LOG_KEY);
                    MDC.remove(CorrelationIdFilter.REQUEST_ID_LOG_KEY);
                });
    }
}
