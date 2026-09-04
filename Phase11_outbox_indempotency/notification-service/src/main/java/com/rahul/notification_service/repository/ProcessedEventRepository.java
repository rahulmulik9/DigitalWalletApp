package com.rahul.notification_service.repository;

import com.rahul.notification_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByTransactionId(Long transactionId);
}