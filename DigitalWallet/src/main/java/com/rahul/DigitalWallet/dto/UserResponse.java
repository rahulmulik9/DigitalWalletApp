package com.rahul.DigitalWallet.dto;

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
    private WalletSummary wallet;
}

