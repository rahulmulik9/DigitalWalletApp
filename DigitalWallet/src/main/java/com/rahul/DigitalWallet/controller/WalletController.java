package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.AmountRequest;
import com.rahul.DigitalWallet.dto.WalletResponse;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable Long walletId) {
        Wallet wallet = walletService.getWallet(walletId);
        return ResponseEntity.ok(toResponse(wallet));
    }

    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<WalletResponse> deposit(
            @PathVariable Long walletId,
            @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.deposit(walletId, request.getAmount());
        return ResponseEntity.ok(toResponse(wallet));
    }

    @PostMapping("/{walletId}/withdraw")
    public ResponseEntity<WalletResponse> withdraw(
            @PathVariable Long walletId,
            @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.withdraw(walletId, request.getAmount());
        return ResponseEntity.ok(toResponse(wallet));
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