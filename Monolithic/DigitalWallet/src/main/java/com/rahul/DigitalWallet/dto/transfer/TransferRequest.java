package com.rahul.DigitalWallet.dto.transfer;

import com.rahul.DigitalWallet.validator.PositiveAmount;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull(message = "Source wallet is required")
    private Long fromWalletId;

    @NotNull(message = "Destination wallet is required")
    private Long toWalletId;

    @PositiveAmount
    private BigDecimal amount;
}