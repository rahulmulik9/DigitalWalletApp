package com.rahul.notification_service.listener;

import com.rahul.notification_service.event.TransferEvent;
import com.rahul.notification_service.service.NotificationSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.RetryableTopic;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final NotificationSimulator notificationSimulator;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(
                    delay = 1000,
                    multiplier = 2.0
            ),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "transfer-events", groupId = "notification-service-group")
    public void onTransferEvent(TransferEvent event) {
        // Same guard as Monitoring Service — INITIATED events always have
        // transactionId=null, which violates ProcessedEvent's not-null
        // constraint inside NotificationSimulator.notify() if allowed through.
        if (!"COMPLETED".equals(event.getEventType())) {
            return;
        }

        notificationSimulator.notify(event);
    }
}