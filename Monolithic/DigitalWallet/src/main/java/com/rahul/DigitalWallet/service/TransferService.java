package com.rahul.DigitalWallet.service;

import com.rahul.DigitalWallet.dto.transfer.TransferRequest;
import com.rahul.DigitalWallet.entity.*;
import com.rahul.DigitalWallet.exception.InsufficientBalanceException;
import com.rahul.DigitalWallet.exception.ResourceNotFoundException;
import com.rahul.DigitalWallet.repository.TransactionRepository;
import com.rahul.DigitalWallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Moves money between two wallets. Both legs succeed or both roll back —
     * Transactional is what guarantees that here.
     *
     * NOTE: this is the single-database, ACID version of a transfer. Once the
     * system is split across services (Phase 5+), a plain @Transactional can no
     * longer span both wallets, and this logic gets replaced by a Saga — that's
     * the whole point of Phase 11. Don't skip understanding *why* this simple
     * version works before moving on.
     */
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
}