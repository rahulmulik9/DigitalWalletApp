# Phase 11 — Outbox Pattern & Idempotency Keys

**Branch:** `phase-11-outbox-idempotency`
**Service:** Transaction Service (`com.rahul.transaction_service`)

This phase solves two separate but related reliability problems in a
distributed, microservice-based transfer flow:

1. **What if Kafka is down when we try to publish an event?** → Outbox Pattern
2. **What if the same transfer request arrives twice?** → Idempotency Keys

---

# PART 1 — Outbox Pattern

## The problem, in plain terms

Before this phase, a transfer did this:
```
1. Save Transaction to DB
2. Publish event to Kafka directly (transferEventProducer.publish(...))
```

These are **two separate operations against two separate systems** (Postgres
and Kafka). There is no way to make them succeed or fail together. If the
app crashes, or Kafka is unreachable, exactly between step 1 and step 2:

- The DB says "this transfer happened."
- Nobody downstream (Fraud Service, Notification Service, anything
  consuming these events) ever finds out.

This is a **lost event** — a silent, permanent gap between what the
database believes happened and what the rest of the system knows about.
In a banking system, this is unacceptable — e.g. a fraud check that should
have run, never runs, and nobody knows it was skipped.

## The fix — write intent to publish, in the same transaction

Instead of publishing to Kafka directly inside business logic, we:

1. Write a row into an `outbox_event` table, in the **same database
   transaction** as the actual business write (the `Transaction` insert).
   Since both are just rows in the same Postgres database, they are
   guaranteed to commit together or roll back together — this is a normal
   ACID guarantee we already get for free, no special coordination needed.
2. A **separate background process** (`OutboxPublisher`) polls this table
   on a schedule, and does the actual Kafka publish, marking the row `SENT`
   once Kafka confirms it.

This converts "publish or lose the event" into "publish now, or retry
until it eventually succeeds (or is flagged for attention)." The DB write
becomes the single source of truth for "did this need to be published,"
completely decoupled from whether Kafka happened to be reachable at that
exact moment.

## Entity — `OutboxEvent`

```java
package com.rahul.transaction_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType;   // e.g. "TRANSACTION" — what kind of thing changed

    @Column(nullable = false)
    private String aggregateId;     // the actual transaction's id, as a string

    @Column(nullable = false)
    private String eventType;       // e.g. "TransferInitiated"

    // IMPORTANT: no @Lob here. @Lob + String on Postgres makes Hibernate use
    // Postgres's Large Object (oid) storage instead of a plain text column —
    // the column ends up storing a numeric OID reference, not the JSON text
    // itself. columnDefinition = "TEXT" alone is correct and sufficient.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

```java
package com.rahul.transaction_service.entity;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
```

### Real bug hit during this phase: `@Lob` + Postgres

Initial version had `@Lob` on `payload`. This caused every insert to store
a small integer (e.g. `16541`, `16542`) instead of JSON text. Reason:
Postgres treats `@Lob` on a `String` as a request for **Large Object (oid)**
storage — a completely separate storage mechanism where the column just
holds a reference number, not the actual text. Removing `@Lob` and keeping
only `columnDefinition = "TEXT"` fixed it immediately. **Lesson: never use
`@Lob` for a `String` column on Postgres unless you specifically want oid
storage (you almost never do).**

## Repository

```java
package com.rahul.transaction_service.repository;

import com.rahul.transaction_service.entity.OutboxEvent;
import com.rahul.transaction_service.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
```

## Writing the outbox row — `OutboxService`

```java
package com.rahul.transaction_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.transaction_service.entity.OutboxEvent;
import com.rahul.transaction_service.entity.OutboxStatus;
import com.rahul.transaction_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper; // Spring Boot auto-configures this bean

    @SneakyThrows
    public void saveEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json = objectMapper.writeValueAsString(payload);

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .build();

        outboxEventRepository.save(event);
    }
}
```

## Calling it from the business transaction — `TransferService`

The critical detail: `outboxService.saveEvent(...)` is called **inside the
same `@Transactional` method**, right alongside `transactionRepository.save(...)`.
This is what makes the two writes atomic — if either one throws, Spring
rolls back the whole transaction, so you never end up with a `Transaction`
row and no matching `OutboxEvent`, or vice versa.

```java
@Transactional
public Transaction transfer(TransferRequest request, Long callerUserId) {
    // ... validation, wallet lookups, ownership check ...

    Transaction txn = Transaction.builder()
            .fromWalletId(fromWallet.getId())
            .toWalletId(toWallet.getId())
            .amount(request.getAmount())
            .type(TransactionType.TRANSFER)
            .status(TransactionStatus.PENDING)
            .build();

    Transaction savedTxn = transactionRepository.save(txn);

    // Same transaction as the save above — atomic with it.
    outboxService.saveEvent(
            "TRANSACTION",
            String.valueOf(savedTxn.getId()),
            "TransferInitiated",
            TransferInitiatedPayload.builder()
                    .transactionId(savedTxn.getId())
                    .fromWalletId(fromWallet.getId())
                    .toWalletId(toWallet.getId())
                    .amount(request.getAmount())
                    .timestamp(Instant.now())
                    .build()
    );

    return savedTxn;
}
```

## Publishing — `OutboxPublisher`

This is a background job, not something ever called directly from other
code. It runs because of two annotations working together:

- `@EnableScheduling` on the main `@SpringBootApplication` class tells
  Spring "scan for `@Scheduled` methods and run them on a timer."
- `@Scheduled(fixedDelay = 5000)` on the method itself tells Spring "call
  this automatically every 5 seconds, forever."

```java
package com.rahul.transaction_service.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahul.transaction_service.entity.OutboxEvent;
import com.rahul.transaction_service.entity.OutboxStatus;
import com.rahul.transaction_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final String OUTBOX_TOPIC = "outbox-events"; // one shared topic for all event types
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate; // sends raw JSON text, not a Java object
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            String message = buildMessage(event);

            // kafkaTemplate.send(...) is ASYNC — it returns a CompletableFuture
            // immediately, not the actual result. .whenComplete(...) registers a
            // callback that runs LATER, whenever Kafka actually responds.
            kafkaTemplate.send(OUTBOX_TOPIC, event.getAggregateId(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            handleFailure(event, ex);
                        } else {
                            log.info("Published outbox event id={} eventType={} to partition={}",
                                    event.getId(), event.getEventType(),
                                    result.getRecordMetadata().partition());

                            event.setStatus(OutboxStatus.SENT);
                            event.setSentAt(LocalDateTime.now());
                            outboxEventRepository.save(event);
                        }
                    });
        }
    }

    // Increments retryCount on every failure. After MAX_RETRY_COUNT (5) failed
    // attempts, flips status to FAILED so the publisher stops picking it up
    // (it only queries PENDING rows) — prevents an infinite retry loop on a
    // permanently broken event, and turns it into a visible, queryable signal
    // instead of a silent forever-retry.
    private void handleFailure(OutboxEvent event, Throwable ex) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);

        if (newRetryCount >= MAX_RETRY_COUNT) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event id={} eventType={} failed after {} attempts — marking FAILED",
                    event.getId(), event.getEventType(), newRetryCount, ex);
        } else {
            log.warn("Outbox event id={} eventType={} failed (attempt {}/{}), will retry",
                    event.getId(), event.getEventType(), newRetryCount, MAX_RETRY_COUNT, ex);
        }

        outboxEventRepository.save(event);
    }

    // The stored payload is already a JSON string (e.g. {"transactionId":10,...}).
    // If we just glued it into another JSON string as-is, it would come out
    // double-escaped (a JSON string containing JSON text, not real nested JSON).
    // So: parse it back into a generic Object first (readValue), then let
    // Jackson write the whole wrapper out again (writeValueAsString) — this
    // produces clean, properly nested JSON with eventType alongside the data.
    @SneakyThrows
    private String buildMessage(OutboxEvent event) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("eventType", event.getEventType());
        Object parsedPayload = objectMapper.readValue(event.getPayload(), Object.class);
        wrapper.put("data", parsedPayload);

        return objectMapper.writeValueAsString(wrapper);
    }
}
```

Final message published to Kafka looks like:
```json
{"eventType":"TransferInitiated","data":{"transactionId":10,"fromWalletId":1,"toWalletId":2,"amount":10.00,"timestamp":"2026-09-03T06:08:54Z"}}
```

## The lifecycle, end to end

```
Transfer request comes in
        ↓
TransferService.transfer() — one @Transactional method:
   • saves Transaction
   • saves OutboxEvent (status = PENDING)   ← atomic with the line above
        ↓
   (returns to client — nothing published yet)
        ↓
Every 5 seconds, automatically (Spring's scheduler):
OutboxPublisher.publishPendingEvents()
   • finds all PENDING rows
   • sends each to Kafka topic "outbox-events"
        ↓
   ┌────────────┴────────────┐
   ↓                          ↓
Kafka confirms success    Kafka send fails
   ↓                          ↓
status → SENT              retryCount++
sentAt = now                  ↓
                        retryCount >= 5?
                        ├── No  → stays PENDING, retried next poll (5s later)
                        └── Yes → status → FAILED, never auto-retried again
```

## Tested scenarios

1. **Normal case** — Kafka up, transfer happens → row goes `PENDING` → `SENT`
   within 5 seconds automatically.
2. **Kafka down, then recovered** — stopped Kafka, ran a transfer, watched
   `retryCount` climb (1/5, 2/5, ...) via logs and DB, restarted Kafka before
   hitting 5 — row recovered to `SENT` on the very next poll.
3. **Kafka down permanently** — same as above, but never restarted Kafka —
   row correctly flipped to `FAILED` after exactly 5 attempts (~25 seconds),
   and stayed `FAILED` even after Kafka came back later (expected — `FAILED`
   rows are intentionally not auto-retried; that's a deliberate real-world
   limitation this phase leaves for manual/alerting-based recovery).

---

# PART 2 — Idempotency Keys

## The problem, in plain terms

`POST /api/transfers` can be called more than once for what the client
*intends* to be a single transfer:
- A network timeout makes the client's HTTP library retry automatically.
- A user double-clicks "Send Money" before the UI disables the button.
- An upstream Feign client (Phase 9) retries on a transient failure.

Without any protection, each of these retries is indistinguishable from a
brand-new transfer request — the system would happily process it again,
moving money a second time. **The fix must guarantee "exactly once" effect
per logical request, even under genuine concurrency** (two identical
requests landing at the same instant), not just when they arrive one after
another.

## The mechanism — a client-supplied key, checked before processing

The client sends a unique value in a header on every transfer:
```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

The server's job: remember every key it has ever seen, and what it
answered for that key, so a repeated key returns the *same* answer instead
of executing the transfer again.

## Entity — `IdempotencyKey`

```java
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
    private String idempotencyKey;       // the client-supplied key

    @Column(nullable = false)
    private String requestHash;          // SHA-256 of the request body

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IdempotencyStatus status = IdempotencyStatus.PROCESSING;

    private Integer responseStatus;      // filled in only once COMPLETED
    @Column(columnDefinition = "TEXT")
    private String responseBody;         // filled in only once COMPLETED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

```java
package com.rahul.transaction_service.Idempotency;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED
}
```

**Why `requestHash` exists:** the key alone only proves "I've seen this
key before" — it says nothing about whether the *body* of the retry
matches the original. If a client bug reused an old key for a genuinely
different transfer (different amount, different wallet), blindly returning
the cached response would silently swallow a real, different transfer that
never actually happened. Comparing a hash of the request body catches this
and lets us reject it explicitly instead.

## Repository

```java
package com.rahul.transaction_service.Idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
```

## The first (flawed) design — and why it was wrong

The first version of this logic was:
```
1. Check if the key exists.
2. If not, process the transfer.
3. After processing, save the key + response.
```

**This is unsafe under real concurrency.** If two identical requests
(same key) arrive at nearly the same instant, both can pass step 1
("does not exist yet") **before either has reached step 3** — because
neither has saved anything yet at the moment they check. Result: both
proceed to execute the actual transfer. Two debits happen. Idempotency
completely fails to do its one job, silently, under exactly the load
condition it exists to protect against.

**The lesson:** a check-then-act pattern in application code is never
safe against true concurrency — only the database itself, via a
constraint enforced atomically at insert time, can give that guarantee.

## The corrected design — "reserve, then process, then complete"

Flip the order: **claim the key first**, before touching any business
logic, and let the database's own unique constraint be the actual
enforcement mechanism.

```
1. Try to insert a row for this key, status = PROCESSING, immediately.
   → This insert commits in its OWN transaction (REQUIRES_NEW), right away,
     independent of anything else — so it's instantly visible to any other
     concurrent request checking the database.

2a. Insert succeeds → this request has exclusive ownership of the key.
    Proceed with the real transfer. Once done, update the SAME row:
    status = COMPLETED, store the response.

2b. Insert fails (DataIntegrityViolationException, from the unique
    constraint) → someone else already claimed this key. Look at their row:
      • requestHash doesn't match the current request → reject 409
        ("Idempotency-Key already used with a different request payload")
      • status == PROCESSING → the other request is still mid-flight
        right now → reject 409 ("already being processed") — do NOT
        proceed, that would risk the exact double-transfer this exists
        to prevent
      • status == COMPLETED → safe to return their cached response
```

## `IdempotencyService`

```java
package com.rahul.transaction_service.Idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // REQUIRES_NEW: runs in its OWN, independent transaction that commits
    // immediately — not tied to whatever transaction the calling method is
    // in. This is what makes the reservation instantly visible to a
    // concurrent request checking the DB at the same moment. Without this,
    // two requests could both "reserve" inside their own uncommitted
    // transactions and neither would see the other's row yet.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKey registerProcessing(String idempotencyKey, Object request) {
        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hashRequest(request))
                .status(IdempotencyStatus.PROCESSING)
                .build();

        // The DB's unique constraint on idempotencyKey is what actually
        // enforces "only one winner" — this throws
        // DataIntegrityViolationException if another row with this key
        // already exists, even if that other insert happened microseconds
        // ago on a different thread/request.
        return idempotencyKeyRepository.save(record);
    }

    public Optional<IdempotencyKey> checkExisting(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idempotencyKey, int responseStatus, Object response) {
        IdempotencyKey record = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(); // must exist — we reserved it ourselves moments ago

        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponseStatus(responseStatus);
        record.setResponseBody(serialize(response));
        record.setCompletedAt(LocalDateTime.now());

        idempotencyKeyRepository.save(record);
    }

    @SneakyThrows
    public <T> T deserializeResponse(String responseBody, Class<T> type) {
        return objectMapper.readValue(responseBody, type);
    }

    public boolean matchesOriginalRequest(IdempotencyKey record, Object incomingRequest) {
        return record.getRequestHash().equals(hashRequest(incomingRequest));
    }

    @SneakyThrows
    private String serialize(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }

    @SneakyThrows
    public String hashRequest(Object request) {
        String json = objectMapper.writeValueAsString(request);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(json.getBytes());

        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
```

## `TransferController` — wiring it all together

```java
package com.rahul.transaction_service.controller;

import com.rahul.transaction_service.Idempotency.IdempotencyKey;
import com.rahul.transaction_service.Idempotency.IdempotencyService;
import com.rahul.transaction_service.Idempotency.IdempotencyStatus;
import com.rahul.transaction_service.dto.transfer.TransactionResponse;
import com.rahul.transaction_service.dto.transfer.TransferRequest;
import com.rahul.transaction_service.entity.Transaction;
import com.rahul.transaction_service.security.SecurityUtils;
import com.rahul.transaction_service.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final SecurityUtils securityUtils;
    private final IdempotencyService idempotencyService;

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // Try to claim the key FIRST — before any business logic runs.
        try {
            idempotencyService.registerProcessing(idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            // Someone already claimed this key — inspect their record.
            IdempotencyKey existing = idempotencyService.checkExisting(idempotencyKey)
                    .orElseThrow();

            if (!idempotencyService.matchesOriginalRequest(existing, request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key already used with a different request payload");
            }

            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A request with this Idempotency-Key is already being processed");
            }

            // COMPLETED — safe to return the cached response.
            TransactionResponse cachedResponse =
                    idempotencyService.deserializeResponse(existing.getResponseBody(), TransactionResponse.class);
            return ResponseEntity.status(existing.getResponseStatus()).body(cachedResponse);
        }

        // We won the reservation — no one else can be here concurrently for this key.
        Long callerUserId = securityUtils.getCurrentUserId();
        Transaction txn = transferService.transfer(request, callerUserId);
        TransactionResponse response = toResponse(txn);

        idempotencyService.complete(idempotencyKey, HttpStatus.CREATED.value(), response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private TransactionResponse toResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .fromWalletId(txn.getFromWalletId())
                .toWalletId(txn.getToWalletId())
                .amount(txn.getAmount())
                .type(txn.getType())
                .status(txn.getStatus())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
```

## The lifecycle, end to end

```
POST /api/transfers, Idempotency-Key: X
        ↓
registerProcessing(X, request)  — tries to insert {key=X, status=PROCESSING}
        ↓
   ┌──────────────┴──────────────┐
   ↓                              ↓
Insert succeeds              Insert fails (unique constraint)
(nobody else has X)          (X already exists — someone else got there first)
   ↓                              ↓
Run the real transfer     Fetch the existing row for X
   ↓                              ↓
complete(X, ...)          requestHash mismatch?
— status → COMPLETED         → 409 "different payload"
— response saved                 ↓
   ↓                       status == PROCESSING?
Return response               → 409 "already being processed"
                                  ↓
                          status == COMPLETED?
                             → return their cached response, no new transfer
```

## Why `REQUIRES_NEW` specifically (not plain `@Transactional`)

Plain `@Transactional` on `registerProcessing(...)` would just join
whatever transaction the calling method is already in — meaning the
insert wouldn't actually commit to the database until the *entire*
controller method finishes. That defeats the purpose: a concurrent
request checking the database moments later wouldn't see the reservation
yet, because it's still sitting uncommitted. `REQUIRES_NEW` forces a
brand-new, independent transaction that commits the instant the method
returns — making the claim immediately visible to everyone else.

## Tested scenarios

1. **Fresh key** — normal transfer, `201`, row saved as `COMPLETED`.
2. **Same key, same body, called again (sequentially)** — returns the
   exact cached response; confirmed via row counts that no new
   `Transaction`, `OutboxEvent`, or `IdempotencyKey` row was created.
3. **Same key, different body** — correctly rejected with `409` (mismatch
   detected via `requestHash` comparison).
4. **True concurrency** — fired two identical requests simultaneously via
   parallel `curl &` calls with the same brand-new key. One completed
   normally; the other received `"A request with this Idempotency-Key is
   already being processed"` (409) — confirmed via `SELECT COUNT(*) FROM
   transactions` that only **one** transaction was created, not two.

---

# Summary — why both patterns matter together

- **Outbox** guarantees an event is never silently lost once a business
  fact is committed to the database — it decouples "did the DB write
  succeed" from "is Kafka currently reachable."
- **Idempotency** guarantees a client's retry of the *same logical
  request* never causes the business action to execute more than once —
  it decouples "how many times did the network deliver this request" from
  "how many times did we actually act on it."

Both patterns share the same underlying principle: **use the database's
own transactional/constraint guarantees as the actual source of safety,
not application-level timing assumptions.** This is exactly what Phase 12
(Saga) builds on top of — every saga event published by Account Service
will flow through this same outbox mechanism, so the reliability
guarantees carry forward instead of being reinvented.