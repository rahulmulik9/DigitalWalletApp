package com.rahul.account_service.controller;

import com.rahul.account_service.dto.wallet.AmountRequest;
import com.rahul.account_service.dto.wallet.WalletResponse;
import com.rahul.account_service.entity.Wallet;
import com.rahul.account_service.security.SecurityUtils;
import com.rahul.account_service.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final SecurityUtils securityUtils;

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable Long walletId) {
        Wallet wallet = walletService.getWallet(walletId);
        verifyOwnership(wallet);
        return ResponseEntity.ok(toResponse(wallet));
    }

    // Internal, service-to-service only — no ownership check.
    // Used by Transaction Service to look up ANY wallet (source, destination,
    // beneficiary's wallet) regardless of who the original caller is.
    // Known Phase 5 simplification: not gateway-protected yet.
    @GetMapping("/{walletId}/internal")
    public ResponseEntity<WalletResponse> getWalletInternal(@PathVariable Long walletId) {
        Wallet wallet = walletService.getWallet(walletId);
        return ResponseEntity.ok(toResponse(wallet));
    }

    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<WalletResponse> deposit(@PathVariable Long walletId, @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.getWallet(walletId);
        verifyOwnership(wallet);
        Wallet updated = walletService.deposit(wallet, request.getAmount());
        return ResponseEntity.ok(toResponse(updated));
    }

    @PostMapping("/{walletId}/withdraw")
    public ResponseEntity<WalletResponse> withdraw(@PathVariable Long walletId, @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.getWallet(walletId);
        verifyOwnership(wallet);
        Wallet updated = walletService.withdraw(wallet, request.getAmount());
        return ResponseEntity.ok(toResponse(updated));
    }

    // Internal — called only by Transaction Service during a transfer.
    @PostMapping("/{walletId}/debit")
    public ResponseEntity<Void> debit(@PathVariable Long walletId, @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.getWallet(walletId);
        walletService.withdraw(wallet, request.getAmount());
        return ResponseEntity.ok().build();
    }

    // Internal — called only by Transaction Service during a transfer.
    @PostMapping("/{walletId}/credit")
    public ResponseEntity<Void> credit(@PathVariable Long walletId, @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.getWallet(walletId);
        walletService.deposit(wallet, request.getAmount());
        return ResponseEntity.ok().build();
    }

    private void verifyOwnership(Wallet wallet) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        String currentEmail = securityUtils.getCurrentUserEmail();
        if (!currentEmail.equals(wallet.getUser().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: wallet does not belong to you");
        }
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }
}