package com.rahul.account_service.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}