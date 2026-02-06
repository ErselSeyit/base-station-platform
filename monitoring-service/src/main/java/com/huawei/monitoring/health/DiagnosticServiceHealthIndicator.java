package com.huawei.monitoring.health;

import static com.huawei.common.constants.JsonResponseKeys.KEY_SERVICE;
import static com.huawei.common.constants.JsonResponseKeys.KEY_STATUS;
import static com.huawei.common.constants.ServiceNames.AI_DIAGNOSTIC_SERVICE;

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

    private final DiagnosticClient diagnosticClient;

    public DiagnosticServiceHealthIndicator(DiagnosticClient diagnosticClient) {
        this.diagnosticClient = diagnosticClient;
    }

    @Override
    public Health health() {
        if (!diagnosticClient.isEnabled()) {
            return Health.up()
                    .withDetail(KEY_SERVICE, AI_DIAGNOSTIC_SERVICE)
                    .withDetail(KEY_STATUS, "disabled")
                    .build();
        }

        if (diagnosticClient.isCircuitBreakerOpen()) {
            return Health.down()
                    .withDetail(KEY_SERVICE, AI_DIAGNOSTIC_SERVICE)
                    .withDetail(KEY_STATUS, "circuit-open")
                    .withDetail("url", diagnosticClient.getServiceUrl())
                    .build();
        }

        try {
            boolean isAvailable = diagnosticClient.isAvailable();
            if (isAvailable) {
                return Health.up()
                        .withDetail(KEY_SERVICE, AI_DIAGNOSTIC_SERVICE)
                        .withDetail(KEY_STATUS, "available")
                        .withDetail("url", diagnosticClient.getServiceUrl())
                        .build();
            } else {
                return Health.down()
                        .withDetail(KEY_SERVICE, AI_DIAGNOSTIC_SERVICE)
                        .withDetail(KEY_STATUS, "unhealthy")
                        .withDetail("url", diagnosticClient.getServiceUrl())
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail(KEY_SERVICE, AI_DIAGNOSTIC_SERVICE)
                    .withDetail(KEY_STATUS, "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
