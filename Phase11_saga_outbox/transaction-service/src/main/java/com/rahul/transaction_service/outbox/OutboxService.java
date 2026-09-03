package com.rahul.transaction_service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper; // Spring Boot auto-configures this bean

    @SneakyThrows // acceptable here: JSON serialization of our own DTOs won't fail in practice
    public void saveEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json = objectMapper.writeValueAsString(payload);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .build();

        outboxEventRepository.save(event);
    }
}