package com.huawei.monitoring.health;

import com.huawei.monitoring.client.DiagnosticClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for AI Diagnostic Service dependency.
 *
 * Reports the availability of the diagnostic service which
 * is used for AI-powered troubleshooting recommendations.
 */
@Component
public class DiagnosticServiceHealthIndicator implements HealthIndicator {

    private static final String SERVICE_KEY = "service";
    private static final String SERVICE_NAME = "ai-diagnostic";
    private static final String STATUS_KEY = "status";

    private final DiagnosticClient diagnosticClient;

    public DiagnosticServiceHealthIndicator(DiagnosticClient diagnosticClient) {
        this.diagnosticClient = diagnosticClient;
    }

    @Override
    public Health health() {
        if (!diagnosticClient.isEnabled()) {
            return Health.up()
                    .withDetail(SERVICE_KEY, SERVICE_NAME)
                    .withDetail(STATUS_KEY, "disabled")
                    .build();
        }

        if (diagnosticClient.isCircuitBreakerOpen()) {
            return Health.down()
                    .withDetail(SERVICE_KEY, SERVICE_NAME)
                    .withDetail(STATUS_KEY, "circuit-open")
                    .withDetail("url", diagnosticClient.getServiceUrl())
                    .build();
        }

        try {
            boolean isAvailable = diagnosticClient.isAvailable();
            if (isAvailable) {
                return Health.up()
                        .withDetail(SERVICE_KEY, SERVICE_NAME)
                        .withDetail(STATUS_KEY, "available")
                        .withDetail("url", diagnosticClient.getServiceUrl())
                        .build();
            } else {
                return Health.down()
                        .withDetail(SERVICE_KEY, SERVICE_NAME)
                        .withDetail(STATUS_KEY, "unhealthy")
                        .withDetail("url", diagnosticClient.getServiceUrl())
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail(SERVICE_KEY, SERVICE_NAME)
                    .withDetail(STATUS_KEY, "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
