package com.rahul.account_service.service;

import com.rahul.account_service.entity.Wallet;
import com.rahul.account_service.exception.InsufficientBalanceException;
import com.rahul.account_service.exception.ResourceNotFoundException;
import com.rahul.account_service.outbox.CreditResultPayload;
import com.rahul.account_service.outbox.DebitResultPayload;
import com.rahul.account_service.outbox.TransferInitiatedPayload;
import com.rahul.account_service.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSagaService {

    private final WalletService walletService;
    private final OutboxService outboxService;

    @Transactional
    public void handleTransferInitiated(TransferInitiatedPayload payload) {
        try {
            Wallet fromWallet = walletService.getWallet(payload.getFromWalletId());
            walletService.withdraw(fromWallet, payload.getAmount());

            outboxService.saveEvent(
                    "TRANSACTION",
                    String.valueOf(payload.getTransactionId()),
                    "DebitCompleted",
                    DebitResultPayload.builder()
                            .transactionId(payload.getTransactionId())
                            .fromWalletId(payload.getFromWalletId())
                            .toWalletId(payload.getToWalletId())
                            .amount(payload.getAmount())
                            .timestamp(Instant.now())
                            .build()
            );

            log.info("Debit succeeded for transactionId={}", payload.getTransactionId());

        } catch (InsufficientBalanceException | ResourceNotFoundException ex) {
            outboxService.saveEvent(
                    "TRANSACTION",
                    String.valueOf(payload.getTransactionId()),
                    "DebitFailed",
                    DebitResultPayload.builder()
                            .transactionId(payload.getTransactionId())
                            .fromWalletId(payload.getFromWalletId())
                            .toWalletId(payload.getToWalletId())
                            .amount(payload.getAmount())
                            .reason(ex.getMessage())
                            .timestamp(Instant.now())
                            .build()
            );

            log.warn("Debit failed for transactionId={} reason={}", payload.getTransactionId(), ex.getMessage());
        }
    }


    @Transactional
    public void handleDebitCompleted(DebitResultPayload payload) {
        try {
            Wallet toWallet = walletService.getWallet(payload.getToWalletId());
            walletService.deposit(toWallet, payload.getAmount());

            outboxService.saveEvent(
                    "TRANSACTION",
                    String.valueOf(payload.getTransactionId()),
                    "CreditCompleted",
                    CreditResultPayload.builder()
                            .transactionId(payload.getTransactionId())
                            .fromWalletId(payload.getFromWalletId())
                            .toWalletId(payload.getToWalletId())
                            .amount(payload.getAmount())
                            .timestamp(Instant.now())
                            .build()
            );

            log.info("Credit succeeded for transactionId={}", payload.getTransactionId());

        } catch (ResourceNotFoundException ex) {
            outboxService.saveEvent(
                    "TRANSACTION",
                    String.valueOf(payload.getTransactionId()),
                    "CreditFailed",
                    CreditResultPayload.builder()
                            .transactionId(payload.getTransactionId())
                            .fromWalletId(payload.getFromWalletId())
                            .toWalletId(payload.getToWalletId())
                            .amount(payload.getAmount())
                            .reason(ex.getMessage())
                            .timestamp(Instant.now())
                            .build()
            );

            log.warn("Credit failed for transactionId={} reason={}", payload.getTransactionId(), ex.getMessage());
        }
    }
}