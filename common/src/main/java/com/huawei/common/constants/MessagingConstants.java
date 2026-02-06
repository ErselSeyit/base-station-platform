package com.huawei.common.constants;

/**
 * RabbitMQ messaging constants used across services.
 *
 * <p>Centralizes exchange names, routing keys, and queue names to ensure
 * consistent messaging configuration between publishers (monitoring-service)
 * and consumers (notification-service).
 *
 * <h2>Usage:</h2>
 * <pre>
 * import static com.huawei.common.constants.MessagingConstants.*;
 *
 * template.convertAndSend(ALERTS_EXCHANGE, ALERT_TRIGGERED_ROUTING_KEY, event);
 * </pre>
 */
public final class MessagingConstants {

    // ========================================
    // ALERTS MESSAGING
    // ========================================

    /**
     * Exchange for alert-related messages.
     */
    public static final String ALERTS_EXCHANGE = "alerts.exchange";

    /**
     * Routing key for triggered alert events.
     */
    public static final String ALERT_TRIGGERED_ROUTING_KEY = "alert.triggered";

    /**
     * Queue for notification service to receive alerts.
     */
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    // ========================================
    // DEAD LETTER QUEUE
    // ========================================

    /**
     * Dead letter queue for failed alert messages.
     */
    public static final String ALERTS_DEADLETTER_QUEUE = "alerts.dlq";

    /**
     * Dead letter exchange for routing failed messages.
     */
    public static final String ALERTS_DEADLETTER_EXCHANGE = "alerts.dlx";

    /**
     * Routing key for failed alert messages.
     */
    public static final String ALERTS_DEADLETTER_ROUTING_KEY = "alert.failed";

    // Prevent instantiation
    private MessagingConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}
