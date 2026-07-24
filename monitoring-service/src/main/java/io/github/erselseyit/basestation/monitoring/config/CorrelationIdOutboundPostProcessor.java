package io.github.erselseyit.basestation.monitoring.config;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

import io.github.erselseyit.basestation.common.config.CorrelationIdFilter;
import io.github.erselseyit.basestation.common.constants.HttpHeaders;

/**
 * Stamps the current correlation id onto every outbound RabbitMQ message so it
 * survives the asynchronous hop.
 *
 * <p>Without this the correlation id, carried in the logging MDC for HTTP
 * requests, is lost the moment an alert becomes a message: the consumer logs
 * the resulting notification under a different (or empty) id, and the two ends
 * of the same causal chain cannot be tied together. This is the "cracks
 * propagate along integration points" problem from <em>Release It!</em> — the
 * gap is at the boundary, not in either service.
 *
 * <p>When there is no correlation id in scope (an event raised on a background
 * thread rather than in response to a request), a fresh one is generated so the
 * message still carries a traceable id rather than none.
 */
public class CorrelationIdOutboundPostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_LOG_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        message.getMessageProperties().setHeader(HttpHeaders.HEADER_CORRELATION_ID, correlationId);
        // Also set the native AMQP correlation-id property, which brokers and
        // tooling surface directly.
        message.getMessageProperties().setCorrelationId(correlationId);
        return message;
    }
}
