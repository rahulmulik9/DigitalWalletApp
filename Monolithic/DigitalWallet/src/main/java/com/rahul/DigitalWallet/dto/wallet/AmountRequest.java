package com.rahul.DigitalWallet.dto.wallet;

import com.rahul.DigitalWallet.validator.PositiveAmount;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AmountRequest {
    @PositiveAmount
    private BigDecimal amount;
}