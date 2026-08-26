package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.ledger.LedgerEntryResponse;
import com.rahul.DigitalWallet.dto.transfer.TransactionResponse;
import com.rahul.DigitalWallet.entity.Transaction;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.security.SecurityUtils;
import com.rahul.DigitalWallet.service.TransactionService;
import com.rahul.DigitalWallet.service.WalletService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final WalletService walletService;
    private final SecurityUtils securityUtils;

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> history(
            @PathVariable Long walletId,
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {

        Wallet wallet = walletService.getWallet(walletId);
        verifyOwnership(wallet);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> transactions = transactionService.getHistory(walletId, fromDate, toDate, minAmount, maxAmount, pageable);
        return ResponseEntity.ok(transactions.map(this::toResponse));
    }

    private void verifyOwnership(Wallet wallet) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        String currentEmail = securityUtils.getCurrentUserEmail();
        if (!currentEmail.equals(wallet.getUser().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: wallet does not belong to you");
        }
    }

    private TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .fromWalletId(t.getFromWallet() != null ? t.getFromWallet().getId() : null)
                .toWalletId(t.getToWallet() != null ? t.getToWallet().getId() : null)
                .amount(t.getAmount())
                .type(t.getType())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .ledgerEntries(t.getLedgerEntries().stream().map(e -> LedgerEntryResponse.builder()
                        .id(e.getId()).walletId(e.getWallet().getId()).transactionId(t.getId())
                        .amount(e.getAmount()).type(e.getType()).description(e.getDescription())
                        .createdAt(e.getCreatedAt()).build()).toList())
                .build();
    }
}