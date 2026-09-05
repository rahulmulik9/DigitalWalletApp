package com.rahul.account_service.controller;

import com.rahul.account_service.dto.user.LoginRequest;
import com.rahul.account_service.dto.user.LoginResponse;
import com.rahul.account_service.dto.user.RegisterRequest;
import com.rahul.account_service.dto.user.UserResponse;
import com.rahul.account_service.dto.wallet.WalletInfo;
import com.rahul.account_service.entity.User;
import com.rahul.account_service.entity.Wallet;
import com.rahul.account_service.security.JwtTokenProvider;
import com.rahul.account_service.security.TokenBlacklistService;
import com.rahul.account_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    /*
     @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    The Problem: When User has @OneToOne Wallet wallet and Wallet has @OneToOne User user, returning User causes infinite recursion because Jackson serializes: User → Wallet → User → Wallet → ... forever, causing StackOverflowError.
    The Solution: Create a separate WalletSummary DTO with  balance, currency (NO user field), then map User → UserResponse with WalletSummary inside. This breaks the cycle and prevents recursion.
    Key Rule: Never include the back-reference in your DTO — if User has Wallet, make sure Wallet's DTO doesn't point back to User.
    */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse user = userService.login(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        if (token != null && jwtTokenProvider.isTokenValid(token)) {
            long expirationTime = jwtTokenProvider.getExpirationTime(token);
            tokenBlacklistService.blacklist(token, expirationTime);
        }
        return ResponseEntity.noContent().build();
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private UserResponse toResponse(User user) {

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .wallet(toWalletSummary(user.getWallet()))
                .build();
    }

    private WalletInfo toWalletSummary(Wallet wallet) {

        return WalletInfo.builder()
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }
}