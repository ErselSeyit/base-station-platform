package com.huawei.monitoring.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.service.AlertingService;

/**
 * REST API for managing alerting rules.
 * 
 * Enables runtime configuration of thresholds without redeployment.
 */
@RestController
@RequestMapping("/api/v1/alerts/rules")
public class AlertRuleController {

    private static final String RULE_ID_NULL_MESSAGE = "Rule ID cannot be null";

    private final AlertingService alertingService;

    public AlertRuleController(AlertingService alertingService) {
        this.alertingService = alertingService;
    }

    @GetMapping
    public ResponseEntity<List<AlertRule>> getAllRules() {
        return ResponseEntity.ok(alertingService.getAllRules());
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<AlertRule> getRule(@PathVariable String ruleId) {
        return Objects.requireNonNull(
                alertingService.getRule(Objects.requireNonNull(ruleId, RULE_ID_NULL_MESSAGE))
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                "Response cannot be null");
    }

    @PutMapping("/{ruleId}/enable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Map<String, String>> enableRule(@PathVariable String ruleId) {
        String validatedRuleId = Objects.requireNonNull(ruleId, RULE_ID_NULL_MESSAGE);
        alertingService.enableRule(validatedRuleId);
        return ResponseEntity.ok(Map.of("status", "enabled", "ruleId", validatedRuleId));
    }

    @PutMapping("/{ruleId}/disable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Map<String, String>> disableRule(@PathVariable String ruleId) {
        String validatedRuleId = Objects.requireNonNull(ruleId, RULE_ID_NULL_MESSAGE);
        alertingService.disableRule(validatedRuleId);
        return ResponseEntity.ok(Map.of("status", "disabled", "ruleId", validatedRuleId));
    }

    @PutMapping("/{ruleId}/threshold")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<AlertRule> updateThreshold(
            @PathVariable String ruleId,
            @RequestParam Double threshold) {
        return Objects.requireNonNull(
                alertingService.getRule(Objects.requireNonNull(ruleId, RULE_ID_NULL_MESSAGE))
                        .map(rule -> {
                            AlertRule updated = Objects.requireNonNull(
                                    rule.withThreshold(threshold),
                                    "Updated rule cannot be null");
                            alertingService.addRule(updated);
                            return ResponseEntity.ok(updated);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                "Response cannot be null");
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleId) {
        alertingService.removeRule(Objects.requireNonNull(ruleId, RULE_ID_NULL_MESSAGE));
        return ResponseEntity.noContent().build();
    }
}
