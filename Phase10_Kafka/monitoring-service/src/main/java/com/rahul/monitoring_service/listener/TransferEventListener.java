package com.rahul.monitoring_service.listener;

import com.rahul.monitoring_service.event.TransferEvent;
import com.rahul.monitoring_service.service.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final RuleEngine ruleEngine;

    @KafkaListener(topics = "transfer-events", groupId = "monitoring-service-group")
    public void onTransferEvent(TransferEvent event) {
        log.info("Received {} event for transactionId={}", event.getEventType(), event.getTransactionId());

        // Only evaluate completed transfers — no point flagging a transfer
        // that hasn't actually gone through yet.
        if ("COMPLETED".equals(event.getEventType())) {
            ruleEngine.evaluate(event);
        }
    }
}