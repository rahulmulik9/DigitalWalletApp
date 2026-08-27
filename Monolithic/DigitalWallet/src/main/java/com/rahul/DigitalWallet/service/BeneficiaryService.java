package com.rahul.DigitalWallet.service;

import com.rahul.DigitalWallet.dto.beneficiary.BeneficiaryRequest;
import com.rahul.DigitalWallet.entity.Beneficiary;
import com.rahul.DigitalWallet.entity.User;
import com.rahul.DigitalWallet.entity.Wallet;
import com.rahul.DigitalWallet.exception.ResourceNotFoundException;
import com.rahul.DigitalWallet.repository.BeneficiaryRepository;
import com.rahul.DigitalWallet.repository.UserRepository;
import com.rahul.DigitalWallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public Beneficiary create(BeneficiaryRequest request, String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Wallet beneficiaryWallet = walletRepository.findById(request.getBeneficiaryWalletId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Beneficiary wallet not found: " + request.getBeneficiaryWalletId()));

        if (user.getWallet() != null && user.getWallet().getId().equals(beneficiaryWallet.getId())) {
            throw new IllegalArgumentException("You cannot add your own wallet as a beneficiary");
        }

        if (beneficiaryRepository.existsByUserIdAndBeneficiaryWalletId(user.getId(), beneficiaryWallet.getId())) {
            throw new IllegalArgumentException("Beneficiary already exists");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .user(user)
                .beneficiaryWallet(beneficiaryWallet)
                .nickname(request.getNickname())
                .build();

        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional(readOnly = true)
    public List<Beneficiary> getAll(String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return beneficiaryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void delete(Long beneficiaryId, String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Beneficiary beneficiary = beneficiaryRepository.findByIdAndUserId(beneficiaryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found: " + beneficiaryId));

        beneficiaryRepository.delete(beneficiary);
    }
}