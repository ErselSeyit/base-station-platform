package com.huawei.notification.integration;

import com.huawei.notification.model.Notification;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for external alert integrations.
 *
 * Implementations provide connectivity to external alerting systems
 * like PagerDuty, Slack, Microsoft Teams, etc.
 */
public interface AlertIntegration {

    /**
     * Get the name of this integration.
     *
     * @return integration name (e.g., "pagerduty", "slack")
     */
    String getName();

    /**
     * Check if this integration is enabled.
     *
     * @return true if the integration is configured and enabled
     */
    boolean isEnabled();

    /**
     * Send an alert asynchronously.
     *
     * @param notification the notification to send
     * @return CompletableFuture that completes when the alert is sent
     */
    CompletableFuture<AlertResult> sendAlert(Notification notification);

    /**
     * Resolve/close an alert.
     *
     * @param notification the notification to resolve
     * @return CompletableFuture that completes when the alert is resolved
     */
    CompletableFuture<AlertResult> resolveAlert(Notification notification);

    /**
     * Test the integration connection.
     *
     * @return true if connection is successful
     */
    boolean testConnection();

    /**
     * Result of an alert operation.
     */
    record AlertResult(
            boolean success,
            String externalId,
            String message,
            String integrationName
    ) {
        public static AlertResult success(String integrationName, String externalId) {
            return new AlertResult(true, externalId, "Alert sent successfully", integrationName);
        }

        public static AlertResult failure(String integrationName, String message) {
            return new AlertResult(false, null, message, integrationName);
        }
    }
}
