package com.rahul.account_service.service;


import com.rahul.account_service.dto.user.LoginRequest;
import com.rahul.account_service.dto.user.LoginResponse;
import com.rahul.account_service.dto.user.RegisterRequest;
import com.rahul.account_service.entity.Role;
import com.rahul.account_service.entity.User;
import com.rahul.account_service.entity.Wallet;
import com.rahul.account_service.exception.DuplicateEmailException;
import com.rahul.account_service.exception.ResourceNotFoundException;
import com.rahul.account_service.repository.UserRepository;
import com.rahul.account_service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public User register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .currency("INR")
                .build();

        user.setWallet(wallet);

        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        Long walletId = user.getWallet() != null ? user.getWallet().getId() : null;

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .walletId(walletId)
                .build();
    }

    public LoginResponse refreshAccessToken(String refreshToken) {
        if (!jwtTokenProvider.isTokenValid(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        String email = jwtTokenProvider.extractEmail(refreshToken);

        // confirm the user still exists — covers the case where an account
        // was deleted/disabled after the refresh token was issued
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)   // same refresh token, unchanged — only access token rotates
                .tokenType("Bearer")
                .build();
    }
}