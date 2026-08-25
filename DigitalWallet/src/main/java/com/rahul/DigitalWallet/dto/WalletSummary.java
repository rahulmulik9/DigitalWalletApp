package com.rahul.DigitalWallet.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletSummary {
    private BigDecimal balance;
    private String currency;
}
