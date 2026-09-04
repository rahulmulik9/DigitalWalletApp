package com.rahul.account_service.dto.wallet;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletInfo {
    private BigDecimal balance;
    private String currency;
}
