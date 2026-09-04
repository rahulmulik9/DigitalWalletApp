package com.rahul.account_service.controller;

import com.rahul.account_service.dto.user.LoginResponse;
import com.rahul.account_service.dto.user.RefreshTokenRequest;
import com.rahul.account_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = userService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}