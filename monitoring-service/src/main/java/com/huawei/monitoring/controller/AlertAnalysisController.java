package com.huawei.monitoring.controller;

import com.huawei.common.dto.DiagnosticRequest;
import com.huawei.common.dto.DiagnosticResponse;
import com.huawei.monitoring.client.DiagnosticClient;
import com.huawei.monitoring.service.AlertParserService;
import com.huawei.monitoring.service.AlertParserService.ParsedAlert;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BYOA (Bring Your Own Alert) Controller.
 * Allows users to paste raw alert logs and get AI-powered analysis.
 *
 * This is a demo-critical feature for customer meetings:
 * - Customer pastes their real alarm log
 * - AI analyzes it instantly
 * - Shows root cause, confidence, and suggested fix
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisController.class);

    private final DiagnosticClient diagnosticClient;
    private final AlertParserService alertParserService;

    public AlertAnalysisController(DiagnosticClient diagnosticClient, AlertParserService alertParserService) {
        this.diagnosticClient = diagnosticClient;
        this.alertParserService = alertParserService;
    }

    /**
     * Analyze raw alert text using AI diagnostics.
     * This is the core BYOA endpoint for demo "wow moments".
     */
    @SuppressWarnings("null") // Objects.requireNonNullElse guarantees non-null returns
    @PostMapping("/analyze")
    public ResponseEntity<AlertAnalysisResponse> analyzeAlert(
            @Valid @RequestBody AlertAnalysisRequest request) {

        log.info("BYOA: Analyzing raw alert (length={})", request.rawAlert().length());

        // 1. Parse raw alert text to extract structured information
        ParsedAlert parsed = alertParserService.parse(request.rawAlert());

        // 2. Build diagnostic request with raw logs for AI analysis
        DiagnosticRequest diagRequest = DiagnosticRequest.builder()
                .stationId(parsed.stationId() != null ? parsed.stationId() : "UNKNOWN")
                .category(parsed.category())
                .severity(parsed.severity())
                .code(parsed.problemCode())
                .message(parsed.message())
                .metrics(parsed.metrics())
                .rawLogs(request.rawAlert())
                .build();

        // 3. Call AI diagnostic service
        DiagnosticResponse diagResponse = diagnosticClient.diagnose(diagRequest);

        // 4. Build user-friendly response with null-safe defaults
        AlertAnalysisResponse response = new AlertAnalysisResponse(
                Objects.requireNonNullElse(parsed.problemCode(), "UNKNOWN_ALERT"),
                Objects.requireNonNullElse(diagResponse.getAction(), "Unable to determine fix"),
                Objects.requireNonNullElse(diagResponse.getReasoning(), "No reasoning available"),
                toPercentage(diagResponse.getConfidence()),
                Objects.requireNonNullElse(diagResponse.getCommands(), Collections.emptyList()),
                Objects.requireNonNullElse(diagResponse.getRiskLevel(), "unknown"),
                Objects.requireNonNullElse(diagResponse.getExpectedOutcome(), "Unknown outcome"),
                Objects.requireNonNullElse(parsed.extractedMetrics(), Collections.emptyMap()),
                Objects.requireNonNullElse(parsed.detectedPatterns(), Collections.emptyList())
        );

        log.info("BYOA: Analysis complete - rootCause='{}', confidence={}%",
                diagResponse.getAction(), response.confidence());

        return ResponseEntity.ok(response);
    }

    /**
     * Health check for the analysis service.
     */
    @GetMapping("/analyze/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean aiAvailable = diagnosticClient.isAvailable();
        return ResponseEntity.ok(Map.of(
                "status", aiAvailable ? "healthy" : "degraded",
                "aiServiceAvailable", aiAvailable,
                "message", aiAvailable
                        ? "BYOA analysis service is ready"
                        : "AI service unavailable, will use rule-based analysis"
        ));
    }

    private int toPercentage(@Nullable Double confidence) {
        if (confidence == null) return 0;
        return (int) Math.round(confidence * 100);
    }

    /**
     * Request for BYOA alert analysis.
     */
    public record AlertAnalysisRequest(
            @NotBlank(message = "Raw alert text is required")
            @Size(min = 10, max = 50000, message = "Alert text must be between 10 and 50000 characters")
            String rawAlert,

            @Nullable
            String deviceType,

            @Nullable
            String location
    ) {}

    /**
     * Response from BYOA alert analysis.
     * Designed for immediate visual impact in demos.
     */
    public record AlertAnalysisResponse(
            /** Identified problem code/type */
            String problemCode,

            /** AI-recommended action (the root cause fix) */
            String suggestedFix,

            /** AI reasoning explaining why this diagnosis was made */
            String reasoning,

            /** Confidence percentage (0-100) */
            int confidence,

            /** Specific commands to execute */
            List<String> commands,

            /** Risk level of the suggested fix (low/medium/high) */
            String riskLevel,

            /** What should happen after fix is applied */
            String expectedOutcome,

            /** Metrics extracted from the alert */
            Map<String, Object> extractedMetrics,

            /** Patterns detected in the alert */
            List<String> detectedPatterns
    ) {}
}
