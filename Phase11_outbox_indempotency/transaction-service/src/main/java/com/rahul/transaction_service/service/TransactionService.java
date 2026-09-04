package com.rahul.transaction_service.service;

import com.rahul.transaction_service.client.AccountServiceClient;
import com.rahul.transaction_service.entity.Transaction;
import com.rahul.transaction_service.repository.TransactionRepository;
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
    private final AccountServiceClient accountServiceClient;

    @Transactional(readOnly = true)
    public Page<Transaction> getHistory(Long walletId, Pageable pageable) {
        accountServiceClient.getWallet(walletId); // throws WalletNotFoundException if missing
        return transactionRepository.findHistory(walletId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getFilteredHistory(Long walletId, LocalDateTime fromDate, LocalDateTime toDate,
                                                BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        accountServiceClient.getWallet(walletId); // throws WalletNotFoundException if missing
        return transactionRepository.findFilteredHistory(
                walletId, fromDate, toDate, minAmount, maxAmount, pageable);
    }
}