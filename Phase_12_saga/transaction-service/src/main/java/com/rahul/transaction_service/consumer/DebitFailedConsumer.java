package com.rahul.transaction_service.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.transaction_service.outbox.TransferEventPayload;
import com.rahul.transaction_service.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DebitFailedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferService transferService;

    // Own groupId — guarantees this listener gets an independent copy of
    // every message on the shared "outbox-events" topic, same reasoning
    // as Account Service's consumers.
    @KafkaListener(topics = "outbox-events", groupId = "transaction-service-debit-failed")
    public void handle(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String eventType = root.get("eventType").asText();

        if (!"DebitFailed".equals(eventType)) {
            return;
        }

        TransferEventPayload payload =
                objectMapper.treeToValue(root.get("data"), TransferEventPayload.class);

        log.info("Received DebitFailed: transactionId={} reason={}",
                payload.getTransactionId(), payload.getReason());

        transferService.handleDebitFailed(payload.getTransactionId());
    }
}