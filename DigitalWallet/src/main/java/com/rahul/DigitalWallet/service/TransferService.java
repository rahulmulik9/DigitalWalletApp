package com.rahul.DigitalWallet.service;

import com.rahul.DigitalWallet.dto.TransferRequest;
import com.rahul.DigitalWallet.entity.*;
import com.rahul.DigitalWallet.exception.InsufficientBalanceException;
import com.rahul.DigitalWallet.exception.ResourceNotFoundException;
import com.rahul.DigitalWallet.repository.TransactionRepository;
import com.rahul.DigitalWallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    public Transaction transfer(TransferRequest request) {
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Wallet fromWallet = walletRepository.findById(request.getFromWalletId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source wallet not found: " + request.getFromWalletId()));

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
                .status(TransactionStatus.SUCCESS)
                .build();

        return transactionRepository.save(txn);
    }

    public Page<Transaction> getHistory(Long walletId, Pageable pageable) {
       // return transactionRepository.findAllByWalletId(walletId, pageable);
        Page<Transaction> transactions =  transactionRepository.findAllByWalletId(walletId, pageable);

//        // Get first Transaction
//        Transaction transaction = transactions.getContent().get(1);
//
//        // Get wallets from that Transaction
//        Wallet fromWallet = transaction.getFromWallet();
//        Wallet toWallet = transaction.getToWallet();
//
//        Long id = fromWallet.getId();
//        User user = fromWallet.getUser();
//        BigDecimal balance = fromWallet.getBalance();
//        String currency = fromWallet.getCurrency();
//        Long version = fromWallet.getVersion();
//        LocalDateTime createdAt = fromWallet.getCreatedAt();
//        LocalDateTime updatedAt = fromWallet.getUpdatedAt();

        // Put breakpoint here
        return transactions;
    }
}