//package com.rahul.transaction_service.controller;
//
//import com.rahul.transaction_service.Idempotency.IdempotencyKey;
//import com.rahul.transaction_service.Idempotency.IdempotencyService;
//import com.rahul.transaction_service.dto.transfer.TransactionResponse;
//import com.rahul.transaction_service.dto.transfer.TransferRequest;
//import com.rahul.transaction_service.entity.Transaction;
//import com.rahul.transaction_service.security.SecurityUtils;
//import com.rahul.transaction_service.service.TransferService;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Optional;
//
//@RestController
//@RequestMapping("/api")
//@RequiredArgsConstructor
//public class TransferController {
//
//    private final TransferService transferService;
//    private final SecurityUtils securityUtils;
//    private final IdempotencyService idempotencyService;
//
//    @PostMapping("/transfers")
//    public ResponseEntity<TransactionResponse> transfer(
//            @Valid @RequestBody TransferRequest request,
//            @RequestHeader("Idempotency-Key") String idempotencyKey) {
//
//        // STEP 2.2 — check before processing
//        Optional<IdempotencyKey> existing = idempotencyService.checkExisting(idempotencyKey);
//        if (existing.isPresent()) {
//            IdempotencyKey record = existing.get();
//            TransactionResponse cachedResponse =
//                    idempotencyService.deserializeResponse(record.getResponseBody(), TransactionResponse.class);
//            return ResponseEntity.status(record.getResponseStatus()).body(cachedResponse);
//        }
//
//        Long callerUserId = securityUtils.getCurrentUserId();
//        Transaction txn = transferService.transfer(request, callerUserId);
//        TransactionResponse response = toResponse(txn);
//
//        // STEP 2.3 — save the key + response so a future duplicate is caught
//        idempotencyService.save(idempotencyKey, request, HttpStatus.CREATED.value(), response);
//
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }
//
//    private TransactionResponse toResponse(Transaction txn) {
//        return TransactionResponse.builder()
//                .id(txn.getId())
//                .fromWalletId(txn.getFromWalletId())
//                .toWalletId(txn.getToWalletId())
//                .amount(txn.getAmount())
//                .type(txn.getType())
//                .status(txn.getStatus())
//                .createdAt(txn.getCreatedAt())
//                .build();
//    }
//}

package com.rahul.transaction_service.controller;

import com.rahul.transaction_service.Idempotency.IdempotencyKey;
import com.rahul.transaction_service.Idempotency.IdempotencyService;
import com.rahul.transaction_service.Idempotency.IdempotencyStatus;
import com.rahul.transaction_service.dto.transfer.TransactionResponse;
import com.rahul.transaction_service.dto.transfer.TransferRequest;
import com.rahul.transaction_service.entity.Transaction;
import com.rahul.transaction_service.security.SecurityUtils;
import com.rahul.transaction_service.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final SecurityUtils securityUtils;
    private final IdempotencyService idempotencyService;

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // STEP 2.4 — try to claim this key FIRST, before any business logic
        try {
            //try to save key
            idempotencyService.registerProcessing(idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            // Someone already claimed this key — look at their record
            IdempotencyKey existing = idempotencyService.checkExisting(idempotencyKey)
                    .orElseThrow(); // must exist, since the insert just failed on its uniqueness

            if (!idempotencyService.matchesOriginalRequest(existing, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key already used with a different request payload");
            }

            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                // The other request hasn't finished yet — don't risk a double-transfer
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A request with this Idempotency-Key is already being processed");
            }

            // COMPLETED — safe to return the cached response
            TransactionResponse cachedResponse =
                    idempotencyService.deserializeResponse(existing.getResponseBody(), TransactionResponse.class);
            return ResponseEntity.status(existing.getResponseStatus()).body(cachedResponse);
        }

        // We successfully reserved the key — safe to proceed, no one else can be here concurrently
        Long callerUserId = securityUtils.getCurrentUserId();
        Transaction txn = transferService.transfer(request, callerUserId);
        TransactionResponse response = toResponse(txn);

        idempotencyService.complete(idempotencyKey, HttpStatus.CREATED.value(), response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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