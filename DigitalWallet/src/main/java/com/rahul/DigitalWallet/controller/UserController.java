package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.*;
import com.rahul.DigitalWallet.entity.User;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.service.UserService;
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

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .wallet(toWalletSummary(user.getWallet()))
                .build();
    }

    private WalletSummary toWalletSummary(Wallet wallet) {
        return WalletSummary.builder()
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse user = userService.login(request);
        return ResponseEntity.ok(user);
    }
}