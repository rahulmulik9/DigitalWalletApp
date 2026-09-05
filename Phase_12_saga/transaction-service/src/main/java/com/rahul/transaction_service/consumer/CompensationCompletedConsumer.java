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
public class CompensationCompletedConsumer {

    private final ObjectMapper objectMapper;
    private final TransferService transferService;

    @KafkaListener(topics = "outbox-events", groupId = "transaction-service-compensation-completed")
    public void handle(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String eventType = root.get("eventType").asText();

        if (!"CompensationCompleted".equals(eventType)) {
            return;
        }

        TransferEventPayload payload =
                objectMapper.treeToValue(root.get("data"), TransferEventPayload.class);

        log.info("Received CompensationCompleted: transactionId={}", payload.getTransactionId());

        transferService.handleCompensationCompleted(payload.getTransactionId());
    }
}