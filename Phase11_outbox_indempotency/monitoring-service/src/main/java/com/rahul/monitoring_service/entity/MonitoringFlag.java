package com.rahul.monitoring_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "monitoring_flags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long transactionId;
    private Long walletId;
    private BigDecimal amount;
    private String reason;       // e.g. "AMOUNT_THRESHOLD_EXCEEDED", "VELOCITY_LIMIT_EXCEEDED"
    private Instant flaggedAt;
}