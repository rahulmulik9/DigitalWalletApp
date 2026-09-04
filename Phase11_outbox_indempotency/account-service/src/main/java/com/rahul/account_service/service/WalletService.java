package com.rahul.account_service.service;

import com.rahul.account_service.entity.Wallet;
import com.rahul.account_service.exception.InsufficientBalanceException;
import com.rahul.account_service.exception.ResourceNotFoundException;
import com.rahul.account_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    public Wallet getWallet(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }

    public List<Wallet> getAllWallets() {
        return walletRepository.findAll();
    }

    @Transactional
    public Wallet deposit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
        return walletRepository.save(wallet);
        /* We are not saving transaction as per older method monolithic pattern (update wallet->save translation->save leader)
         Transaction Service already calls Account Service for transfers*
        A reverse call (Account Service calling Transaction Service) creates a circular dependency between the two services */
    }

    @Transactional
    public Wallet withdraw(Wallet wallet, BigDecimal amount) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in wallet: " + wallet.getId());
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        return walletRepository.save(wallet);
    }
}