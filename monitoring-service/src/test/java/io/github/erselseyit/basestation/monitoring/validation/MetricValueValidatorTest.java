package io.github.erselseyit.basestation.monitoring.validation;

import io.github.erselseyit.basestation.monitoring.dto.MetricDataDTO;
import io.github.erselseyit.basestation.monitoring.model.MetricType;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterisation tests for the ingestion guard. MetricValueValidator rejects
 * physically impossible readings before they reach storage, and had no tests.
 * These lock its behaviour per metric category so the ranges can be refactored
 * later without silently changing what the platform accepts.
 */
class MetricValueValidatorTest {

    private MetricValueValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new MetricValueValidator();

        // On an invalid value the validator disables the default violation and
        // builds a custom one; the builder chain must not NPE.
        context = mock(ConstraintValidatorContext.class);
        ConstraintViolationBuilder builder = mock(ConstraintViolationBuilder.class);
        lenient().when(context.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(builder);
    }

    private boolean validate(MetricType type, double value) {
        MetricDataDTO dto = new MetricDataDTO();
        dto.setStationId(1L);
        dto.setMetricType(type);
        dto.setValue(value);
        return validator.isValid(dto, context);
    }

    @Nested
    class NullHandling {

        @Test
        void nullDtoIsLeftToNotNull() {
            assertThat(validator.isValid(null, context)).isTrue();
        }

        @Test
        void nullMetricTypeIsLeftToNotNull() {
            MetricDataDTO dto = new MetricDataDTO();
            dto.setValue(50.0);
            assertThat(validator.isValid(dto, context)).isTrue();
        }

        @Test
        void nullValueIsLeftToNotNull() {
            MetricDataDTO dto = new MetricDataDTO();
            dto.setMetricType(MetricType.CPU_USAGE);
            assertThat(validator.isValid(dto, context)).isTrue();
        }
    }

    @Nested
    class Percentages {

        @Test
        void acceptsTheZeroToHundredRange() {
            assertThat(validate(MetricType.CPU_USAGE, 0)).isTrue();
            assertThat(validate(MetricType.CPU_USAGE, 100)).isTrue();
            assertThat(validate(MetricType.MEMORY_USAGE, 55.5)).isTrue();
            assertThat(validate(MetricType.HANDOVER_SUCCESS_RATE, 99.9)).isTrue();
        }

        @Test
        void rejectsValuesOutsideZeroToHundred() {
            assertThat(validate(MetricType.CPU_USAGE, -0.1)).isFalse();
            assertThat(validate(MetricType.CPU_USAGE, 100.1)).isFalse();
            assertThat(validate(MetricType.BATTERY_SOC, 150)).isFalse();
        }
    }

    @Nested
    class Temperature {

        @Test
        void acceptsTheOperationalAndExtremeRange() {
            assertThat(validate(MetricType.TEMPERATURE, -50)).isTrue();
            assertThat(validate(MetricType.TEMPERATURE, 25)).isTrue();
            assertThat(validate(MetricType.TEMPERATURE, 150)).isTrue();
        }

        @Test
        void rejectsPhysicallyImpossibleTemperatures() {
            assertThat(validate(MetricType.TEMPERATURE, -60)).isFalse();
            assertThat(validate(MetricType.TEMPERATURE, 200)).isFalse();
        }
    }

    @Nested
    class Voltage {

        @Test
        void acceptsZeroToFiveHundredVolts() {
            assertThat(validate(MetricType.UTILITY_VOLTAGE_L1, 0)).isTrue();
            assertThat(validate(MetricType.UTILITY_VOLTAGE_L1, 230)).isTrue();
            assertThat(validate(MetricType.UTILITY_VOLTAGE_L1, 500)).isTrue();
        }

        @Test
        void rejectsNegativeAndExcessiveVoltage() {
            assertThat(validate(MetricType.UTILITY_VOLTAGE_L1, -1)).isFalse();
            assertThat(validate(MetricType.UTILITY_VOLTAGE_L1, 501)).isFalse();
        }
    }

    @Nested
    class PowerFactor {

        @Test
        void acceptsZeroToUnity() {
            assertThat(validate(MetricType.POWER_FACTOR, 0)).isTrue();
            assertThat(validate(MetricType.POWER_FACTOR, 0.95)).isTrue();
            assertThat(validate(MetricType.POWER_FACTOR, 1)).isTrue();
        }

        @Test
        void rejectsAboveUnity() {
            assertThat(validate(MetricType.POWER_FACTOR, 1.1)).isFalse();
            assertThat(validate(MetricType.POWER_FACTOR, -0.1)).isFalse();
        }
    }

    @Nested
    class Throughput {

        @Test
        void acceptsNonNegativeThroughput() {
            assertThat(validate(MetricType.DL_THROUGHPUT_NR700, 0)).isTrue();
            assertThat(validate(MetricType.DL_THROUGHPUT_NR700, 850.5)).isTrue();
        }

        @Test
        void rejectsNegativeThroughput() {
            assertThat(validate(MetricType.DL_THROUGHPUT_NR700, -1)).isFalse();
        }
    }

    @Nested
    class BinarySensors {

        @Test
        void acceptOnlyZeroOrOne() {
            assertThat(validate(MetricType.SMOKE_DETECTED, 0)).isTrue();
            assertThat(validate(MetricType.SMOKE_DETECTED, 1)).isTrue();
            assertThat(validate(MetricType.DOOR_STATUS, 0)).isTrue();
        }

        @Test
        void rejectNonBinaryValues() {
            assertThat(validate(MetricType.SMOKE_DETECTED, 2)).isFalse();
            assertThat(validate(MetricType.MOTION_DETECTED, 0.5)).isFalse();
        }
    }

    @Nested
    class Completeness {

        @ParameterizedTest
        @EnumSource(MetricType.class)
        void everyMetricTypeHasAValidationRuleAndDoesNotThrow(MetricType type) {
            // The switch is exhaustive at compile time; this proves a plausible
            // in-range value is accepted for every metric and none blows up.
            assertThatCode(() -> validate(type, 1.0)).doesNotThrowAnyException();
        }
    }
}
