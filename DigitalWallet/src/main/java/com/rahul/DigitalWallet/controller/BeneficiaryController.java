package com.rahul.DigitalWallet.controller;

import com.rahul.DigitalWallet.dto.beneficiary.BeneficiaryRequest;
import com.rahul.DigitalWallet.dto.beneficiary.BeneficiaryResponse;
import com.rahul.DigitalWallet.entity.Beneficiary;
import com.rahul.DigitalWallet.security.SecurityUtils;
import com.rahul.DigitalWallet.service.BeneficiaryService;
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
        String email = securityUtils.getCurrentUserEmail();
        Beneficiary beneficiary = beneficiaryService.create(request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(beneficiary));
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getAll() {
        String email = securityUtils.getCurrentUserEmail();
        List<BeneficiaryResponse> response = beneficiaryService.getAll(email)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String email = securityUtils.getCurrentUserEmail();
        beneficiaryService.delete(id, email);
        return ResponseEntity.noContent().build();
    }

    private BeneficiaryResponse toResponse(Beneficiary b) {
        return BeneficiaryResponse.builder()
                .id(b.getId())
                .beneficiaryWalletId(b.getBeneficiaryWallet().getId())
                .nickname(b.getNickname())
                .createdAt(b.getCreatedAt())
                .build();
    }
}