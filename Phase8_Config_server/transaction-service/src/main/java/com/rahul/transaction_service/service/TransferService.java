package com.rahul.transaction_service.service;

import com.rahul.transaction_service.client.AccountServiceClient;
import com.rahul.transaction_service.dto.transfer.TransferRequest;
import com.rahul.transaction_service.dto.wallet.WalletResponse;
import com.rahul.transaction_service.entity.*;
import com.rahul.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    /**
     * Moves money between two wallets. Both legs succeed or both roll back —
     * Transactional is what guarantees that here.
     *
     * NOTE: this is the single-database, ACID version of a transfer. Once the
     * system is split across services (Phase 5+), a plain @Transactional can no
     * longer span both wallets, and this logic gets replaced by a Saga — that's
     * the whole point of Phase 11. Don't skip understanding *why* this simple
     * version works before moving on.

    @Transactional
    public Transaction transfer(TransferRequest request, String callerEmail) {   // CHANGED — added callerEmail
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Wallet fromWallet = walletRepository.findById(request.getFromWalletId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source wallet not found: " + request.getFromWalletId()));

        if (!fromWallet.getUser().getEmail().equals(callerEmail)) {   // NEW
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: source wallet does not belong to you");
        }

        Wallet toWallet = walletRepository.findById(request.getToWalletId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Destination wallet not found: " + request.getToWalletId()));

        if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance in wallet: " + fromWallet.getId());
        }

        fromWallet.setBalance(fromWallet.getBalance().subtract(request.getAmount()));
        toWallet.setBalance(toWallet.getBalance().add(request.getAmount()));

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        Transaction txn = Transaction.builder()
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .build();


        LedgerEntry debit = LedgerEntry.builder()
                .wallet(fromWallet).transaction(txn).amount(request.getAmount())
                .type(LedgerEntryType.DEBIT).description("Transfer to wallet " + toWallet.getId()).build();
        LedgerEntry credit = LedgerEntry.builder()
                .wallet(toWallet).transaction(txn).amount(request.getAmount())
                .type(LedgerEntryType.CREDIT).description("Transfer from wallet " + fromWallet.getId()).build();
        txn.addLedgerEntry(debit);
        txn.addLedgerEntry(credit);
        txn.setStatus(TransactionStatus.SUCCESS);

        return transactionRepository.save(txn);
    }
     */
    @Transactional
    public Transaction transfer(TransferRequest request, Long callerUserId) {
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        WalletResponse fromWallet = accountServiceClient.getWallet(request.getFromWalletId());

        if (!fromWallet.getUserId().equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: source wallet does not belong to you");
        }

        WalletResponse toWallet = accountServiceClient.getWallet(request.getToWalletId());

        // debit/credit calls hit Account Service, which owns balance validation
        // and mutation. If balance is insufficient, Account Service returns 409
        // and AccountServiceClient throws InsufficientBalanceException here.
        accountServiceClient.debit(fromWallet.getId(), request.getAmount());
        accountServiceClient.credit(toWallet.getId(), request.getAmount());

        Transaction txn = Transaction.builder()
                .fromWalletId(fromWallet.getId())
                .toWalletId(toWallet.getId())
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .build();

        LedgerEntry debit = LedgerEntry.builder()
                .walletId(fromWallet.getId()).transaction(txn).amount(request.getAmount())
                .type(LedgerEntryType.DEBIT).description("Transfer to wallet " + toWallet.getId()).build();
        LedgerEntry credit = LedgerEntry.builder()
                .walletId(toWallet.getId()).transaction(txn).amount(request.getAmount())
                .type(LedgerEntryType.CREDIT).description("Transfer from wallet " + fromWallet.getId()).build();
        txn.addLedgerEntry(debit);
        txn.addLedgerEntry(credit);
        txn.setStatus(TransactionStatus.SUCCESS);

        return transactionRepository.save(txn);
    }
}


/*
 * ============================================================
 * BEFORE (Phase 4 — single DB, single @Transactional)
 * ============================================================
 *
 * 1. Client
 *       |
 *       v
 * 2. TransferService
 *       |
 *       | debit source wallet (local)
 *       | credit destination wallet (local)
 *       | create transaction + ledger entries (local)
 *       v
 * 3. ONE database, ONE @Transactional
 *       |
 *       v
 * 4. All-or-nothing commit/rollback — guaranteed by the DB
 *
 * ============================================================
 * AFTER (Phase 5 — two services, two databases)
 * ============================================================
 *
 * 1. Client
 *       |
 *       v
 * 2. Transaction Service
 *       |
 *       | debit source wallet
 *       v
 * 3. Account Service
 *       |
 *       v
 * 4. account_db
 *       |
 *       | success
 *       v
 * 5. Transaction Service
 *       |
 *       | credit destination wallet
 *       v
 * 6. Account Service
 *       |
 *       v
 * 7. account_db
 *       |
 *       | success
 *       v
 * 8. Transaction Service
 *       |
 *       | create transaction + ledger entries
 *       v
 * 9. transaction_db
 *
 * NOTE: @Transactional on this method now ONLY protects step 9
 * (the local transaction_db write). It does NOT make steps 2-7
 * atomic — if debit (step 2-4) succeeds and credit (step 5-7)
 * fails, the source wallet is already short ₹X with nothing to
 * roll it back. This is the exact problem Phase 11 (Saga +
 * Compensation + Idempotency + Outbox) exists to solve. We are
 * NOT fixing it here — this is the deliberate lesson of Phase 5.
 * ============================================================
 */