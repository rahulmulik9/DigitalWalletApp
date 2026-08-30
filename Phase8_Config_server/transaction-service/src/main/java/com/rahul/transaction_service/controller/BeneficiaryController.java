package com.rahul.transaction_service.controller;

import com.rahul.transaction_service.dto.beneficiary.BeneficiaryRequest;
import com.rahul.transaction_service.dto.beneficiary.BeneficiaryResponse;
import com.rahul.transaction_service.entity.Beneficiary;
import com.rahul.transaction_service.security.SecurityUtils;
import com.rahul.transaction_service.service.BeneficiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> create(@Valid @RequestBody BeneficiaryRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        Long walletId = securityUtils.getCurrentWalletId();
        Beneficiary beneficiary = beneficiaryService.create(request, userId, walletId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(beneficiary));
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getAll() {
        Long userId = securityUtils.getCurrentUserId();
        List<BeneficiaryResponse> response = beneficiaryService.getAll(userId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        beneficiaryService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private BeneficiaryResponse toResponse(Beneficiary b) {
        return BeneficiaryResponse.builder()
                .id(b.getId())
                .beneficiaryWalletId(b.getBeneficiaryWalletId())
                .nickname(b.getNickname())
                .createdAt(b.getCreatedAt())
                .build();
    }
}