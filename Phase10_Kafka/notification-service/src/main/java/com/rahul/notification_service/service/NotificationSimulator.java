package com.rahul.notification_service.service;

import com.rahul.notification_service.event.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationSimulator {

    public void notify(TransferEvent event) {
        log.info("📩 SMS SENT: ₹{} debited from wallet {} — transactionId={}",
                event.getAmount(), event.getFromWalletId(), event.getTransactionId());
    }
}