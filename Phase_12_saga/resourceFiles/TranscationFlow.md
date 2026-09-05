# PayFlow — Transfer API Flow (Phase 12 Saga)

> Corrected version of the manually-drawn flow diagram. See notes at the
> bottom for what was fixed and why.

---

- **Client calls `POST /api/transfers`**
    - Create `Transaction` record with `status = "PENDING"`
        - Saved into database → `transaction_db`
    - Create outbox event with `status = "PENDING"`, `eventType = "TransferInitiated"`
        - Saved into database (same transaction as above)
    - Response returned immediately to client: `status: "PENDING"` — nothing has moved yet

- **Publisher service in Transaction Module runs every 5 sec (Producer) (Class: `OutboxPublisher`)**
    - Check status of outbox object in database
    - If status pending, publish event with `eventType = "TransferInitiated"` and outbox payload → Kafka

- **Consumer Service consumes `"TransferInitiated"` (Consumer) (Class: `TransferInitiatedConsumer`)**
    - Check `eventType` matches string `"TransferInitiated"`, then proceed further
    - Check amount from sender wallet — if insufficient, fails
    - Debit from sender wallet
    - `account_db` updated
    - Create outbox object with `status = "PENDING"` and `eventType = "DebitCompleted"` or `"DebitFailed"`
        - Saved this outbox event into `account_db`

- **Publisher service in Account Module runs every 5 sec (Producer) (Class: `OutboxPublisher`)**
    - Check status of outbox object in database
    - If status pending, publish event as per `eventType` (`DebitCompleted` or `DebitFailed`) with outbox payload → Kafka

- **If `DebitCompleted` — Consumer Service consumes it (Consumer) (Class: `DebitCompletedConsumer`)**
    - Check `eventType` matches string `"DebitCompleted"`, then proceed further
    - Credit to receiver wallet
    - `account_db` updated
    - Create outbox object with `status = "PENDING"` and `eventType = "CreditCompleted"` or `"CreditFailed"`
        - Saved this outbox object into `account_db`

- **If `DebitFailed` — no consumer in Account Module.** Nothing to reverse (money never left the sender). This event goes straight to Transaction Module (see below).

- **Publisher service in Account Module runs every 5 sec (Producer) (Class: `OutboxPublisher`)**
    - Check status of outbox object in database
    - If status pending, publish event as per `eventType` (`CreditCompleted` or `CreditFailed`) with outbox payload → Kafka

- **If `CreditFailed` — Consumer Service consumes it (Consumer) (Class: `CreditFailedConsumer`)**
    - Check `eventType` matches string `"CreditFailed"`, then proceed further
    - Reverse the debited amount — refund sender's wallet
    - `account_db` updated
    - Create outbox object with `status = "PENDING"` and `eventType = "CompensationCompleted"`
        - Saved this outbox object into `account_db`

- **Publisher service in Account Module runs every 5 sec (Producer) (Class: `OutboxPublisher`)**
    - Check status of outbox object in database
    - If status pending, publish event as per `eventType` (`CompensationCompleted`) with outbox payload → Kafka

- **Consumer Service in Transaction Module consumes the terminal events (three separate consumer classes)**
    - `DebitFailedConsumer` — consumes `"DebitFailed"` → update `Transaction.status = "FAILED"` (nothing to compensate)
    - `CreditCompletedConsumer` — consumes `"CreditCompleted"` → update `Transaction.status = "SUCCESS"`
    - `CompensationCompletedConsumer` — consumes `"CompensationCompleted"` → update `Transaction.status = "FAILED"` (money safely returned)
    - Each of these updates the `Transaction` object directly in `transaction_db`
    - **No further outbox event is created after this step** — updating the status is the last step of the saga

- **Client checks the outcome**
    - No `TransactionCompleted`/`TransactionFailed` event is broadcast to other modules (not built yet)
    - A client (or a future Notification/Monitoring module) would need to read `Transaction.status` directly — e.g. via a status-check endpoint (not yet built)

---

## What was corrected from the original diagram

1. **`DebitRequested` → `TransferInitiated`** — this is the actual event name used throughout the implementation.
2. **Removed `TransactionFailed` as an event Account Service creates.** It doesn't exist in the code — `DebitFailed` and `CreditFailed`-triggered `CompensationCompleted` go **directly** from Account Service to Transaction Service. There's no intermediate translation step.
3. **Split `CreditDebitFailedConsumer` into two separate, correct paths** — `DebitFailed` and `CreditFailed` are handled by different services with different logic (one just reports failure, the other triggers a refund), so they were never a single shared consumer.
4. **Removed the reversal step from the `DebitFailed` path** — reversal (refund) only applies to `CreditFailed`, since `DebitFailed` means the money never left the sender in the first place.
5. **Removed the `TransactionCompleted`/`TransactionFailed` broadcast event** — Transaction Service just updates `Transaction.status` locally; no further event exists for a Notification/Monitoring module to consume yet. This would be new work if you want it, not something already built.
6. **Corrected consumer class list** for the final step to the three real classes (`DebitFailedConsumer`, `CreditCompletedConsumer`, `CompensationCompletedConsumer`), each reacting to exactly one event, rather than one box handling four event names at once.