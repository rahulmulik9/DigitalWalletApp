package com.rahul.monitoring_service.listener;

import com.rahul.monitoring_service.entity.ProcessedEvent;
import com.rahul.monitoring_service.event.TransferEvent;
import com.rahul.monitoring_service.repository.ProcessedEventRepository;
import com.rahul.monitoring_service.service.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final RuleEngine ruleEngine;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "transfer-events", groupId = "monitoring-service-group")
    public void onTransferEvent(TransferEvent event) {
        log.info("Received {} event for transactionId={}", event.getEventType(), event.getTransactionId());

        // Guard: only COMPLETED events are ever evaluated or recorded.
        // INITIATED always has transactionId=null (by design — see TransferService),
        // so letting it reach the DB save below violates the not-null constraint
        // on ProcessedEvent.transactionId and crashes the listener.
        if (!"COMPLETED".equals(event.getEventType())) {
            return;
        }

        if (processedEventRepository.existsByTransactionId(event.getTransactionId())) {
            log.warn("Duplicate event detected for transactionId={} — skipping re-processing", event.getTransactionId());
            return;
        }

        ruleEngine.evaluate(event);

        processedEventRepository.save(ProcessedEvent.builder()
                .transactionId(event.getTransactionId())
                .processedAt(Instant.now())
                .build());
    }
}