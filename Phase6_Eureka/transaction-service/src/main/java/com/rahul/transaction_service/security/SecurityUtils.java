package com.rahul.transaction_service.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final HttpServletRequest request;

    public Long getCurrentUserId() {
        return (Long) request.getAttribute("userId");
    }

    public Long getCurrentWalletId() {
        return (Long) request.getAttribute("walletId");
    }

    public boolean isCurrentUserAdmin() {
        String role = (String) request.getAttribute("role");
        return "ADMIN".equals(role);
    }
}