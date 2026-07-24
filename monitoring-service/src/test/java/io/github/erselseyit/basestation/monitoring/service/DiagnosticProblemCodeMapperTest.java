package io.github.erselseyit.basestation.monitoring.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterisation tests for the metric-type to problem-code/category mapping
 * extracted from DiagnosticSessionService. These lock the AI-service code
 * vocabulary and the null/case handling so the mapping can be evolved without
 * silently changing what codes the platform emits.
 */
class DiagnosticProblemCodeMapperTest {

    private final DiagnosticProblemCodeMapper mapper = new DiagnosticProblemCodeMapper();

    @Test
    void categoryGroupsMetricsByDomain() {
        assertThat(mapper.categoryFor("CPU_USAGE")).isEqualTo("hardware");
        assertThat(mapper.categoryFor("FAN_SPEED")).isEqualTo("hardware");
        assertThat(mapper.categoryFor("TEMPERATURE")).isEqualTo("power");
        assertThat(mapper.categoryFor("POWER_CONSUMPTION")).isEqualTo("power");
        assertThat(mapper.categoryFor("SIGNAL_STRENGTH")).isEqualTo("network");
        assertThat(mapper.categoryFor("CONNECTION_COUNT")).isEqualTo("network");
    }

    @Test
    void categoryFallsBackToSoftwareForUnmappedTypes() {
        assertThat(mapper.categoryFor("SOME_OTHER_METRIC")).isEqualTo("software");
    }

    @Test
    void categoryIsCaseInsensitive() {
        assertThat(mapper.categoryFor("cpu_usage")).isEqualTo("hardware");
    }

    @Test
    void problemCodeMapsKnownTypesToTheAiVocabulary() {
        assertThat(mapper.problemCodeFor("CPU_USAGE")).isEqualTo("CPU_OVERHEAT");
        assertThat(mapper.problemCodeFor("TEMPERATURE")).isEqualTo("CPU_OVERHEAT");
        assertThat(mapper.problemCodeFor("MEMORY_USAGE")).isEqualTo("MEMORY_PRESSURE");
        assertThat(mapper.problemCodeFor("SIGNAL_STRENGTH")).isEqualTo("SIGNAL_DEGRADATION");
        assertThat(mapper.problemCodeFor("BATTERY_SOC")).isEqualTo("LOW_BATTERY");
        assertThat(mapper.problemCodeFor("HANDOVER_SUCCESS_RATE")).isEqualTo("HANDOVER_FAILURE");
    }

    @Test
    void problemCodeIsCaseInsensitive() {
        assertThat(mapper.problemCodeFor("memory_usage")).isEqualTo("MEMORY_PRESSURE");
    }

    @Test
    void problemCodeFallsBackToTypeIssueForUnmappedTypes() {
        assertThat(mapper.problemCodeFor("FAN_SPEED")).isEqualTo("FAN_SPEED_ISSUE");
    }

    @Test
    void problemCodeIsUnknownWhenMetricTypeIsAbsent() {
        assertThat(mapper.problemCodeFor(null)).isEqualTo("UNKNOWN");
    }
}
