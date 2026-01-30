package com.huawei.monitoring.validation;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Validator that ensures metric values are within realistic ranges for their type.
 * Prevents storing unrealistic or erroneous data that could indicate sensor failures
 * or data corruption.
 */
public class MetricValueValidator implements ConstraintValidator<ValidMetricValue, MetricDataDTO> {

    @Override
    public void initialize(ValidMetricValue constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(MetricDataDTO dto, ConstraintValidatorContext context) {
        // Null checks - let @NotNull handle these
        if (dto == null || dto.getMetricType() == null || dto.getValue() == null) {
            return true;
        }

        MetricType metricType = dto.getMetricType();
        double value = Objects.requireNonNull(dto.getValue(), "Value cannot be null after null check");

        ValidationResult result = validateValue(metricType, value);

        if (!result.isValid()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(result.getMessage())
                   .addConstraintViolation();
            return false;
        }

        return true;
    }

    /**
     * Validates a metric value against expected ranges for the metric type.
     *
     * @param metricType the metric type
     * @param value the value to validate
     * @return validation result with message if invalid
     */
    private ValidationResult validateValue(MetricType metricType, double value) {
        return switch (metricType) {
            case CPU_USAGE, MEMORY_USAGE, UPTIME, INITIAL_BLER, HANDOVER_SUCCESS_RATE ->
                validatePercentage(metricType, value);
            case TEMPERATURE -> validateTemperature(value);
            case POWER_CONSUMPTION -> validatePowerConsumption(value);
            case SIGNAL_STRENGTH, RSRP_NR700, RSRP_NR3500 -> validateSignalStrength(value);
            case CONNECTION_COUNT, RB_PER_SLOT -> validateConnectionCount(value);
            case DATA_THROUGHPUT, DL_THROUGHPUT_NR700, UL_THROUGHPUT_NR700,
                 DL_THROUGHPUT_NR3500, UL_THROUGHPUT_NR3500, PDCP_THROUGHPUT,
                 RLC_THROUGHPUT, CA_DL_THROUGHPUT, CA_UL_THROUGHPUT -> validateDataThroughput(value);
            case FAN_SPEED -> validateFanSpeed(value);
            case SINR_NR700, SINR_NR3500 -> validateSinr(value);
            case LATENCY_PING -> validateLatency(value);
            case TX_IMBALANCE, VSWR -> validateTxImbalance(value);
            case AVG_MCS -> validateMcs(value);
            case RANK_INDICATOR -> validateRankIndicator(value);
            case INTERFERENCE_LEVEL -> validateInterference(value);
        };
    }

    private ValidationResult validateSinr(double value) {
        // SINR range: -20 dB to 50 dB
        // Typical good: 10-30 dB
        if (value < -20 || value > 50) {
            return ValidationResult.invalid(
                String.format("SINR must be between -20 dB and 50 dB, received: %.2f dB", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateLatency(double value) {
        // Latency: 0 ms to 1000 ms
        // Target for 5G: < 15 ms
        if (value < 0 || value > 1000) {
            return ValidationResult.invalid(
                String.format("Latency must be between 0 ms and 1000 ms, received: %.2f ms", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateTxImbalance(double value) {
        // TX Imbalance: 0 dB to 30 dB
        // Target: <= 4 dB
        if (value < 0 || value > 30) {
            return ValidationResult.invalid(
                String.format("TX Imbalance must be between 0 dB and 30 dB, received: %.2f dB", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateMcs(double value) {
        // MCS: 0 to 28 (5G NR)
        if (value < 0 || value > 28) {
            return ValidationResult.invalid(
                String.format("MCS must be between 0 and 28, received: %.2f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateRankIndicator(double value) {
        // Rank: 1, 2, or 4
        if (value != 1 && value != 2 && value != 4) {
            return ValidationResult.invalid(
                String.format("Rank indicator must be 1, 2, or 4, received: %.0f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateInterference(double value) {
        // Interference level: -120 dBm to -40 dBm
        if (value < -120 || value > -40) {
            return ValidationResult.invalid(
                String.format("Interference level must be between -120 dBm and -40 dBm, received: %.2f dBm", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validatePercentage(MetricType metricType, double value) {
        // Percentages must be between 0 and 100
        if (value < 0 || value > 100) {
            return ValidationResult.invalid(
                String.format("%s must be between 0 and 100%%, received: %.2f",
                    metricType, value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateTemperature(double value) {
        // Temperature range: -50°C to 150°C (extreme range for safety)
        // Typical operational: 20-60°C, alarm at 70°C
        if (value < -50 || value > 150) {
            return ValidationResult.invalid(
                String.format("Temperature must be between -50°C and 150°C, received: %.2f°C", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validatePowerConsumption(double value) {
        // Power consumption: 0W to 50,000W (50kW max for large macro cells)
        // Typical: 500-8000W
        if (value < 0 || value > 50000) {
            return ValidationResult.invalid(
                String.format("Power consumption must be between 0W and 50,000W, received: %.2fW", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateSignalStrength(double value) {
        // Signal strength: -120 dBm (very weak) to -20 dBm (very strong)
        // Typical: -100 to -40 dBm
        if (value < -120 || value > -20) {
            return ValidationResult.invalid(
                String.format("Signal strength must be between -120 dBm and -20 dBm, received: %.2f dBm", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateConnectionCount(double value) {
        // Connection count: 0 to 10,000 concurrent connections
        // Typical: 5-500 connections
        if (value < 0 || value > 10000) {
            return ValidationResult.invalid(
                String.format("Connection count must be between 0 and 10,000, received: %.0f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateDataThroughput(double value) {
        // Data throughput: 0 Mbps to 100,000 Mbps (100 Gbps theoretical max)
        // Typical: 100-5000 Mbps
        if (value < 0 || value > 100000) {
            return ValidationResult.invalid(
                String.format("Data throughput must be between 0 Mbps and 100,000 Mbps, received: %.2f Mbps", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateFanSpeed(double value) {
        // Fan speed: 0 RPM to 15,000 RPM (high-performance server fans)
        // Typical: 1000-5000 RPM
        if (value < 0 || value > 15000) {
            return ValidationResult.invalid(
                String.format("Fan speed must be between 0 RPM and 15,000 RPM, received: %.0f RPM", value)
            );
        }
        return ValidationResult.valid();
    }

    /**
     * Simple result class to hold validation status and error message.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
