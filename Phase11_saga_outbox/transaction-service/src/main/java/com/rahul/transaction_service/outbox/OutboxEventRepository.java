package com.rahul.transaction_service.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<com.payflow.transaction.outbox.OutboxEvent, Long> {

    List<com.payflow.transaction.outbox.OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}