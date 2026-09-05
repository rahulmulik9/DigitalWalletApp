# PayFlow — Phase 12 Saga Flow (Corrected)

> Based on the original hand-drawn flow diagram, corrected to match what's
> actually implemented and verified working end-to-end. See the "Naming
> corrections" section at the bottom for exactly what changed from the
> original diagram and why.

---

## Step-by-step flow

### 1. Client calls `POST /api/transfers` (Transaction Service)

- `TransferService.transfer()` validates the request (not same-wallet
  transfer, sender ownership via `AccountServiceClient.getWallet()`).
- Creates a `Transaction` record with `status = PENDING`, saved into
  `transaction_db`.
- Creates an `OutboxEvent` with `eventType = "TransferInitiated"`,
  `status = PENDING`, saved into `transaction_db` (same `@Transactional`
  method as the save above — atomic).
- Returns `201 Created` with `status: "PENDING"` immediately. Nothing has
  moved money yet.

### 2. Transaction Service's `OutboxPublisher` runs every 5s

- Class: `OutboxPublisher` (Transaction Service).
- Polls `outbox_event` table in `transaction_db` for `status = PENDING`.
- Publishes each pending row to Kafka topic `outbox-events`, wrapped as
  `{"eventType": "TransferInitiated", "data": {...}}`.
- Flips the row to `SENT` on success.

### 3. Account Service consumes `TransferInitiated`

- Class: `TransferInitiatedConsumer` → delegates to
  `TransferSagaService.handleTransferInitiated(...)`.
- Checks `eventType == "TransferInitiated"`, ignores everything else on
  the shared topic.
- Fetches sender's wallet, attempts debit.
    - **Success:** `account_db` updated (sender's balance reduced). Creates
      a new `OutboxEvent` (`eventType = "DebitCompleted"`, `status =
    PENDING`) in `account_db`, same transaction as the debit.
    - **Failure** (insufficient balance / wallet not found): nothing in
      `account_db` changes. Creates a new `OutboxEvent`
      (`eventType = "DebitFailed"`, `status = PENDING`, includes failure
      `reason`) in `account_db`.

### 4. Account Service's own `OutboxPublisher` runs every 5s

- Class: `OutboxPublisher` (Account Service) — a **separate instance**
  from Transaction Service's, polling `account_db`'s own `outbox_event`
  table, publishing to the same shared `outbox-events` topic.
- Publishes whichever event was created in Step 3 (`DebitCompleted` or
  `DebitFailed`).

### 5a. If `DebitCompleted` — Account Service credits the receiver

- Class: `DebitCompletedConsumer` → delegates to
  `TransferSagaService.handleDebitCompleted(...)`.
- Fetches receiver's wallet, attempts credit.
    - **Success:** `account_db` updated (receiver's balance increased).
      Creates `OutboxEvent` (`eventType = "CreditCompleted"`) in
      `account_db`.
    - **Failure** (receiver wallet not found): `account_db` unchanged so
      far. Creates `OutboxEvent` (`eventType = "CreditFailed"`, includes
      `reason`) in `account_db`.
- Published by Account Service's `OutboxPublisher` on its next 5s poll,
  same as before.

### 5b. If `DebitFailed` — goes straight to Transaction Service

- **No compensation needed** — money never left the sender, so there's
  nothing to reverse. `DebitFailed` is consumed directly by Transaction
  Service (Step 7), not by Account Service.

### 6. If `CreditFailed` — Account Service compensates (refunds sender)

- Class: `CreditFailedConsumer` → delegates to
  `TransferSagaService.handleCreditFailed(...)`.
- Deposits the amount back into the **sender's** wallet — `account_db`
  updated (reversal of the original debit).
- Creates `OutboxEvent` (`eventType = "CompensationCompleted"`) in
  `account_db`. Published by Account Service's `OutboxPublisher`, same
  5s-poll mechanism.

### 7. Transaction Service consumes the terminal events

Three separate consumer classes, one per event, all in Transaction
Service:

| Event | Consumer class | Result |
|---|---|---|
| `DebitFailed` | `DebitFailedConsumer` | `Transaction.status = FAILED` (nothing to compensate — debit never happened) |
| `CreditCompleted` | `CreditCompletedConsumer` | `Transaction.status = SUCCESS` |
| `CompensationCompleted` | `CompensationCompletedConsumer` | `Transaction.status = FAILED` (money safely returned) |

Each of these updates the `Transaction` row in `transaction_db` directly.
**No further event is published after this** — updating the status is the
final step of the saga. There is no `TransactionCompleted`/
`TransactionFailed` event broadcast to other modules; a client (or
Notification/Monitoring module, if built later) would need to read
`Transaction.status` directly (e.g. via a status-check endpoint — not yet
built) rather than subscribing to a dedicated "transaction finished" event.

---

## Event catalog (as actually implemented)

| Event | Published by | Consumed by | Meaning |
|---|---|---|---|
| `TransferInitiated` | Transaction Service | Account Service | Start the transfer — debit the sender |
| `DebitCompleted` | Account Service | Account Service (self) | Sender debited — now credit the receiver |
| `DebitFailed` | Account Service | Transaction Service | Debit failed — saga stops, nothing to compensate |
| `CreditCompleted` | Account Service | Transaction Service | Both legs done — transfer succeeded |
| `CreditFailed` | Account Service | Account Service (self) | Credit failed after debit succeeded — refund sender |
| `CompensationCompleted` | Account Service | Transaction Service | Refund issued — transfer ultimately failed, no money lost |

Six events, six consumer classes, one shared Kafka topic (`outbox-events`),
`eventType` field in the JSON payload distinguishes them.

---

## Naming corrections (original diagram → actual implementation)

| Diagram said | Actually implemented | Why |
|---|---|---|
| `DebitRequested` | `TransferInitiated` | Matches the originally agreed event catalog; never renamed during implementation |
| `TransactionFailed` (emitted by Account Service after `DebitFailed`) | *(doesn't exist)* | `DebitFailed` is consumed **directly** by Transaction Service — there's no intermediate event. Account Service doesn't need to "translate" `DebitFailed` into anything else. |
| `CreditDebitFailedConsumer` (one consumer handling both `CreditFailed` and `DebitFailed`) | Two separate paths: `CreditFailedConsumer` (Account Service, triggers compensation) and `DebitFailedConsumer` (Transaction Service, just marks status) | `DebitFailed` and `CreditFailed` are handled by **different services** with **different logic** (one compensates, one doesn't) — a single shared consumer class doesn't fit; they were never in the same place to combine. |
| `account_db updated` (reversal) shown under the same box as `DebitFailed` | Reversal only happens on `CreditFailed` | `DebitFailed` means money never left the sender — there's nothing in `account_db` to reverse. |
| `TransactionCompleted` event | *(doesn't exist)* | Transaction Service just updates `Transaction.status` locally after `CreditCompleted`/`DebitFailed`/`CompensationCompleted` — no further event is published. If a Notification/Monitoring module needs to react, it would need its own additional event or must poll `Transaction.status` (not yet built). |
| Consumers listed together as "`CompensationCompletedConsumer`, `CreditCompletedConsumer`, `DebitFailedConsumer`" reacting to `TransactionFailed`/`CreditCompleted` | Confirmed correct as **three separate consumer classes**, each reacting to its own distinct event (not `TransactionFailed`, since that event doesn't exist) | Matches actual code — just needed the non-existent `TransactionFailed` removed from the trigger list |

---

## Known, deliberately deferred gaps (not shown in the diagram)

- **No idempotent-consumption guard** on any of the six consumers — Kafka's
  at-least-once delivery means a redelivered message could double-process
  (double-debit, double-credit, etc.). Tracked separately in
  `phase-12-followup-idempotent-consumers.md`.
- **No distinction between `FAILED` (never touched money)** and
  **`FAILED` (compensated, money touched then returned)** — both map to
  the same `TransactionStatus.FAILED`. Deliberately decided to leave this
  as-is for Phase 12.
- **No client-facing status endpoint** — verification so far has been via
  logs and direct DB checks, not a `GET /api/transfers/{id}/status`
  endpoint.