package com.huawei.monitoring.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that the unit field matches the expected unit for the metric type.
 * This ensures data consistency across the application.
 *
 * <p>Example usage:
 * <pre>
 * &#64;ValidMetricUnit
 * public class MetricDataDTO {
 *     private MetricType metricType;
 *     private String unit;
 *     // ...
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MetricUnitValidator.class)
@Documented
public @interface ValidMetricUnit {
    String message() default "Unit does not match the metric type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
