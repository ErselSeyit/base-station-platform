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
            // Infrastructure - Percentages (0-100%)
            case CPU_USAGE, MEMORY_USAGE, UPTIME, INITIAL_BLER, HANDOVER_SUCCESS_RATE,
                 BATTERY_SOC, BATTERY_DOD, ETH_UTILIZATION, SLICE_PRB_UTIL,
                 SLICE_SLA_COMPLIANCE, RRC_SETUP_SUCCESS, PAGING_SUCCESS ->
                validatePercentage(metricType, value);

            // Temperature metrics
            case TEMPERATURE, BATTERY_CELL_TEMP_MIN, BATTERY_CELL_TEMP_MAX ->
                validateTemperature(value);

            // Power metrics
            case POWER_CONSUMPTION, SITE_POWER_KWH -> validatePowerConsumption(value);

            // Signal strength metrics (dBm)
            case SIGNAL_STRENGTH, RSRP_NR700, RSRP_NR3500 -> validateSignalStrength(value);

            // Count metrics
            case CONNECTION_COUNT, RB_PER_SLOT, GPS_SATELLITES -> validateConnectionCount(value);

            // Throughput metrics (Mbps)
            case DATA_THROUGHPUT, DL_THROUGHPUT_NR700, UL_THROUGHPUT_NR700,
                 DL_THROUGHPUT_NR3500, UL_THROUGHPUT_NR3500, PDCP_THROUGHPUT,
                 RLC_THROUGHPUT, CA_DL_THROUGHPUT, CA_UL_THROUGHPUT,
                 GTP_THROUGHPUT, SLICE_THROUGHPUT -> validateDataThroughput(value);

            // Fan speed
            case FAN_SPEED -> validateFanSpeed(value);

            // SINR metrics
            case SINR_NR700, SINR_NR3500, MW_SNR -> validateSinr(value);

            // Latency metrics (ms)
            case LATENCY_PING, ETH_LATENCY, PACKET_DELAY, SLICE_LATENCY, PTP_OFFSET ->
                validateLatency(value);

            // TX/RF metrics
            case TX_IMBALANCE, VSWR, ACLR -> validateTxImbalance(value);

            // MCS
            case AVG_MCS -> validateMcs(value);

            // Rank
            case RANK_INDICATOR, PRECODING_RANK -> validateRankIndicator(value);

            // Interference
            case INTERFERENCE_LEVEL, CO_CHANNEL_INTERFERENCE, PIM_LEVEL -> validateInterference(value);

            // Power & Energy - Voltage (0-500V)
            case UTILITY_VOLTAGE_L1, UTILITY_VOLTAGE_L2, UTILITY_VOLTAGE_L3, SOLAR_PANEL_VOLTAGE ->
                validateVoltage(value);

            // Power factor (0-1)
            case POWER_FACTOR -> validatePowerFactor(value);

            // Generator metrics
            case GENERATOR_FUEL_LEVEL -> validatePercentage(metricType, value);
            case GENERATOR_RUNTIME -> validateRuntime(value);

            // Current (0-100A)
            case SOLAR_CHARGE_CURRENT -> validateCurrent(value);

            // Environmental - Wind
            case WIND_SPEED -> validateWindSpeed(value);
            case WIND_DIRECTION -> validateDirection(value);

            // Environmental - Weather
            case PRECIPITATION -> validatePrecipitation(value);
            case LIGHTNING_DISTANCE -> validateDistance(value);

            // Environmental - Structural
            case TILT_ANGLE -> validateAngle(value);
            case VIBRATION_LEVEL -> validateVibration(value);
            case WATER_LEVEL -> validateWaterLevel(value);

            // Environmental - Air quality
            case PM25_LEVEL -> validatePM25(value);
            case CO_LEVEL -> validateCOLevel(value);

            // Environmental - Binary sensors (0 or 1)
            case SMOKE_DETECTED, DOOR_STATUS, MOTION_DETECTED -> validateBinary(metricType, value);

            // Transport - Fiber optical
            case FIBER_RX_POWER, FIBER_TX_POWER -> validateOpticalPower(value);
            case FIBER_BER -> validateBER(value);
            case FIBER_OSNR -> validateOSNR(value);

            // Transport - Microwave
            case MW_RSL -> validateSignalStrength(value);
            case MW_MODULATION -> validateModulation(value);

            // Transport - Errors
            case ETH_ERRORS -> validateErrorCount(value);

            // Advanced Radio
            case BEAM_WEIGHT_MAG -> validateBeamMagnitude(value);
            case BEAM_WEIGHT_PHASE -> validateDirection(value);  // 0-360 degrees
            case OCCUPIED_BANDWIDTH -> validateBandwidth(value);

            // Network Slicing
            case SLICE_PACKET_LOSS -> validatePacketLoss(value);
        };
    }

    private ValidationResult validateVoltage(double value) {
        // Voltage: 0V to 500V (covers single/three-phase and solar)
        if (value < 0 || value > 500) {
            return ValidationResult.invalid(
                String.format("Voltage must be between 0V and 500V, received: %.2fV", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validatePowerFactor(double value) {
        // Power factor: 0 to 1 (unity)
        if (value < 0 || value > 1) {
            return ValidationResult.invalid(
                String.format("Power factor must be between 0 and 1, received: %.3f", value)
            );
        }
        return ValidationResult.valid();
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

    private ValidationResult validateCurrent(double value) {
        // Current: 0 to 100A
        if (value < 0 || value > 100) {
            return ValidationResult.invalid(
                String.format("Current must be between 0A and 100A, received: %.2fA", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateWindSpeed(double value) {
        // Wind speed: 0 to 200 km/h (hurricane force)
        if (value < 0 || value > 200) {
            return ValidationResult.invalid(
                String.format("Wind speed must be between 0 and 200 km/h, received: %.2f km/h", value)
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

    private ValidationResult validatePrecipitation(double value) {
        // Precipitation: 0 to 500 mm/hr (extreme rainfall)
        if (value < 0 || value > 500) {
            return ValidationResult.invalid(
                String.format("Precipitation must be between 0 and 500 mm/hr, received: %.2f mm/hr", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateDistance(double value) {
        // Distance: 0 to 100 km
        if (value < 0 || value > 100) {
            return ValidationResult.invalid(
                String.format("Distance must be between 0 and 100 km, received: %.2f km", value)
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

    private ValidationResult validateVibration(double value) {
        // Vibration: 0 to 100 mm/s RMS
        if (value < 0 || value > 100) {
            return ValidationResult.invalid(
                String.format("Vibration must be between 0 and 100 mm/s, received: %.2f mm/s", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateWaterLevel(double value) {
        // Water level: 0 to 500 cm
        if (value < 0 || value > 500) {
            return ValidationResult.invalid(
                String.format("Water level must be between 0 and 500 cm, received: %.2f cm", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validatePM25(double value) {
        // PM2.5: 0 to 1000 µg/m³
        if (value < 0 || value > 1000) {
            return ValidationResult.invalid(
                String.format("PM2.5 must be between 0 and 1000 µg/m³, received: %.2f µg/m³", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateCOLevel(double value) {
        // CO level: 0 to 1000 ppm
        if (value < 0 || value > 1000) {
            return ValidationResult.invalid(
                String.format("CO level must be between 0 and 1000 ppm, received: %.2f ppm", value)
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

    private ValidationResult validateOpticalPower(double value) {
        // Optical power: -40 dBm to 10 dBm
        if (value < -40 || value > 10) {
            return ValidationResult.invalid(
                String.format("Optical power must be between -40 dBm and 10 dBm, received: %.2f dBm", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateBER(double value) {
        // BER: 0 to 1 (typically very small, e.g., 1e-12)
        if (value < 0 || value > 1) {
            return ValidationResult.invalid(
                String.format("BER must be between 0 and 1, received: %.2e", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateOSNR(double value) {
        // OSNR: 0 to 50 dB
        if (value < 0 || value > 50) {
            return ValidationResult.invalid(
                String.format("OSNR must be between 0 and 50 dB, received: %.2f dB", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateModulation(double value) {
        // Modulation scheme index: 0 to 12 (BPSK to 4096-QAM)
        if (value < 0 || value > 12) {
            return ValidationResult.invalid(
                String.format("Modulation index must be between 0 and 12, received: %.0f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateErrorCount(double value) {
        // Error count: 0 to 1,000,000
        if (value < 0 || value > 1000000) {
            return ValidationResult.invalid(
                String.format("Error count must be between 0 and 1,000,000, received: %.0f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateBeamMagnitude(double value) {
        // Beam weight magnitude: 0 to 1 (normalized)
        if (value < 0 || value > 1) {
            return ValidationResult.invalid(
                String.format("Beam magnitude must be between 0 and 1, received: %.4f", value)
            );
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateBandwidth(double value) {
        // Bandwidth: 0 to 400 MHz (5G NR max)
        if (value < 0 || value > 400) {
            return ValidationResult.invalid(
                String.format("Bandwidth must be between 0 and 400 MHz, received: %.2f MHz", value)
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
