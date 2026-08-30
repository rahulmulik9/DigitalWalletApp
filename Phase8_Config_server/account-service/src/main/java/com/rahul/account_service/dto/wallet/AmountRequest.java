package com.rahul.account_service.dto.wallet;

import com.rahul.account_service.validator.PositiveAmount;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AmountRequest {
    @PositiveAmount
    private BigDecimal amount;
}