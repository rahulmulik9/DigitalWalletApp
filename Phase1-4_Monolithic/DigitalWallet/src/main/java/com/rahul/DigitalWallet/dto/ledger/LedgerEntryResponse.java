package com.rahul.DigitalWallet.dto.ledger;

import com.rahul.DigitalWallet.entity.LedgerEntryType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LedgerEntryResponse {
    private Long id;
    private Long walletId;
    private Long transactionId;
    private BigDecimal amount;
    private LedgerEntryType type;
    private String description;
    private LocalDateTime createdAt;
}