package com.huawei.monitoring.client;

import com.huawei.common.dto.AlertEvent;
import com.huawei.common.dto.DiagnosticRequest;
import com.huawei.common.dto.DiagnosticResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client for calling the AI Diagnostic Service.
 *
 * The diagnostic service can be either:
 * - Python-based AI service (default, supports Ollama/rule-based)
 * - Any HTTP service implementing the diagnostic API
 *
 * Features:
 * - Simple circuit breaker (fails fast after consecutive failures)
 * - Async support
 * - Fallback responses when service unavailable
 */
@Component
public class DiagnosticClient {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticClient.class);
    private static final int MAX_FAILURES = 5;
    private static final Duration CIRCUIT_RESET_TIME = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String diagnosticServiceUrl;
    private final boolean enabled;

    // Simple circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private volatile long circuitOpenedAt = 0;

    public DiagnosticClient(
            RestClient.Builder restClientBuilder,
            @Value("${diagnostic.service.url:http://localhost:9091}") String diagnosticServiceUrl,
            @Value("${diagnostic.service.enabled:true}") boolean enabled) {
        this.diagnosticServiceUrl = diagnosticServiceUrl;
        this.enabled = enabled;
        this.restClient = restClientBuilder
                .baseUrl(diagnosticServiceUrl)
                .build();

        log.info("DiagnosticClient initialized: url={}, enabled={}", diagnosticServiceUrl, enabled);
    }

    /**
     * Get diagnosis for an alert event asynchronously.
     */
    public CompletableFuture<DiagnosticResponse> diagnoseAsync(AlertEvent alert) {
        return CompletableFuture.supplyAsync(() -> diagnose(alert));
    }

    /**
     * Get diagnosis for an alert event.
     */
    public DiagnosticResponse diagnose(AlertEvent alert) {
        if (!enabled) {
            log.debug("Diagnostic service disabled, returning fallback");
            return DiagnosticResponse.fallback("disabled", "Diagnostic service is disabled");
        }

        if (isCircuitOpen()) {
            log.debug("Circuit breaker open, returning fallback");
            return DiagnosticResponse.fallback(alert.getAlertRuleId(), "Service temporarily unavailable (circuit open)");
        }

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);
        return executeRequest(request);
    }

    /**
     * Get diagnosis using raw request object.
     */
    public DiagnosticResponse diagnose(DiagnosticRequest request) {
        if (!enabled) {
            return DiagnosticResponse.fallback(request.getId(), "Diagnostic service is disabled");
        }

        if (isCircuitOpen()) {
            return DiagnosticResponse.fallback(request.getId(), "Service temporarily unavailable (circuit open)");
        }

        return executeRequest(request);
    }

    private DiagnosticResponse executeRequest(DiagnosticRequest request) {
        log.info("Requesting diagnosis for problem: {} (code={})", request.getId(), request.getCode());

        try {
            DiagnosticResponse response = restClient.post()
                    .uri("/diagnose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DiagnosticResponse.class);

            // Success - reset circuit breaker
            onSuccess();

            if (response != null) {
                log.info("Received diagnosis: action='{}', confidence={}, risk={}",
                        response.getAction(), response.getConfidence(), response.getRiskLevel());
                return response;
            }

            return DiagnosticResponse.fallback(request.getId(), "Empty response from service");

        } catch (RestClientException e) {
            onFailure();
            log.warn("Diagnostic service call failed: {}", e.getMessage());
            return DiagnosticResponse.fallback(request.getId(), "Service error: " + e.getMessage());
        } catch (Exception e) {
            onFailure();
            log.error("Unexpected error calling diagnostic service", e);
            return DiagnosticResponse.fallback(request.getId(), "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Check if the diagnostic service is available.
     */
    public boolean isAvailable() {
        if (!enabled) return false;
        if (isCircuitOpen()) return false;

        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("Diagnostic service health check failed: {}", e.getMessage());
            return false;
        }
    }

    // Circuit breaker logic

    private boolean isCircuitOpen() {
        if (!circuitOpen.get()) {
            return false;
        }

        // Check if enough time has passed to try again
        if (System.currentTimeMillis() - circuitOpenedAt > CIRCUIT_RESET_TIME.toMillis()) {
            log.info("Circuit breaker half-open, attempting reset");
            circuitOpen.set(false);
            consecutiveFailures.set(0);
            return false;
        }

        return true;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        if (circuitOpen.compareAndSet(true, false)) {
            log.info("Circuit breaker closed after successful call");
        }
    }

    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= MAX_FAILURES && circuitOpen.compareAndSet(false, true)) {
            circuitOpenedAt = System.currentTimeMillis();
            log.warn("Circuit breaker opened after {} consecutive failures", failures);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServiceUrl() {
        return diagnosticServiceUrl;
    }

    public boolean isCircuitBreakerOpen() {
        return circuitOpen.get();
    }
}
