package com.rahul.account_service.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.account_service.outbox.OutboxEvent;
import com.rahul.account_service.outbox.OutboxEventRepository;
import com.rahul.account_service.outbox.OutboxStatus;
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

    private static final String OUTBOX_TOPIC = "outbox-events";
    private static final int MAX_RETRY_COUNT = 5; // NEW

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            log.info("No result found to publish");
            return;
        }

        log.info("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            String message = buildMessage(event);

            kafkaTemplate.send(OUTBOX_TOPIC, event.getAggregateId(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            handleFailure(event, ex); // CHANGED — was just a log line before
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

    // NEW — pulled failure handling into its own method
    private void handleFailure(OutboxEvent event, Throwable ex) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);

        if (newRetryCount >= MAX_RETRY_COUNT) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event id={} eventType={} failed after {} attempts — marking FAILED",
                    event.getId(), event.getEventType(), newRetryCount, ex);
        } else {
            // stays PENDING — will be retried on the next poll
            log.warn("Outbox event id={} eventType={} failed (attempt {}/{}), will retry",
                    event.getId(), event.getEventType(), newRetryCount, MAX_RETRY_COUNT, ex);
        }

        outboxEventRepository.save(event);
    }

    @SneakyThrows
    private String buildMessage(OutboxEvent event) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("eventType", event.getEventType());
        Object parsedPayload = objectMapper.readValue(event.getPayload(), Object.class);
        wrapper.put("data", parsedPayload);

        return objectMapper.writeValueAsString(wrapper);
    }
}