package com.rahul.DigitalWallet.service;

import com.rahul.DigitalWallet.entity.Transaction;
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
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public Page<Transaction> getHistory(Long walletId, LocalDateTime fromDate, LocalDateTime toDate,
                                        BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        if (!walletRepository.existsById(walletId)) {
            throw new ResourceNotFoundException("Wallet not found: " + walletId);
        }
        return transactionRepository.findHistory(walletId, fromDate, toDate, minAmount, maxAmount, pageable);
    }
}