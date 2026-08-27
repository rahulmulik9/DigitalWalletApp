package com.rahul.transaction_service.client;

import com.rahul.transaction_service.dto.wallet.WalletResponse;
import com.rahul.transaction_service.exception.WalletNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AccountServiceClient {

    private final RestTemplate restTemplate;

    @Value("${account-service.url}")
    private String accountServiceUrl;

    public WalletResponse getWallet(Long walletId) {
        try {
            return restTemplate.getForObject(
                    accountServiceUrl + "/api/wallets/" + walletId,
                    WalletResponse.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
    }

    public void debit(Long walletId, BigDecimal amount) {
        try {
            restTemplate.postForObject(
                    accountServiceUrl + "/api/wallets/" + walletId + "/debit",
                    new AmountPayload(amount),
                    Void.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new com.rahul.transaction_service.exception.InsufficientBalanceException(
                    "Insufficient balance in wallet: " + walletId);
        }
    }

    public void credit(Long walletId, BigDecimal amount) {
        try {
            restTemplate.postForObject(
                    accountServiceUrl + "/api/wallets/" + walletId + "/credit",
                    new AmountPayload(amount),
                    Void.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
    }

    private record AmountPayload(BigDecimal amount) {}
}