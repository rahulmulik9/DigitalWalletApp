package com.rahul.transaction_service.dto.transfer;

import com.rahul.transaction_service.dto.ledger.LedgerEntryResponse;
import com.rahul.transaction_service.entity.TransactionStatus;
import com.rahul.transaction_service.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private Long id;
    private Long fromWalletId;
    private Long toWalletId;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private List<LedgerEntryResponse> ledgerEntries;
}