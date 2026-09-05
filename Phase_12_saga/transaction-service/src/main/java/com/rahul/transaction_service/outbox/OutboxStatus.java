package com.rahul.transaction_service.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}