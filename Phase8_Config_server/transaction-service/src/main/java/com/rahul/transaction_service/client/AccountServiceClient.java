package com.rahul.transaction_service.client;

import com.rahul.transaction_service.dto.wallet.WalletResponse;
import com.rahul.transaction_service.exception.AccountServiceUnavailableException;
import com.rahul.transaction_service.exception.InsufficientBalanceException;
import com.rahul.transaction_service.exception.WalletNotFoundException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AccountServiceClient {

    /// ======================= Using Rest controller where usl value given hardcoded ==================================================

/*    private final RestTemplate restTemplate;

    @Value("${account-service.url}")
    private String accountServiceUrl;

    public WalletResponse getWallet(Long walletId) {
        try {
            return restTemplate.getForObject(
                    accountServiceUrl + "/api/wallets/" + walletId + "/internal",
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
     Void.class above: RestTemplate's postForObject() is generic and needs
     SOME Class token for the response type, even when we don't care about
     the body. Void.class is the convention meaning "deserialize nothing,
     return null". Feign's `void debit(...)` below handles this natively -
     no token needed, the method just returns void directly.

*/

    /// ================================== Feign client code : URL value taken from eureka server for service name which was mentioned in accountClient
  /*  private final AccountClient accountClient;

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
   */

    /// ================================= Added CircuitBreaker : CircuitBreaker properties are defined in config (github)

    private final AccountClient accountClient;

    @CircuitBreaker(name = "accountService", fallbackMethod = "getWalletFallback")
    public WalletResponse getWallet(Long walletId) {
        try {
            return accountClient.getWallet(walletId);
        } catch (FeignException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
    }

    private WalletResponse getWalletFallback(Long walletId, Throwable cause) {
        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable for wallet " + walletId, cause);
    }



    @CircuitBreaker(name = "accountService", fallbackMethod = "debitFallback")
    public void debit(Long walletId, BigDecimal amount) {
        try {
            accountClient.debit(walletId, new AccountClient.AmountRequest(amount));
        } catch (FeignException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        } catch (FeignException.Conflict e) {
            throw new InsufficientBalanceException("Insufficient balance in wallet: " + walletId);
        }
    }

    private void debitFallback(Long walletId, BigDecimal amount, Throwable cause) {
        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable — debit failed for wallet " + walletId, cause);
    }



    // KNOWN GAP (documented since Phase 5, not fixed here): if debit() above
    // succeeds but credit() below fails/opens, the source wallet has already
    // been debited with no compensating refund and no Transaction record
    // created. This circuit breaker makes that failure fast and clean
    // instead of hanging — it does NOT make the transfer atomic. Real fix
    // is Phase 11's Saga/compensation logic.
    @CircuitBreaker(name = "accountService", fallbackMethod = "creditFallback")
    public void credit(Long walletId, BigDecimal amount) {
        try {
            accountClient.credit(walletId, new AccountClient.AmountRequest(amount));
        } catch (FeignException.NotFound e) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
    }

    private void creditFallback(Long walletId, BigDecimal amount, Throwable cause) {
        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable — credit failed for wallet " + walletId, cause);
    }


}