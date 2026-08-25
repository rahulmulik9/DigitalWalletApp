package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.LoginRequest;
import com.rahul.DigitalWallet.dto.LoginResponse;
import com.rahul.DigitalWallet.dto.RegisterRequest;
import com.rahul.DigitalWallet.entity.User;
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

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse user = userService.login(request);
        return ResponseEntity.ok(user);
    }
}