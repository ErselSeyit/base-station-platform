package io.github.erselseyit.basestation.notification.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

import io.github.erselseyit.basestation.common.config.CorrelationIdFilter;
import io.github.erselseyit.basestation.common.constants.HttpHeaders;

/**
 * Restores the correlation id carried on an inbound RabbitMQ message into the
 * logging MDC for the duration of the listener invocation, then clears it.
 *
 * <p>The producer stamps {@code X-Correlation-ID} onto every message
 * (see monitoring-service's outbound post-processor). Applied as advice on the
 * listener container factory so both listeners are covered without either
 * having to know about it, and so the id is set before the listener runs and
 * removed afterwards — leaving no bleed between messages processed on a reused
 * consumer thread.
 */
public class CorrelationIdInboundAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String correlationId = extractCorrelationId(invocation);

        boolean applied = correlationId != null && !correlationId.isBlank();
        if (applied) {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_LOG_KEY, correlationId);
        }
        try {
            return invocation.proceed();
        } finally {
            if (applied) {
                MDC.remove(CorrelationIdFilter.CORRELATION_ID_LOG_KEY);
            }
        }
    }

    private String extractCorrelationId(MethodInvocation invocation) {
        for (Object arg : invocation.getArguments()) {
            if (arg instanceof Message message) {
                Object header = message.getMessageProperties().getHeader(HttpHeaders.HEADER_CORRELATION_ID);
                if (header != null) {
                    return header.toString();
                }
                // Fall back to the native AMQP correlation-id property.
                return message.getMessageProperties().getCorrelationId();
            }
        }
        return null;
    }
}
