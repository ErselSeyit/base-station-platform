package com.huawei.monitoring.model;

import java.util.List;

/**
 * Embedded document representing an AI-generated solution.
 */
public class AISolution {

    private String action;
    private List<String> commands;
    private String expectedOutcome;
    private String riskLevel;
    private Double confidence;
    private String reasoning;

    public AISolution() {
    }

    public AISolution(String action, List<String> commands, String expectedOutcome,
                      String riskLevel, Double confidence, String reasoning) {
        this.action = action;
        this.commands = commands;
        this.expectedOutcome = expectedOutcome;
        this.riskLevel = riskLevel;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
