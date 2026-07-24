package io.github.erselseyit.basestation.notification.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the queues are wired to the dead-letter exchange, so a message that
 * exhausts its retries is routed to the DLQ rather than dropped or redelivered
 * forever (Nygard: keep a record of failures).
 */
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void notificationQueueDeadLettersToTheDlx() {
        Object dlx = config.notificationQueue().getArguments().get("x-dead-letter-exchange");
        assertEquals(RabbitMQConfig.DEAD_LETTER_EXCHANGE, dlx);
    }

    @Test
    void diagnosticResolutionQueueDeadLettersToTheDlx() {
        Object dlx = config.diagnosticResolutionQueue().getArguments().get("x-dead-letter-exchange");
        assertEquals(RabbitMQConfig.DEAD_LETTER_EXCHANGE, dlx);
    }

    @Test
    void deadLetterQueueAndExchangeAreNamed() {
        assertEquals(RabbitMQConfig.DEAD_LETTER_QUEUE, config.deadLetterQueue().getName());
        assertEquals(RabbitMQConfig.DEAD_LETTER_EXCHANGE, config.deadLetterExchange().getName());
    }
}
