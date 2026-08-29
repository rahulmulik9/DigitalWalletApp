package com.rahul.transaction_service.dto.beneficiary;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BeneficiaryRequest {
    @NotNull(message = "Beneficiary wallet is required")
    private Long beneficiaryWalletId;

    @NotNull(message = "Nickname is required")
    @Size(min = 1, max = 100, message = "Nickname must be between 1 and 100 characters")
    private String nickname;
}