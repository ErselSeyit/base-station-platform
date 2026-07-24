package io.github.erselseyit.basestation.monitoring.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleTest {

    private static AlertRule.Builder cpuRule() {
        return AlertRule.builder()
                .id("rule-1")
                .name("High CPU")
                .metricType(MetricType.CPU_USAGE)
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(90.0)
                .severity(AlertSeverity.CRITICAL)
                .message("CPU usage exceeds threshold");
    }

    @Nested
    class ValueSemantics {

        @Test
        void rulesWithEqualFieldsAreEqual() {
            assertThat(cpuRule().build()).isEqualTo(cpuRule().build());
        }

        @Test
        void equalRulesShareAHashCode() {
            assertThat(cpuRule().build()).hasSameHashCodeAs(cpuRule().build());
        }

        @Test
        void differingFieldsBreakEquality() {
            assertThat(cpuRule().build()).isNotEqualTo(cpuRule().threshold(80.0).build());
            assertThat(cpuRule().build()).isNotEqualTo(cpuRule().severity(AlertSeverity.WARNING).build());
        }

        @Test
        void isNotEqualToOtherTypesOrNull() {
            assertThat(cpuRule().build()).isNotEqualTo("rule-1").isNotEqualTo(null);
        }

        @Test
        void toStringNamesTheRuleAndItsCondition() {
            assertThat(cpuRule().build().toString())
                    .contains("rule-1")
                    .contains("CPU_USAGE")
                    .contains("GREATER_THAN")
                    .contains("90.0");
        }
    }

    @Nested
    class RequiredFields {

        // Effective Java item 49: enforce restrictions at construction, so a
        // malformed rule fails immediately rather than throwing a
        // NullPointerException later inside alert evaluation, where the
        // operator/threshold are unboxed on a background path.

        @Test
        void rejectsAMissingThreshold() {
            assertThatThrownBy(() -> cpuRule().threshold(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("threshold");
        }

        @Test
        void rejectsAMissingOperator() {
            assertThatThrownBy(() -> cpuRule().operator(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("operator");
        }

        @Test
        void rejectsAMissingMetricType() {
            assertThatThrownBy(() -> cpuRule().metricType(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("metricType");
        }

        @Test
        void withThresholdRejectsNull() {
            assertThatThrownBy(() -> cpuRule().build().withThreshold(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("threshold");
        }
    }

    @Nested
    class ImmutableUpdates {

        @Test
        void withThresholdChangesOnlyTheThreshold() {
            AlertRule original = cpuRule().build();
            AlertRule updated = original.withThreshold(75.0);

            assertThat(updated.getThreshold()).isEqualTo(75.0);
            assertThat(original.getThreshold()).isEqualTo(90.0);
            assertThat(updated).isEqualTo(cpuRule().threshold(75.0).build());
        }

        @Test
        void withEnabledChangesOnlyTheEnabledFlag() {
            AlertRule original = cpuRule().build();
            AlertRule disabled = original.withEnabled(false);

            assertThat(disabled.isEnabled()).isFalse();
            assertThat(original.isEnabled()).isTrue();
            assertThat(disabled).isEqualTo(cpuRule().enabled(false).build());
        }

        @Test
        void withMethodsPreserveEveryOtherField() {
            AlertRule original = cpuRule().build();
            AlertRule updated = original.withThreshold(1.0).withEnabled(false);

            assertThat(updated.getId()).isEqualTo(original.getId());
            assertThat(updated.getName()).isEqualTo(original.getName());
            assertThat(updated.getMetricType()).isEqualTo(original.getMetricType());
            assertThat(updated.getOperator()).isEqualTo(original.getOperator());
            assertThat(updated.getSeverity()).isEqualTo(original.getSeverity());
            assertThat(updated.getMessage()).isEqualTo(original.getMessage());
        }
    }
}
