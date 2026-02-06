package com.huawei.monitoring.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.huawei.monitoring.config.TestSecurityConfig;
import com.huawei.monitoring.model.AlertRule;
import com.huawei.monitoring.model.AlertSeverity;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.service.AlertingService;

/**
 * Unit tests for AlertRuleController.
 *
 * Tests all REST endpoints for managing alert rules.
 */
@WebMvcTest(AlertRuleController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AlertRuleController Tests")
@SuppressWarnings("null") // Mockito matchers return null placeholders
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertingService alertingService;

    @Test
    @DisplayName("GET /api/v1/alerts/rules - Should return all alert rules")
    void getAllRules_ShouldReturnAllRules() throws Exception {
        // Given
        List<AlertRule> rules = Arrays.asList(
                createAlertRule("cpu-critical", "CPU Critical", MetricType.CPU_USAGE, 90.0, AlertSeverity.CRITICAL, true),
                createAlertRule("cpu-warning", "CPU Warning", MetricType.CPU_USAGE, 80.0, AlertSeverity.WARNING, true),
                createAlertRule("memory-critical", "Memory Critical", MetricType.MEMORY_USAGE, 85.0, AlertSeverity.CRITICAL, true)
        );
        when(alertingService.getAllRules()).thenReturn(rules);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("cpu-critical"))
                .andExpect(jsonPath("$[0].name").value("CPU Critical"))
                .andExpect(jsonPath("$[0].threshold").value(90.0))
                .andExpect(jsonPath("$[1].id").value("cpu-warning"))
                .andExpect(jsonPath("$[2].id").value("memory-critical"));

        verify(alertingService).getAllRules();
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules - Should return empty list when no rules exist")
    void getAllRules_NoRules_ReturnsEmptyList() throws Exception {
        // Given
        when(alertingService.getAllRules()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(alertingService).getAllRules();
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules/{ruleId} - Should return rule by ID")
    void getRule_ValidId_ReturnsRule() throws Exception {
        // Given
        String ruleId = "cpu-critical";
        AlertRule rule = createAlertRule(ruleId, "CPU Critical", MetricType.CPU_USAGE, 90.0, AlertSeverity.CRITICAL, true);
        when(alertingService.getRule(ruleId)).thenReturn(Optional.of(rule));

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.name").value("CPU Critical"))
                .andExpect(jsonPath("$.metricType").value("CPU_USAGE"))
                .andExpect(jsonPath("$.threshold").value(90.0))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(alertingService).getRule(ruleId);
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules/{ruleId} - Should return 404 when rule not found")
    void getRule_InvalidId_ReturnsNotFound() throws Exception {
        // Given
        String ruleId = "non-existent-rule";
        when(alertingService.getRule(ruleId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", ruleId))
                .andExpect(status().isNotFound());

        verify(alertingService).getRule(ruleId);
    }

    @Test
    @DisplayName("PUT /api/v1/alerts/rules/{ruleId}/enable - Should enable rule")
    void enableRule_ValidId_ReturnsSuccess() throws Exception {
        // Given
        String ruleId = "cpu-critical";

        // When & Then
        mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/enable", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("enabled"))
                .andExpect(jsonPath("$.ruleId").value(ruleId));

        verify(alertingService).enableRule(ruleId);
    }

    @Test
    @DisplayName("PUT /api/v1/alerts/rules/{ruleId}/disable - Should disable rule")
    void disableRule_ValidId_ReturnsSuccess() throws Exception {
        // Given
        String ruleId = "cpu-critical";

        // When & Then
        mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/disable", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"))
                .andExpect(jsonPath("$.ruleId").value(ruleId));

        verify(alertingService).disableRule(ruleId);
    }

    @Test
    @DisplayName("PUT /api/v1/alerts/rules/{ruleId}/threshold - Should update rule threshold")
    void updateThreshold_ValidIdAndThreshold_ReturnsUpdatedRule() throws Exception {
        // Given
        String ruleId = "cpu-critical";
        Double newThreshold = 95.0;
        AlertRule existingRule = createAlertRule(ruleId, "CPU Critical", MetricType.CPU_USAGE, 90.0, AlertSeverity.CRITICAL, true);

        when(alertingService.getRule(ruleId)).thenReturn(Optional.of(existingRule));

        // When & Then
        mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/threshold", ruleId)
                .param("threshold", newThreshold.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.threshold").value(newThreshold));

        verify(alertingService).getRule(ruleId);
        verify(alertingService).addRule(any(AlertRule.class));
    }

    @Test
    @DisplayName("PUT /api/v1/alerts/rules/{ruleId}/threshold - Should return 404 when rule not found")
    void updateThreshold_InvalidId_ReturnsNotFound() throws Exception {
        // Given
        String ruleId = "non-existent-rule";
        Double newThreshold = 95.0;
        when(alertingService.getRule(ruleId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/threshold", ruleId)
                .param("threshold", newThreshold.toString()))
                .andExpect(status().isNotFound());

        verify(alertingService).getRule(ruleId);
    }

    @Test
    @DisplayName("PUT /api/v1/alerts/rules/{ruleId}/threshold - Should handle various threshold values")
    void updateThreshold_VariousValues_HandlesCorrectly() throws Exception {
        // Given
        String ruleId = "cpu-warning";
        Double[] thresholds = {50.0, 75.5, 100.0, 0.0};

        for (Double threshold : thresholds) {
            AlertRule rule = createAlertRule(ruleId, "CPU Warning", MetricType.CPU_USAGE, 80.0, AlertSeverity.WARNING, true);

            when(alertingService.getRule(ruleId)).thenReturn(Optional.of(rule));

            // When & Then
            mockMvc.perform(put("/api/v1/alerts/rules/{ruleId}/threshold", ruleId)
                    .param("threshold", threshold.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.threshold").value(threshold));
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/alerts/rules/{ruleId} - Should delete rule")
    void deleteRule_ValidId_ReturnsNoContent() throws Exception {
        // Given
        String ruleId = "cpu-critical";

        // When & Then
        mockMvc.perform(delete("/api/v1/alerts/rules/{ruleId}", ruleId))
                .andExpect(status().isNoContent());

        verify(alertingService).removeRule(ruleId);
    }

    @Test
    @DisplayName("DELETE /api/v1/alerts/rules/{ruleId} - Should handle deletion of non-existent rule")
    void deleteRule_NonExistentId_ReturnsNoContent() throws Exception {
        // Given
        String ruleId = "non-existent-rule";

        // When & Then
        mockMvc.perform(delete("/api/v1/alerts/rules/{ruleId}", ruleId))
                .andExpect(status().isNoContent());

        verify(alertingService).removeRule(ruleId);
    }

    private AlertRule createAlertRule(String id, String name, MetricType metricType,
                                     Double threshold, AlertSeverity severity, boolean enabled) {
        return AlertRule.builder()
                .id(id)
                .name(name)
                .metricType(metricType)
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(threshold)
                .severity(severity)
                .message(name + " triggered")
                .enabled(enabled)
                .build();
    }
}
