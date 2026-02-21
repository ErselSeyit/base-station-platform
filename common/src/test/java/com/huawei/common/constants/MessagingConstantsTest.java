package com.huawei.common.constants;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagingConstantsTest {

    @Test
    void alertsExchangeHasCorrectValue() {
        assertThat(MessagingConstants.ALERTS_EXCHANGE).isEqualTo("alerts.exchange");
    }

    @Test
    void notificationQueueHasCorrectValue() {
        assertThat(MessagingConstants.NOTIFICATION_QUEUE).isEqualTo("notification.queue");
    }

    @Test
    void diagnosticResolutionQueueHasCorrectValue() {
        assertThat(MessagingConstants.DIAGNOSTIC_RESOLUTION_QUEUE)
                .isEqualTo("notification.diagnostic-resolution.queue");
    }

    @Test
    void alertTriggeredRoutingKeyHasCorrectValue() {
        assertThat(MessagingConstants.ALERT_TRIGGERED_ROUTING_KEY).isEqualTo("alert.triggered");
    }

    @Test
    void diagnosticResolvedRoutingKeyHasCorrectValue() {
        assertThat(MessagingConstants.DIAGNOSTIC_RESOLVED_ROUTING_KEY).isEqualTo("diagnostic.resolved");
    }

    @Test
    void thresholdConfigExchangeHasCorrectValue() {
        assertThat(MessagingConstants.THRESHOLD_CONFIG_EXCHANGE).isEqualTo("threshold.exchange");
    }

    @Test
    void thresholdConfigUpdatedRoutingKeyHasCorrectValue() {
        assertThat(MessagingConstants.THRESHOLD_CONFIG_UPDATED_ROUTING_KEY).isEqualTo("threshold.updated");
    }

    @Test
    void thresholdConfigQueueHasCorrectValue() {
        assertThat(MessagingConstants.THRESHOLD_CONFIG_QUEUE).isEqualTo("threshold.config.queue");
    }

    @Test
    void alertsDeadletterQueueHasCorrectValue() {
        assertThat(MessagingConstants.ALERTS_DEADLETTER_QUEUE).isEqualTo("alerts.dlq");
    }

    @Test
    void alertsDeadletterExchangeHasCorrectValue() {
        assertThat(MessagingConstants.ALERTS_DEADLETTER_EXCHANGE).isEqualTo("alerts.dlx");
    }

    @Test
    void alertsDeadletterRoutingKeyHasCorrectValue() {
        assertThat(MessagingConstants.ALERTS_DEADLETTER_ROUTING_KEY).isEqualTo("alert.failed");
    }

    @Test
    void cannotInstantiate() throws Exception {
        Constructor<MessagingConstants> constructor = MessagingConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
