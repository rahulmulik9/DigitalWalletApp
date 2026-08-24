package com.rahul.DigitalWallet.dto;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Shared request body for deposit and withdraw endpoints.
 * The wallet ID comes from the URL path, not the body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmountRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
}