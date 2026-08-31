package com.rahul.transaction_service.client;

import com.rahul.transaction_service.dto.wallet.WalletResponse;
import com.rahul.transaction_service.exception.InsufficientBalanceException;
import com.rahul.transaction_service.exception.WalletNotFoundException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AccountServiceClient {

//    private final RestTemplate restTemplate;
//
//    @Value("${account-service.url}")
//    private String accountServiceUrl;
//
//    public WalletResponse getWallet(Long walletId) {
//        try {
//            return restTemplate.getForObject(
//                    accountServiceUrl + "/api/wallets/" + walletId + "/internal",
//                    WalletResponse.class
//            );
//        } catch (HttpClientErrorException.NotFound e) {
//            throw new WalletNotFoundException("Wallet not found: " + walletId);
//        }
//    }
//
//    public void debit(Long walletId, BigDecimal amount) {
//        try {
//            restTemplate.postForObject(
//                    accountServiceUrl + "/api/wallets/" + walletId + "/debit",
//                    new AmountPayload(amount),
//                    Void.class
//            );
//        } catch (HttpClientErrorException.NotFound e) {
//            throw new WalletNotFoundException("Wallet not found: " + walletId);
//        } catch (HttpClientErrorException.Conflict e) {
//            throw new com.rahul.transaction_service.exception.InsufficientBalanceException(
//                    "Insufficient balance in wallet: " + walletId);
//        }
//    }
//
//    public void credit(Long walletId, BigDecimal amount) {
//        try {
//            restTemplate.postForObject(
//                    accountServiceUrl + "/api/wallets/" + walletId + "/credit",
//                    new AmountPayload(amount),
//                    Void.class
//            );
//        } catch (HttpClientErrorException.NotFound e) {
//            throw new WalletNotFoundException("Wallet not found: " + walletId);
//        }
//    }
//
//    private record AmountPayload(BigDecimal amount) {}
    // Void.class above: RestTemplate's postForObject() is generic and needs
    // SOME Class token for the response type, even when we don't care about
    // the body. Void.class is the convention meaning "deserialize nothing,
    // return null". Feign's `void debit(...)` below handles this natively -
    // no token needed, the method just returns void directly.

    //new feign client code
    private final AccountClient accountClient;

    public WalletResponse getWallet(Long walletId) {
        try {
            return accountClient.getWallet(walletId);
        } catch (FeignException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
    }

    public void debit(Long walletId, BigDecimal amount) {
        try {
            accountClient.debit(walletId, new AccountClient.AmountRequest(amount));
        } catch (FeignException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        } catch (FeignException.Conflict e) {
            throw new InsufficientBalanceException("Insufficient balance in wallet: " + walletId);
        }
    }

    public void credit(Long walletId, BigDecimal amount) {
        try {
            accountClient.credit(walletId, new AccountClient.AmountRequest(amount));
        } catch (FeignException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
    }
}