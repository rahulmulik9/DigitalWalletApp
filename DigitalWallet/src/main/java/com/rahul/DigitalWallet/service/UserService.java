package com.rahul.DigitalWallet.service;

import com.rahul.DigitalWallet.dto.LoginRequest;
import com.rahul.DigitalWallet.dto.RegisterRequest;
import com.rahul.DigitalWallet.entity.Role;
import com.rahul.DigitalWallet.entity.User;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.exception.DuplicateEmailException;
import com.rahul.DigitalWallet.exception.ResourceNotFoundException;
import com.rahul.DigitalWallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))   // CHANGED — was plaintext
                .role(Role.CUSTOMER)                                       // NEW — default role
                .build();

        // Every user gets a wallet the moment they register.
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .currency("INR")
                .build();

        user.setWallet(wallet);

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found for this email"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        return user;
    }
}