package com.rahul.monitoring_service.repository;

import com.rahul.monitoring_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByTransactionId(Long transactionId);
}