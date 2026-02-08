package com.huawei.monitoring.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.common.constants.SecurityConstants;
import com.huawei.common.dto.AlertEvent;
import com.huawei.common.dto.DiagnosticRequest;
import com.huawei.common.dto.DiagnosticResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
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
@SuppressWarnings("null") // Duration.ofMillis, MediaType.APPLICATION_JSON are never null
public class DiagnosticClient {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticClient.class);
    private static final int MAX_FAILURES = 5;
    private static final Duration CIRCUIT_RESET_TIME = Duration.ofSeconds(30);
    private static final String MSG_SERVICE_DISABLED = "Diagnostic service is disabled";
    private static final String MSG_CIRCUIT_OPEN = "Service temporarily unavailable (circuit open)";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String diagnosticServiceUrl;
    private final String secret;
    private final boolean enabled;

    // Simple circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private volatile long circuitOpenedAt = 0;

    public DiagnosticClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${diagnostic.service.url:http://localhost:9091}") String diagnosticServiceUrl,
            @Value("${diagnostic.service.secret:}") String secret,
            @Value("${diagnostic.service.enabled:true}") boolean enabled,
            @Value("${diagnostic.service.timeout-ms:5000}") int timeoutMs,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.diagnosticServiceUrl = diagnosticServiceUrl;
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.enabled = enabled;

        // Configure timeout for HTTP requests
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(diagnosticServiceUrl)
                .requestFactory(requestFactory)
                .build();

        // Validate secret configuration
        if (secret == null || secret.isBlank()) {
            boolean isProduction = activeProfiles != null && activeProfiles.contains("prod");
            if (isProduction) {
                throw new IllegalStateException(
                    "DIAGNOSTIC_SECRET is required in production - set via diagnostic.service.secret or DIAGNOSTIC_SERVICE_SECRET env var");
            }
            log.warn("DiagnosticClient: No secret configured - requests will not be authenticated (dev mode only)");
        }
        log.info("DiagnosticClient initialized: url={}, enabled={}, authenticated={}, timeout={}ms",
                diagnosticServiceUrl, enabled, secret != null && !secret.isBlank(), timeoutMs);
    }

    /**
     * Compute HMAC-SHA256 signature for request body.
     */
    private String computeHmac(String body) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(SecurityConstants.HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SecurityConstants.HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Get diagnosis for an alert event asynchronously.
     */
    public CompletableFuture<DiagnosticResponse> diagnoseAsync(AlertEvent alert) {
        return CompletableFuture.supplyAsync(() -> diagnose(alert));
    }

    /**
     * Get diagnosis for an alert event asynchronously with explicit problem ID.
     * This ensures the diagnosis response can be matched to the session.
     */
    public CompletableFuture<DiagnosticResponse> diagnoseAsync(AlertEvent alert, String problemId) {
        return CompletableFuture.supplyAsync(() -> diagnose(alert, problemId));
    }

    /**
     * Get diagnosis for an alert event.
     */
    public DiagnosticResponse diagnose(AlertEvent alert) {
        if (!enabled) {
            log.debug("Diagnostic service disabled, returning fallback");
            return DiagnosticResponse.fallback("disabled", MSG_SERVICE_DISABLED);
        }

        if (isCircuitOpen()) {
            log.debug("Circuit breaker open, returning fallback");
            return DiagnosticResponse.fallback(alert.getAlertRuleId(), MSG_CIRCUIT_OPEN);
        }

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert);
        return executeRequest(request);
    }

    /**
     * Get diagnosis for an alert event with explicit problem ID.
     */
    public DiagnosticResponse diagnose(AlertEvent alert, String problemId) {
        if (!enabled) {
            log.debug("Diagnostic service disabled, returning fallback");
            return DiagnosticResponse.fallback(problemId, MSG_SERVICE_DISABLED);
        }

        if (isCircuitOpen()) {
            log.debug("Circuit breaker open, returning fallback");
            return DiagnosticResponse.fallback(problemId, MSG_CIRCUIT_OPEN);
        }

        DiagnosticRequest request = DiagnosticRequest.fromAlertEvent(alert, problemId);
        return executeRequest(request);
    }

    /**
     * Get diagnosis using raw request object.
     */
    public DiagnosticResponse diagnose(DiagnosticRequest request) {
        if (!enabled) {
            return DiagnosticResponse.fallback(request.getId(), MSG_SERVICE_DISABLED);
        }

        if (isCircuitOpen()) {
            return DiagnosticResponse.fallback(request.getId(), MSG_CIRCUIT_OPEN);
        }

        return executeRequest(request);
    }

    private DiagnosticResponse executeRequest(DiagnosticRequest request) {
        log.info("Requesting diagnosis for problem: {} (code={})", request.getId(), request.getCode());

        try {
            // Serialize request body for HMAC computation
            String requestBody = objectMapper.writeValueAsString(request);
            String hmacSignature = computeHmac(requestBody);

            var requestSpec = restClient.post()
                    .uri("/diagnose")
                    .contentType(MediaType.APPLICATION_JSON);

            // Add HMAC header if secret is configured
            if (!hmacSignature.isEmpty()) {
                requestSpec = requestSpec.header("X-HMAC-Signature", hmacSignature);
            }

            DiagnosticResponse response = requestSpec
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

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request: {}", e.getMessage());
            return DiagnosticResponse.fallback(request.getId(), "Serialization error: " + e.getMessage());
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
