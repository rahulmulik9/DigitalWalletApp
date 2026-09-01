package com.rahul.transaction_service.repository;

import com.rahul.transaction_service.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Beneficiary> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndBeneficiaryWalletId(Long userId, Long beneficiaryWalletId);
}