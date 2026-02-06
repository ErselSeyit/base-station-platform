package com.huawei.notification.integration;

import com.huawei.notification.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Dispatches alerts to all configured external integrations.
 *
 * Provides unified interface for sending notifications to multiple
 * channels (PagerDuty, Slack, etc.) with aggregated results.
 */
@Service
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private final List<AlertIntegration> integrations;

    public AlertDispatcher(List<AlertIntegration> integrations) {
        this.integrations = integrations;
        log.info("AlertDispatcher initialized with {} integrations: {}",
                integrations.size(),
                integrations.stream().map(AlertIntegration::getName).toList());
    }

    /**
     * Gets all registered integrations.
     */
    public List<AlertIntegration> getAllIntegrations() {
        return new ArrayList<>(integrations);
    }

    /**
     * Gets only enabled integrations.
     */
    public List<AlertIntegration> getEnabledIntegrations() {
        return integrations.stream()
                .filter(AlertIntegration::isEnabled)
                .toList();
    }

    /**
     * Sends an alert to all enabled integrations.
     *
     * @param notification the notification to send
     * @return aggregated results from all integrations
     */
    public CompletableFuture<DispatchResult> dispatchAlert(Notification notification) {
        List<AlertIntegration> enabled = getEnabledIntegrations();

        if (enabled.isEmpty()) {
            log.debug("No enabled integrations for alert dispatch");
            return CompletableFuture.completedFuture(
                    new DispatchResult(List.of(), 0, 0));
        }

        log.info("Dispatching alert to {} integrations for notification {}",
                enabled.size(), notification.getId());

        // Send to all integrations in parallel
        List<CompletableFuture<AlertIntegration.AlertResult>> futures = enabled.stream()
                .map(integration -> integration.sendAlert(notification))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<AlertIntegration.AlertResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    int successCount = (int) results.stream()
                            .filter(AlertIntegration.AlertResult::success)
                            .count();
                    int failureCount = results.size() - successCount;

                    log.info("Alert dispatch completed: {} success, {} failed",
                            successCount, failureCount);

                    return new DispatchResult(results, successCount, failureCount);
                });
    }

    /**
     * Resolves an alert on all enabled integrations.
     *
     * @param notification the notification to resolve
     * @return aggregated results from all integrations
     */
    public CompletableFuture<DispatchResult> dispatchResolve(Notification notification) {
        List<AlertIntegration> enabled = getEnabledIntegrations();

        if (enabled.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new DispatchResult(List.of(), 0, 0));
        }

        log.info("Dispatching resolve to {} integrations for notification {}",
                enabled.size(), notification.getId());

        List<CompletableFuture<AlertIntegration.AlertResult>> futures = enabled.stream()
                .map(integration -> integration.resolveAlert(notification))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<AlertIntegration.AlertResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    int successCount = (int) results.stream()
                            .filter(AlertIntegration.AlertResult::success)
                            .count();
                    int failureCount = results.size() - successCount;

                    return new DispatchResult(results, successCount, failureCount);
                });
    }

    /**
     * Sends an alert to a specific integration by name.
     *
     * @param integrationName the name of the integration
     * @param notification the notification to send
     * @return the result from the integration
     */
    public CompletableFuture<AlertIntegration.AlertResult> dispatchToIntegration(
            String integrationName, Notification notification) {

        return integrations.stream()
                .filter(i -> i.getName().equals(integrationName))
                .findFirst()
                .map(integration -> {
                    if (!integration.isEnabled()) {
                        return CompletableFuture.completedFuture(
                                AlertIntegration.AlertResult.failure(integrationName,
                                        "Integration is not enabled"));
                    }
                    return integration.sendAlert(notification);
                })
                .orElse(CompletableFuture.completedFuture(
                        AlertIntegration.AlertResult.failure(integrationName,
                                "Integration not found: " + integrationName)));
    }

    /**
     * Tests connection to all integrations.
     *
     * @return map of integration name to connection status
     */
    public Map<String, Boolean> testAllConnections() {
        return integrations.stream()
                .collect(Collectors.toMap(
                        AlertIntegration::getName,
                        AlertIntegration::testConnection
                ));
    }

    /**
     * Gets integration status summary.
     */
    public IntegrationStatus getStatus() {
        List<IntegrationInfo> infos = integrations.stream()
                .map(i -> new IntegrationInfo(i.getName(), i.isEnabled(), i.testConnection()))
                .toList();

        int enabledCount = (int) infos.stream().filter(IntegrationInfo::enabled).count();
        int healthyCount = (int) infos.stream().filter(IntegrationInfo::healthy).count();

        return new IntegrationStatus(infos, integrations.size(), enabledCount, healthyCount);
    }

    /**
     * Result of dispatching to multiple integrations.
     */
    public record DispatchResult(
            List<AlertIntegration.AlertResult> results,
            int successCount,
            int failureCount
    ) {
        public boolean allSuccessful() {
            return failureCount == 0 && successCount > 0;
        }

        public boolean anySuccessful() {
            return successCount > 0;
        }

        public List<AlertIntegration.AlertResult> getFailures() {
            return results.stream()
                    .filter(r -> !r.success())
                    .toList();
        }
    }

    /**
     * Information about a single integration.
     */
    public record IntegrationInfo(
            String name,
            boolean enabled,
            boolean healthy
    ) {}

    /**
     * Overall integration status summary.
     */
    public record IntegrationStatus(
            List<IntegrationInfo> integrations,
            int totalCount,
            int enabledCount,
            int healthyCount
    ) {}
}
