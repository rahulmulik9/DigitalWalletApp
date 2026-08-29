package com.rahul.transaction_service.service;

import com.rahul.transaction_service.client.AccountServiceClient;
import com.rahul.transaction_service.dto.beneficiary.BeneficiaryRequest;
import com.rahul.transaction_service.dto.wallet.WalletResponse;
import com.rahul.transaction_service.entity.Beneficiary;
import com.rahul.transaction_service.exception.ResourceNotFoundException;
import com.rahul.transaction_service.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountServiceClient accountServiceClient;

//    @Transactional
//    public Beneficiary create(BeneficiaryRequest request, String callerEmail) {
//        User user = userRepository.findByEmail(callerEmail)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
//
//        Wallet beneficiaryWallet = walletRepository.findById(request.getBeneficiaryWalletId())
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Beneficiary wallet not found: " + request.getBeneficiaryWalletId()));
//
//        if (user.getWallet() != null && user.getWallet().getId().equals(beneficiaryWallet.getId())) {
//            throw new IllegalArgumentException("You cannot add your own wallet as a beneficiary");
//        }
//
//        if (beneficiaryRepository.existsByUserIdAndBeneficiaryWalletId(user.getId(), beneficiaryWallet.getId())) {
//            throw new IllegalArgumentException("Beneficiary already exists");
//        }
//
//        Beneficiary beneficiary = Beneficiary.builder()
//                .user(user)
//                .beneficiaryWallet(beneficiaryWallet)
//                .nickname(request.getNickname())
//                .build();
//
//        return beneficiaryRepository.save(beneficiary);
//    }

    @Transactional
    public Beneficiary create(BeneficiaryRequest request, Long callerUserId, Long callerWalletId) {

        WalletResponse beneficiaryWallet = accountServiceClient.getWallet(request.getBeneficiaryWalletId());
        // getWallet() throws WalletNotFoundException if not found — caught by GlobalExceptionHandler

        if (callerWalletId != null && callerWalletId.equals(beneficiaryWallet.getId())) {
            throw new IllegalArgumentException("You cannot add your own wallet as a beneficiary");
        }

        if (beneficiaryRepository.existsByUserIdAndBeneficiaryWalletId(callerUserId, beneficiaryWallet.getId())) {
            throw new IllegalArgumentException("Beneficiary already exists");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .userId(callerUserId)
                .beneficiaryWalletId(beneficiaryWallet.getId())
                .nickname(request.getNickname())
                .build();

        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional(readOnly = true)
    public List<Beneficiary> getAll(Long callerUserId) {
        return beneficiaryRepository.findByUserIdOrderByCreatedAtDesc(callerUserId);
    }

    @Transactional
    public void delete(Long beneficiaryId, Long callerUserId) {
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndUserId(beneficiaryId, callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found: " + beneficiaryId));

        beneficiaryRepository.delete(beneficiary);
    }
}