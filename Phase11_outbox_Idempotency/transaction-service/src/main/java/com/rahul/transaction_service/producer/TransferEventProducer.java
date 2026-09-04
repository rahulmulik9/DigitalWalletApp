package com.rahul.transaction_service.producer;

import com.rahul.transaction_service.event.TransferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventProducer {

    private static final String TOPIC = "transfer-events";

    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;

    public void publish(TransferEvent event) {
        // Key = fromWalletId → all events for this wallet stay in order,
        // in the same partition, even across INITIATED and COMPLETED events.
        String key = String.valueOf(event.getFromWalletId());

        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} event for transactionId={}",
                                event.getEventType(), event.getTransactionId(), ex);
                    } else {
                        log.info("Published {} event for transactionId={} to partition={}",
                                event.getEventType(), event.getTransactionId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}