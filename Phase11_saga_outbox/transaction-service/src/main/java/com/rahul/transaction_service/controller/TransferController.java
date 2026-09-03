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

        Optional<IdempotencyKey> existing = idempotencyService.checkExisting(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();

            // NEW — Gap B: reject if the same key is reused for a different request
            if (!idempotencyService.matchesOriginalRequest(record, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key already used with a different request payload");
            }

            TransactionResponse cachedResponse =
                    idempotencyService.deserializeResponse(record.getResponseBody(), TransactionResponse.class);
            return ResponseEntity.status(record.getResponseStatus()).body(cachedResponse);
        }

        Long callerUserId = securityUtils.getCurrentUserId();
        Transaction txn = transferService.transfer(request, callerUserId);
        TransactionResponse response = toResponse(txn);

        // NEW — Gap A: catch the race — DB's unique constraint is the real guard,
        // this try-catch just handles it gracefully instead of crashing with a 500
        try {
            idempotencyService.save(idempotencyKey, request, HttpStatus.CREATED.value(), response);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent request with the same key won the race and saved first.
            // The transfer we just did is now effectively a duplicate execution —
            // this is the one real limitation of choosing "process, then save" ordering.
            // Returning our own result here is still safe: the OTHER request's transfer
            // already happened too, so this pattern doesn't fully cover simultaneous funds
            // movement — mitigated in Step 3 by combining this with the Saga's own dedup.
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

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