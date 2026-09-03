package com.rahul.transaction_service.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OutboxMessage {
    private String eventType;
    private Object data; // will hold the already-serialized payload
}