package com.rahul.monitoring_service.service;

import com.rahul.monitoring_service.entity.MonitoringFlag;
import com.rahul.monitoring_service.event.TransferEvent;
import com.rahul.monitoring_service.repository.MonitoringFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngine {

    private static final BigDecimal AMOUNT_THRESHOLD = new BigDecimal("100000");
    private static final int VELOCITY_LIMIT = 3;
    private static final long VELOCITY_WINDOW_SECONDS = 60;

    private final MonitoringFlagRepository flagRepository;

    // NOTE: in-memory only — resets on restart, and won't work correctly
    // if you ever run more than one instance of this service. Fine for
    // learning Phase 10; a real system would use Redis or a DB-backed window.
    private final Map<Long, List<Instant>> recentTransfersByWallet = new ConcurrentHashMap<>();

    public void evaluate(TransferEvent event) {
        checkAmountThreshold(event);
        checkVelocity(event);
    }

    private void checkAmountThreshold(TransferEvent event) {
        if (event.getAmount().compareTo(AMOUNT_THRESHOLD) > 0) {
            flag(event, "AMOUNT_THRESHOLD_EXCEEDED");
        }
    }

    private void checkVelocity(TransferEvent event) {
        Long walletId = event.getFromWalletId();
        Instant now = Instant.now();

        List<Instant> timestamps = recentTransfersByWallet
                .computeIfAbsent(walletId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());

        timestamps.add(now);
        timestamps.removeIf(t -> t.isBefore(now.minusSeconds(VELOCITY_WINDOW_SECONDS)));

        if (timestamps.size() > VELOCITY_LIMIT) {
            flag(event, "VELOCITY_LIMIT_EXCEEDED");
        }
    }

    private void flag(TransferEvent event, String reason) {
        log.warn("FLAGGED transactionId={} walletId={} reason={}",
                event.getTransactionId(), event.getFromWalletId(), reason);

        flagRepository.save(MonitoringFlag.builder()
                .transactionId(event.getTransactionId())
                .walletId(event.getFromWalletId())
                .amount(event.getAmount())
                .reason(reason)
                .flaggedAt(Instant.now())
                .build());
    }
}