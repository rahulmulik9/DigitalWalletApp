package com.rahul.DigitalWallet.dto.user;

import com.rahul.DigitalWallet.dto.wallet.WalletInfo;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String fullName;
    private String email;
    private LocalDateTime createdAt;
    private WalletInfo wallet;
}

