package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.TransactionResponse;
import com.rahul.DigitalWallet.dto.TransferRequest;
import com.rahul.DigitalWallet.entity.Transaction;
import com.rahul.DigitalWallet.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        Transaction txn = transferService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(txn));
    }

    @GetMapping("/wallets/{walletId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> history(
            @PathVariable Long walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TransactionResponse> result = transferService.getHistory(walletId, pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(result);
    }

    private TransactionResponse toResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .fromWalletId(txn.getFromWallet() != null ? txn.getFromWallet().getId() : null)
                .toWalletId(txn.getToWallet() != null ? txn.getToWallet().getId() : null)
                .amount(txn.getAmount())
                .type(txn.getType())
                .status(txn.getStatus())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}