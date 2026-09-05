package com.rahul.account_service.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.account_service.outbox.CreditResultPayload;
import com.rahul.account_service.service.TransferSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditFailedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferSagaService transferSagaService;

    // Own groupId, same reasoning as the other consumers — guarantees this
    // listener gets an independent copy of every message on the shared topic.
    @KafkaListener(topics = "outbox-events", groupId = "account-service-credit-failed")
    public void handle(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String eventType = root.get("eventType").asText();

        if (!"CreditFailed".equals(eventType)) {
            return;
        }

        CreditResultPayload payload =
                objectMapper.treeToValue(root.get("data"), CreditResultPayload.class);

        log.info("Received CreditFailed: transactionId={} reason={}",
                payload.getTransactionId(), payload.getReason());

        transferSagaService.handleCreditFailed(payload);
    }
}