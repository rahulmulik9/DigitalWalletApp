package com.rahul.DigitalWallet.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PositiveAmountValidator.class)
public @interface PositiveAmount {
    String message() default "Amount must be greater than zero";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}