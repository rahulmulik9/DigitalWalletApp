package com.rahul.transaction_service.dto.beneficiary;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BeneficiaryResponse {
    private Long id;
    private Long beneficiaryWalletId;
    private String nickname;
    private LocalDateTime createdAt;
}