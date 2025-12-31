package com.huawei.monitoring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that the metric value is within the expected range for the metric type.
 * This ensures data quality and prevents unrealistic values from being stored.
 *
 * <p>Validated ranges:
 * <ul>
 *   <li>CPU_USAGE, MEMORY_USAGE, UPTIME: 0-100%</li>
 *   <li>TEMPERATURE: -50 to 150°C (extreme range for safety)</li>
 *   <li>POWER_CONSUMPTION: 0-50,000W (accommodates various cell types)</li>
 *   <li>SIGNAL_STRENGTH: -120 to -20 dBm (cellular signal range)</li>
 *   <li>CONNECTION_COUNT: 0-10,000 (maximum realistic connections)</li>
 *   <li>DATA_THROUGHPUT: 0-100,000 Mbps (100 Gbps max)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * &#64;ValidMetricValue
 * public class MetricDataDTO {
 *     private MetricType metricType;
 *     private Double value;
 *     // ...
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MetricValueValidator.class)
@Documented
public @interface ValidMetricValue {
    String message() default "Metric value is outside the valid range for this metric type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
