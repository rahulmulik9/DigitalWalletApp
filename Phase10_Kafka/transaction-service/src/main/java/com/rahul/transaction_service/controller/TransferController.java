package com.rahul.transaction_service.controller;

import com.rahul.transaction_service.dto.transfer.TransactionResponse;
import com.rahul.transaction_service.dto.transfer.TransferRequest;
import com.rahul.transaction_service.entity.Transaction;
import com.rahul.transaction_service.security.SecurityUtils;
import com.rahul.transaction_service.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final SecurityUtils securityUtils;

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        Long callerUserId = securityUtils.getCurrentUserId();
        Transaction txn = transferService.transfer(request, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(txn));
    }


    private TransactionResponse toResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .fromWalletId(txn.getFromWalletId())
                .toWalletId(txn.getToWalletId())
                .amount(txn.getAmount())
                .type(txn.getType())
                .status(txn.getStatus())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}