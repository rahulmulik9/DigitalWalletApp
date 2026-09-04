package com.rahul.transaction_service.Idempotency;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key", uniqueConstraints = {
        @UniqueConstraint(columnNames = "idempotencyKey")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IdempotencyStatus status = IdempotencyStatus.PROCESSING; // NEW

    private Integer responseStatus; // nullable now — filled in only once COMPLETED

    @Column(columnDefinition = "TEXT")
    private String responseBody; // nullable now — filled in only once COMPLETED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt; // NEW

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}