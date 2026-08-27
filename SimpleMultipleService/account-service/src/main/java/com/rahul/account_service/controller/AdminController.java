package com.rahul.account_service.controller;

import com.rahul.account_service.dto.wallet.WalletResponse;
import com.rahul.account_service.entity.Wallet;
import com.rahul.account_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final WalletService walletService;

    @GetMapping("/wallets")
    public ResponseEntity<List<WalletResponse>> getAllWallets() {

        List<WalletResponse> response =
                walletService.getAllWallets()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(response);
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