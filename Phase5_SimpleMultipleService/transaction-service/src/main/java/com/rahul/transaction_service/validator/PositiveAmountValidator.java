package com.rahul.transaction_service.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class PositiveAmountValidator implements ConstraintValidator<PositiveAmount, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;   // let @NotNull handle the "missing" message separately if you add it
        }
        return value.compareTo(BigDecimal.ZERO) > 0;
    }
}