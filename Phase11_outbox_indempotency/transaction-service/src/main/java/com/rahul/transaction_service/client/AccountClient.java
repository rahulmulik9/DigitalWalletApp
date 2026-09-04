package com.rahul.transaction_service.client;

import com.rahul.transaction_service.dto.wallet.WalletResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "account-service")
public interface AccountClient {

    @GetMapping("/api/wallets/{walletId}/internal")
    WalletResponse getWallet(@PathVariable("walletId") Long walletId);

    @PostMapping("/api/wallets/{walletId}/debit")
    void debit(@PathVariable("walletId") Long walletId, @RequestBody AmountRequest amount);

    @PostMapping("/api/wallets/{walletId}/credit")
    void credit(@PathVariable("walletId") Long walletId, @RequestBody AmountRequest amount);

    record AmountRequest(BigDecimal amount) {}
}