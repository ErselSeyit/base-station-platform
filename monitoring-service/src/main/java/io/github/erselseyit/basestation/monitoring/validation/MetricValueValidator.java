package io.github.erselseyit.basestation.monitoring.validation;

import io.github.erselseyit.basestation.monitoring.dto.MetricDataDTO;
import io.github.erselseyit.basestation.monitoring.model.MetricType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Validator that ensures metric values are within realistic ranges for their type.
 * Prevents storing unrealistic or erroneous data that could indicate sensor failures
 * or data corruption.
 *
 * <p>Most metrics are a plain closed range, so they share the {@link #range} helper;
 * only genuinely different rules (percentages, discrete sets, mixed-unit messages)
 * keep dedicated methods. The {@code switch} stays exhaustive so a newly added
 * {@link MetricType} fails to compile until it is given a rule.
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
            // Infrastructure - Percentages (0-100%)
            case CPU_USAGE, MEMORY_USAGE, UPTIME, INITIAL_BLER, HANDOVER_SUCCESS_RATE,
                 BATTERY_SOC, BATTERY_DOD, ETH_UTILIZATION, SLICE_PRB_UTIL,
                 SLICE_SLA_COMPLIANCE, RRC_SETUP_SUCCESS, PAGING_SUCCESS, GENERATOR_FUEL_LEVEL ->
                validatePercentage(metricType, value);

            // Temperature: -50°C to 150°C (extreme range for safety; typical 20-60°C)
            case TEMPERATURE, BATTERY_CELL_TEMP_MIN, BATTERY_CELL_TEMP_MAX ->
                range(value, -50, 150, "Temperature", "°C", "%.2f");

            // Power consumption: 0-50kW (50kW max for large macro cells; typical 500-8000W)
            case POWER_CONSUMPTION, SITE_POWER_KWH ->
                range(value, 0, 50000, "Power consumption", "W", "%.2f");

            // Signal strength: -120 dBm (very weak) to -20 dBm (very strong)
            case SIGNAL_STRENGTH, RSRP, MW_RSL ->
                range(value, -120, -20, "Signal strength", " dBm", "%.2f");

            // Connection count: 0 to 10,000 concurrent connections
            case CONNECTION_COUNT, RB_PER_SLOT, GPS_SATELLITES ->
                range(value, 0, 10000, "Connection count", "", "%.0f");

            // Throughput (Mbps): 0 to 100 Gbps theoretical max
            case DATA_THROUGHPUT, DL_THROUGHPUT, UL_THROUGHPUT,
                 PDCP_THROUGHPUT, RLC_THROUGHPUT, CA_DL_THROUGHPUT, CA_UL_THROUGHPUT,
                 GTP_THROUGHPUT, SLICE_THROUGHPUT ->
                range(value, 0, 100000, "Data throughput", " Mbps", "%.2f");

            // Fan speed: 0 to 15,000 RPM (high-performance server fans)
            case FAN_SPEED -> range(value, 0, 15000, "Fan speed", " RPM", "%.0f");

            // SINR: -20 dB to 50 dB (typical good 10-30 dB)
            case SINR, MW_SNR -> range(value, -20, 50, "SINR", " dB", "%.2f");

            // Latency (ms): 0 to 1000 ms (5G target < 15 ms)
            case LATENCY_PING, ETH_LATENCY, PACKET_DELAY, SLICE_LATENCY, PTP_OFFSET ->
                range(value, 0, 1000, "Latency", " ms", "%.2f");

            // TX imbalance: 0 dB to 30 dB (target <= 4 dB)
            case TX_IMBALANCE, VSWR, ACLR -> range(value, 0, 30, "TX Imbalance", " dB", "%.2f");

            // MCS: 0 to 28 (5G NR)
            case AVG_MCS -> range(value, 0, 28, "MCS", "", "%.2f");

            // Rank: 1, 2, or 4
            case RANK_INDICATOR, PRECODING_RANK -> validateRankIndicator(value);

            // Interference level: -120 dBm to -40 dBm
            case INTERFERENCE_LEVEL, CO_CHANNEL_INTERFERENCE, PIM_LEVEL ->
                range(value, -120, -40, "Interference level", " dBm", "%.2f");

            // Voltage: 0V to 500V (single/three-phase and solar)
            case UTILITY_VOLTAGE_L1, UTILITY_VOLTAGE_L2, UTILITY_VOLTAGE_L3, SOLAR_PANEL_VOLTAGE ->
                range(value, 0, 500, "Voltage", "V", "%.2f");

            // Power factor: 0 to 1 (unity)
            case POWER_FACTOR -> range(value, 0, 1, "Power factor", "", "%.3f");

            // Generator runtime: 0 to 100,000 hours (bound unit differs from value, so dedicated)
            case GENERATOR_RUNTIME -> validateRuntime(value);

            // Current: 0A to 100A
            case SOLAR_CHARGE_CURRENT -> range(value, 0, 100, "Current", "A", "%.2f");

            // Environmental - Wind
            case WIND_SPEED -> range(value, 0, 200, "Wind speed", " km/h", "%.2f");
            case WIND_DIRECTION -> validateDirection(value);

            // Environmental - Weather
            case PRECIPITATION -> range(value, 0, 500, "Precipitation", " mm/hr", "%.2f");
            case LIGHTNING_DISTANCE -> range(value, 0, 100, "Distance", " km", "%.2f");

            // Environmental - Structural (bound word "degrees" vs value symbol "°", so dedicated)
            case TILT_ANGLE -> validateAngle(value);
            case VIBRATION_LEVEL -> range(value, 0, 100, "Vibration", " mm/s", "%.2f");
            case WATER_LEVEL -> range(value, 0, 500, "Water level", " cm", "%.2f");

            // Environmental - Air quality
            case PM25_LEVEL -> range(value, 0, 1000, "PM2.5", " µg/m³", "%.2f");
            case CO_LEVEL -> range(value, 0, 1000, "CO level", " ppm", "%.2f");

            // Environmental - Binary sensors (0 or 1)
            case SMOKE_DETECTED, DOOR_STATUS, MOTION_DETECTED -> validateBinary(metricType, value);

            // Transport - Fiber optical
            case FIBER_RX_POWER, FIBER_TX_POWER ->
                range(value, -40, 10, "Optical power", " dBm", "%.2f");
            case FIBER_BER -> range(value, 0, 1, "BER", "", "%.2e");
            case FIBER_OSNR -> range(value, 0, 50, "OSNR", " dB", "%.2f");

            // Transport - Microwave
            case MW_MODULATION -> range(value, 0, 12, "Modulation index", "", "%.0f");

            // Transport - Errors
            case ETH_ERRORS -> range(value, 0, 1000000, "Error count", "", "%.0f");

            // Advanced Radio
            case BEAM_WEIGHT_MAG -> range(value, 0, 1, "Beam magnitude", "", "%.4f");
            case BEAM_WEIGHT_PHASE -> validateDirection(value);  // 0-360 degrees
            case OCCUPIED_BANDWIDTH -> range(value, 0, 400, "Bandwidth", " MHz", "%.2f");

            // Network Slicing
            case SLICE_PACKET_LOSS -> validatePacketLoss(value);
        };
    }

    /**
     * Checks a value against a closed {@code [min, max]} range, formatting a
     * message that names the metric and echoes the offending value with its unit.
     * Bounds are rendered with {@link #num} (integer/grouped where natural); the
     * value uses {@code valueFormat} to preserve each metric's precision.
     */
    private static ValidationResult range(double value, double min, double max,
            String label, String unit, String valueFormat) {
        if (value >= min && value <= max) {
            return ValidationResult.valid();
        }
        String template = "%s must be between %s%s and %s%s, received: " + valueFormat + "%s";
        return ValidationResult.invalid(
            String.format(template, label, num(min), unit, num(max), unit, value, unit));
    }

    /** Renders a bound: whole numbers as integers (grouped past 999), else plain. */
    private static String num(double d) {
        if (d == Math.rint(d)) {
            long l = (long) d;
            return Math.abs(l) >= 1000 ? String.format("%,d", l) : Long.toString(l);
        }
        return Double.toString(d);
    }

    private ValidationResult validateRuntime(double value) {
        // Runtime: 0 to 100,000 hours
        if (value < 0 || value > 100000) {
            return ValidationResult.invalid(
                String.format("Runtime must be between 0 and 100,000 hours, received: %.2f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateDirection(double value) {
        // Direction: 0 to 360 degrees
        if (value < 0 || value > 360) {
            return ValidationResult.invalid(
                String.format("Direction must be between 0 and 360 degrees, received: %.2f°", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateAngle(double value) {
        // Tilt angle: -90 to 90 degrees
        if (value < -90 || value > 90) {
            return ValidationResult.invalid(
                String.format("Angle must be between -90 and 90 degrees, received: %.2f°", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validatePacketLoss(double value) {
        // Packet loss: 0 to 100%
        if (value < 0 || value > 100) {
            return ValidationResult.invalid(
                String.format("Packet loss must be between 0 and 100%%, received: %.2f%%", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateBinary(MetricType metricType, double value) {
        // Binary: 0 or 1
        if (value != 0 && value != 1) {
            return ValidationResult.invalid(
                String.format("%s must be 0 or 1, received: %.0f", metricType, value)
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
