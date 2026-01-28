package com.huawei.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;

/**
 * Response DTO from AI diagnostic service.
 * Maps to the Python diagnostic service Solution format.
 */
public class DiagnosticResponse {

    @JsonProperty("problem_id")
    private String problemId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("commands")
    private List<String> commands;

    @JsonProperty("expected_outcome")
    private String expectedOutcome;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("reasoning")
    private String reasoning;

    public DiagnosticResponse() {
        this.commands = Collections.emptyList();
        this.confidence = 0.0;
    }

    private static final Double DEFAULT_CONFIDENCE = 0.0;

    private DiagnosticResponse(Builder builder) {
        this.problemId = builder.problemId;
        this.action = builder.action;
        this.commands = Objects.requireNonNullElse(builder.commands, Collections.emptyList());
        this.expectedOutcome = builder.expectedOutcome;
        this.riskLevel = builder.riskLevel;
        this.confidence = Objects.requireNonNullElse(builder.confidence, DEFAULT_CONFIDENCE);
        this.reasoning = builder.reasoning;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a fallback response when diagnostic service is unavailable.
     */
    public static DiagnosticResponse fallback(@Nullable String problemId, @Nullable String message) {
        return DiagnosticResponse.builder()
                .problemId(problemId)
                .action("Manual investigation required")
                .commands(Collections.emptyList())
                .expectedOutcome("Review alert details and investigate manually")
                .riskLevel("unknown")
                .confidence(0.0)
                .reasoning("Diagnostic service unavailable: " + message)
                .build();
    }

    /**
     * Check if this is a valid diagnostic response with actionable information.
     */
    public boolean isActionable() {
        return action != null && !action.isEmpty() && confidence != null && confidence > 0.3;
    }

    public static class Builder {
        @Nullable private String problemId;
        @Nullable private String action;
        @Nullable private List<String> commands;
        @Nullable private String expectedOutcome;
        @Nullable private String riskLevel;
        @Nullable private Double confidence;
        @Nullable private String reasoning;

        public Builder problemId(@Nullable String problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder action(@Nullable String action) {
            this.action = action;
            return this;
        }

        public Builder commands(@Nullable List<String> commands) {
            this.commands = commands;
            return this;
        }

        public Builder expectedOutcome(@Nullable String expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        public Builder riskLevel(@Nullable String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder confidence(@Nullable Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasoning(@Nullable String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public DiagnosticResponse build() {
            return new DiagnosticResponse(this);
        }
    }

    // Getters and Setters
    @Nullable
    public String getProblemId() { return problemId; }
    public void setProblemId(@Nullable String problemId) { this.problemId = problemId; }

    @Nullable
    public String getAction() { return action; }
    public void setAction(@Nullable String action) { this.action = action; }

    @Nullable
    public List<String> getCommands() { return commands; }
    public void setCommands(@Nullable List<String> commands) { this.commands = commands; }

    @Nullable
    public String getExpectedOutcome() { return expectedOutcome; }
    public void setExpectedOutcome(@Nullable String expectedOutcome) { this.expectedOutcome = expectedOutcome; }

    @Nullable
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(@Nullable String riskLevel) { this.riskLevel = riskLevel; }

    @Nullable
    public Double getConfidence() { return confidence; }
    public void setConfidence(@Nullable Double confidence) { this.confidence = confidence; }

    @Nullable
    public String getReasoning() { return reasoning; }
    public void setReasoning(@Nullable String reasoning) { this.reasoning = reasoning; }

    @Override
    public String toString() {
        return "DiagnosticResponse{" +
                "problemId='" + problemId + '\'' +
                ", action='" + action + '\'' +
                ", commands=" + commands +
                ", riskLevel='" + riskLevel + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
