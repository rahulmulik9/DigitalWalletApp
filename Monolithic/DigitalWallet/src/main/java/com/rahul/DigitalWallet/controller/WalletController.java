package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.wallet.AmountRequest;
import com.rahul.DigitalWallet.dto.wallet.WalletResponse;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.security.SecurityUtils;
import com.rahul.DigitalWallet.service.WalletService;
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

    private void verifyOwnership(Wallet wallet) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return;
        }

        String currentEmail = securityUtils.getCurrentUserEmail();

        if (!currentEmail.equals(wallet.getUser().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: wallet does not belong to you"
            );
        }
    }


    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }
}