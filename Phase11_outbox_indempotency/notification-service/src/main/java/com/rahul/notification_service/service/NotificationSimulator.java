package com.rahul.notification_service.service;

import com.rahul.notification_service.entity.ProcessedEvent;
import com.rahul.notification_service.event.TransferEvent;
import com.rahul.notification_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSimulator {

    private final ProcessedEventRepository processedEventRepository;

    public void notify(TransferEvent event) {
        Long transactionId = event.getTransactionId();

        if (processedEventRepository.existsByTransactionId(transactionId)) {
            log.warn("Duplicate event detected for transactionId={} — skipping re-notification", transactionId);
            return;
        }

        log.info("📩 SMS SENT: ₹{} debited from wallet {} — transactionId={}",
                event.getAmount(), event.getFromWalletId(), transactionId);

        processedEventRepository.save(ProcessedEvent.builder()
                .transactionId(transactionId)
                .processedAt(Instant.now())
                .build());
    }
}