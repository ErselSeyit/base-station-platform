package com.huawei.monitoring.validation;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.model.MetricType;
import com.huawei.monitoring.model.MetricUnit;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator that ensures the unit field matches the expected unit for the metric type.
 */
public class MetricUnitValidator implements ConstraintValidator<ValidMetricUnit, MetricDataDTO> {

    @Override
    public void initialize(ValidMetricUnit constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(MetricDataDTO dto, ConstraintValidatorContext context) {
        // Null checks - let @NotNull handle these
        if (dto == null || dto.getMetricType() == null) {
            return true;
        }

        MetricType metricType = dto.getMetricType();
        String providedUnit = dto.getUnit();

        // If no unit provided, auto-set the correct unit
        if (providedUnit == null || providedUnit.isEmpty()) {
            return true; // Service layer will set correct unit
        }

        // Validate the provided unit matches expected unit
        if (!MetricUnit.isValidUnit(metricType, providedUnit)) {
            String expectedUnit = MetricUnit.getUnitForMetricType(metricType);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Invalid unit '%s' for metric type '%s'. Expected unit: '%s'",
                    providedUnit, metricType, expectedUnit)
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
