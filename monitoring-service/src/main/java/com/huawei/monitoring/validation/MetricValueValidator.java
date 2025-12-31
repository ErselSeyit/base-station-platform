package com.huawei.monitoring.validation;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

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
        double value = dto.getValue();

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
        switch (metricType) {
            case CPU_USAGE, MEMORY_USAGE, UPTIME:
                return validatePercentage(metricType, value);
            case TEMPERATURE:
                return validateTemperature(value);
            case POWER_CONSUMPTION:
                return validatePowerConsumption(value);
            case SIGNAL_STRENGTH:
                return validateSignalStrength(value);
            case CONNECTION_COUNT:
                return validateConnectionCount(value);
            case DATA_THROUGHPUT:
                return validateDataThroughput(value);
            default:
                // Unknown metric type - let it pass, other validations will catch it
                return ValidationResult.valid();
        }
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
