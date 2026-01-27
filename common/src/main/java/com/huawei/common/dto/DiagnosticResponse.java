package com.huawei.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

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

    private DiagnosticResponse(Builder builder) {
        this.problemId = builder.problemId;
        this.action = builder.action;
        this.commands = builder.commands != null ? builder.commands : Collections.emptyList();
        this.expectedOutcome = builder.expectedOutcome;
        this.riskLevel = builder.riskLevel;
        this.confidence = builder.confidence != null ? builder.confidence : 0.0;
        this.reasoning = builder.reasoning;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a fallback response when diagnostic service is unavailable.
     */
    public static DiagnosticResponse fallback(String problemId, String message) {
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
        private String problemId;
        private String action;
        private List<String> commands;
        private String expectedOutcome;
        private String riskLevel;
        private Double confidence;
        private String reasoning;

        public Builder problemId(String problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder commands(List<String> commands) {
            this.commands = commands;
            return this;
        }

        public Builder expectedOutcome(String expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        public Builder riskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public DiagnosticResponse build() {
            return new DiagnosticResponse(this);
        }
    }

    // Getters and Setters
    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }

    public String getExpectedOutcome() { return expectedOutcome; }
    public void setExpectedOutcome(String expectedOutcome) { this.expectedOutcome = expectedOutcome; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

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
