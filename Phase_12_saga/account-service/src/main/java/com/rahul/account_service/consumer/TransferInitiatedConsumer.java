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

    @KafkaListener(topics = "outbox-events", groupId = "account-service")
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