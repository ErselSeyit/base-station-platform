package com.huawei.monitoring.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
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
@SuppressWarnings("null") // Suppress false positives for Spring's @PathVariable null analysis
public class AlertRuleController {

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
        return alertingService.getRule(ruleId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{ruleId}/enable")
    public ResponseEntity<Map<String, String>> enableRule(@PathVariable String ruleId) {
        alertingService.enableRule(ruleId);
        return ResponseEntity.ok(Map.of("status", "enabled", "ruleId", ruleId));
    }

    @PutMapping("/{ruleId}/disable")
    public ResponseEntity<Map<String, String>> disableRule(@PathVariable String ruleId) {
        alertingService.disableRule(ruleId);
        return ResponseEntity.ok(Map.of("status", "disabled", "ruleId", ruleId));
    }

    @PutMapping("/{ruleId}/threshold")
    public ResponseEntity<AlertRule> updateThreshold(
            @PathVariable String ruleId,
            @RequestParam Double threshold) {
        return alertingService.getRule(ruleId)
                .map(rule -> {
                    AlertRule updated = rule.withThreshold(threshold);
                    alertingService.addRule(updated);
                    return ResponseEntity.ok(updated);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleId) {
        alertingService.removeRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
