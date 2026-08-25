package com.rahul.DigitalWallet.service;

import com.rahul.DigitalWallet.entity.Transaction;
import com.rahul.DigitalWallet.entity.TransactionStatus;
import com.rahul.DigitalWallet.entity.TransactionType;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.exception.InsufficientBalanceException;
import com.rahul.DigitalWallet.exception.ResourceNotFoundException;
import com.rahul.DigitalWallet.repository.TransactionRepository;
import com.rahul.DigitalWallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public Wallet getWallet(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }
    @Transactional
    public Wallet deposit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction txn = Transaction.builder()
                .toWallet(wallet)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(txn);

        return wallet;
    }

    @Transactional
    public Wallet withdraw(Wallet wallet, BigDecimal amount) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in wallet: " + wallet.getId());
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction txn = Transaction.builder()
                .fromWallet(wallet)
                .amount(amount)
                .type(TransactionType.WITHDRAW)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(txn);

        return wallet;
    }
}
