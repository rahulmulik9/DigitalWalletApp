package com.rahul.account_service.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.account_service.outbox.TransferInitiatedPayload;
import com.rahul.account_service.service.TransferSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferInitiatedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferSagaService transferSagaService;

    // Separate groupId per listener — ensures every listener gets an independent
    // copy of each message on the shared "outbox-events" topic. If multiple
    // listeners shared the same groupId, Kafka would treat them as one group and
    // split messages between them instead of delivering to both.
    @KafkaListener(topics = "outbox-events", groupId = "account-service-transfer-initiated")
    public void handle(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String eventType = root.get("eventType").asText();

        if (!"TransferInitiated".equals(eventType)) {
            return;
        }

        TransferInitiatedPayload payload =
                objectMapper.treeToValue(root.get("data"), TransferInitiatedPayload.class);

        log.info("Received TransferInitiated: transactionId={} fromWalletId={} toWalletId={} amount={}",
                payload.getTransactionId(), payload.getFromWalletId(),
                payload.getToWalletId(), payload.getAmount());

        transferSagaService.handleTransferInitiated(payload);
    }
}