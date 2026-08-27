package com.rahul.transaction_service.controller;

import com.rahul.transaction_service.client.AccountServiceClient;
import com.rahul.transaction_service.dto.ledger.LedgerEntryResponse;
import com.rahul.transaction_service.dto.transfer.TransactionResponse;
import com.rahul.transaction_service.dto.wallet.WalletResponse;
import com.rahul.transaction_service.entity.Transaction;
import com.rahul.transaction_service.security.SecurityUtils;
import com.rahul.transaction_service.service.TransactionService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountServiceClient accountServiceClient;
    private final SecurityUtils securityUtils;

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> history(
            @PathVariable Long walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {

        verifyOwnership(walletId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> transactions = transactionService.getHistory(walletId, pageable);
        return ResponseEntity.ok(transactions.map(this::toResponse));
    }

    @GetMapping("/{walletId}/transactions/filter")
    public ResponseEntity<Page<TransactionResponse>> filteredHistory(
            @PathVariable Long walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {

        verifyOwnership(walletId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> transactions = transactionService.getFilteredHistory(
                walletId, fromDate, toDate, minAmount, maxAmount, pageable);
        return ResponseEntity.ok(transactions.map(this::toResponse));
    }

    private void verifyOwnership(Long walletId) {
        if (securityUtils.isCurrentUserAdmin()) return;

        WalletResponse wallet = accountServiceClient.getWallet(walletId); // throws WalletNotFoundException if missing
        Long currentUserId = securityUtils.getCurrentUserId();

        if (!currentUserId.equals(wallet.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: wallet does not belong to you");
        }
    }

    private TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .fromWalletId(t.getFromWalletId())
                .toWalletId(t.getToWalletId())
                .amount(t.getAmount())
                .type(t.getType())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .ledgerEntries(t.getLedgerEntries().stream().map(e -> LedgerEntryResponse.builder()
                        .id(e.getId()).walletId(e.getWalletId()).transactionId(t.getId())
                        .amount(e.getAmount()).type(e.getType()).description(e.getDescription())
                        .createdAt(e.getCreatedAt()).build()).toList())
                .build();
    }
}