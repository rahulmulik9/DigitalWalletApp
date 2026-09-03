package com.rahul.transaction_service.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.transaction_service.outbox.OutboxEvent;
import com.rahul.transaction_service.outbox.OutboxEventRepository;
import com.rahul.transaction_service.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final String OUTBOX_TOPIC = "outbox-events"; // ONE shared topic

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper; // NEW — needed to build the wrapper JSON

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            String message = buildMessage(event); // CHANGED — was event.getPayload() directly

            kafkaTemplate.send(OUTBOX_TOPIC, event.getAggregateId(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish outbox event id={} eventType={}",
                                    event.getId(), event.getEventType(), ex);
                        } else {
                            log.info("Published outbox event id={} eventType={} to partition={}",
                                    event.getId(), event.getEventType(),
                                    result.getRecordMetadata().partition());

                            event.setStatus(OutboxStatus.SENT);
                            event.setSentAt(LocalDateTime.now());
                            outboxEventRepository.save(event);
                        }
                    });
        }
    }

    // Wraps the stored payload JSON with its eventType, so any consumer reading
    // the shared "outbox-events" topic can tell what kind of event this is.
    @SneakyThrows
    private String buildMessage(OutboxEvent event) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("eventType", event.getEventType());
        // parse the stored payload string back into a generic Map/Object,
        // so it nests as real JSON, not as an escaped string
        Object parsedPayload = objectMapper.readValue(event.getPayload(), Object.class);
        wrapper.put("data", parsedPayload);

        return objectMapper.writeValueAsString(wrapper);
    }
}