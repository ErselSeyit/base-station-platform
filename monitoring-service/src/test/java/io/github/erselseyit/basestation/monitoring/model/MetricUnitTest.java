package io.github.erselseyit.basestation.monitoring.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricUnitTest {

    @Nested
    class Completeness {

        @ParameterizedTest
        @EnumSource(MetricType.class)
        void everyMetricTypeDeclaresAUnit(MetricType metricType) {
            assertThat(metricType.getUnit()).isNotNull();
            assertThat(MetricUnit.getUnitForMetricType(metricType)).isNotBlank();
        }
    }

    @Nested
    class PhysicallyCorrectUnits {

        @Test
        void lightningDistanceIsADistanceNotACount() {
            assertThat(MetricType.LIGHTNING_DISTANCE.getUnit()).isEqualTo(MetricUnit.KILOMETRES);
            assertThat(MetricUnit.getUnitForMetricType(MetricType.LIGHTNING_DISTANCE)).isEqualTo("km");
        }

        @Test
        void binarySensorsAreBooleanNotCount() {
            assertThat(MetricType.SMOKE_DETECTED.getUnit()).isEqualTo(MetricUnit.BOOLEAN);
            assertThat(MetricType.DOOR_STATUS.getUnit()).isEqualTo(MetricUnit.BOOLEAN);
            assertThat(MetricType.MOTION_DETECTED.getUnit()).isEqualTo(MetricUnit.BOOLEAN);
        }

        @Test
        void representativeUnitsAreUnchanged() {
            assertThat(MetricType.CPU_USAGE.getUnit()).isEqualTo(MetricUnit.PERCENTAGE);
            assertThat(MetricType.TEMPERATURE.getUnit()).isEqualTo(MetricUnit.CELSIUS);
            assertThat(MetricType.RSRP.getUnit()).isEqualTo(MetricUnit.DBM);
            assertThat(MetricType.SINR.getUnit()).isEqualTo(MetricUnit.DB);
            assertThat(MetricType.SITE_POWER_KWH.getUnit()).isEqualTo(MetricUnit.KWH);
        }
    }

    @Nested
    class Validation {

        @Test
        void acceptsTheExpectedUnit() {
            assertThat(MetricUnit.isValidUnit(MetricType.CPU_USAGE, "%")).isTrue();
        }

        @Test
        void rejectsAMismatchedUnit() {
            assertThat(MetricUnit.isValidUnit(MetricType.CPU_USAGE, "dBm")).isFalse();
        }

        @Test
        void rejectsNullArgumentsRatherThanThrowing() {
            assertThat(MetricUnit.isValidUnit(null, "%")).isFalse();
            assertThat(MetricUnit.isValidUnit(MetricType.CPU_USAGE, null)).isFalse();
        }

        @Test
        void unitLookupRejectsNullMetricType() {
            assertThatThrownBy(() -> MetricUnit.getUnitForMetricType(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
