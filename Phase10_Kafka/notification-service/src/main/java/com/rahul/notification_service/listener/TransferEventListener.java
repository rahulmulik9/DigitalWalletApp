package com.rahul.notification_service.listener;

import com.rahul.notification_service.event.TransferEvent;
import com.rahul.notification_service.service.NotificationSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final NotificationSimulator notificationSimulator;

    @KafkaListener(topics = "transfer-events", groupId = "notification-service-group")
    public void onTransferEvent(TransferEvent event) {
        if ("COMPLETED".equals(event.getEventType())) {
            notificationSimulator.notify(event);
        }
    }
}