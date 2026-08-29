# PayFlow — Phase 5 Complete Summary
**Branch:** `phase-5-microservices-basic`
**From:** one Spring Boot app, one database
**To:** two Spring Boot apps, two databases, talking over REST

---

## 1. What Phase 4 looked like

```
                    Client
                       |
                       v
              PayFlow Spring Boot App
                       |
        +--------------+--------------+
        |              |              |
        v              v              v
      User           Wallet       Transaction
                                      |
                                      v
                                LedgerEntry
                                      |
                                      v
                                Beneficiary
                       |
                       v
                  ONE PostgreSQL DB
```

Everything lived in one process. A transfer was one `@Transactional`
method touching one database — all-or-nothing, guaranteed by Postgres.

---

## 2. What Phase 5 built

```
                         Client
                       /         \
                      /           \
                     v             v
          Account Service     Transaction Service
              :8081                 :8082
                 |                     |
                 v                     v
            AccountService DB   TransactionService DB
                 ^
                 |
                 | REST (AccountServiceClient)
                 |
     Transaction Service ------------
```

**Account Service owns:** User, Wallet, login/register, JWT issuing,
admin views.

**Transaction Service owns:** Transaction, LedgerEntry, Beneficiary,
transfers, transaction history.

They do not share a database, and Transaction Service never touches
`User`/`Wallet` as JPA entities anymore.

---

## 3. Entity changes — relations became plain IDs

This was the biggest structural change. Any entity that used to
`@ManyToOne`/`@OneToOne` reference something now owned by the *other*
service had that relationship removed and replaced with a plain ID.

```
Transaction.fromWallet   (Wallet)     → fromWalletId   (Long)
Transaction.toWallet     (Wallet)     → toWalletId     (Long)
LedgerEntry.wallet       (Wallet)     → walletId       (Long)
Beneficiary.user         (User)       → userId         (Long)
Beneficiary.beneficiaryWallet (Wallet) → beneficiaryWalletId (Long)

User.beneficiaries   → removed entirely (owned elsewhere now)
Wallet.ledgerEntries → removed entirely (owned elsewhere now)
```

Same idea everywhere: if the entity on the other end moved to a
different service, you can no longer hold a live JPA reference to it —
only its ID as data.

---

## 4. Where a "wallet" is needed from Transaction Service

Anywhere Transaction Service used to just call `wallet.getX()` locally,
it now has to ask Account Service instead, via `AccountServiceClient`:

```
Need to check a wallet exists / get its balance / get its owner
        |
        v
AccountServiceClient.getWallet(walletId)
        |
        v
HTTP GET → Account Service → returns WalletResponse
```

```
Need to move money out of a wallet
        |
        v
AccountServiceClient.debit(walletId, amount)
        |
        v
HTTP POST → Account Service → validates + updates balance
```

Same pattern for `credit()`. Account Service is the only place that
ever actually changes a balance — Transaction Service just asks for it.

---

## 5. Deposit/Withdraw — a deliberate behavior change

Phase 4: depositing or withdrawing created a `Transaction` +
`LedgerEntry`, same as a transfer.

Phase 5: deposit/withdraw **only update the wallet balance**. No
transaction record is created for them anymore.

```
Old: deposit → update balance → create Transaction → create LedgerEntry
New: deposit → update balance → done
```

Why: creating that record would require Transaction Service, but
having Account Service call back into Transaction Service creates a
circular dependency between the two services — exactly the kind of
complexity Phase 5 says to avoid. This gets revisited properly once
Kafka (Phase 10) gives a non-circular way to do it (Account Service
publishes an event, Transaction Service consumes it whenever it likes).

Only transfers still produce a transaction record, since transfers
already go through Transaction Service by design.

---

## 6. JWT — carries more now, and gets verified twice

Phase 4: token just carried the user's email.

Phase 5: token also carries `userId`, `walletId`, and `role` as claims,
because Transaction Service needs to know who's calling without
querying Account Service every time.

```
Account Service (login)
        |
        v
builds JWT: email + userId + walletId + role
        |
        v
signs with shared secret
        |
        v
returns to client
```

Both services keep an identical copy of that shared secret. Each
service verifies the token independently — no network call needed just
to check "is this a real user."

```
Client → Account Service        Client → Transaction Service
   JWT verified by                 JWT verified by
   Account Service's                Transaction Service's own
   Spring Security filter           lightweight custom filter
   (full login/roles/BCrypt)        (verification only — no
                                      Spring Security here)
```

See `phase5-security-jwt-flow.md` for the full breakdown, including
the one open gap: **Transaction Service → Account Service calls don't
carry a JWT at all** — those three internal routes (`/internal`,
`/debit`, `/credit`) are unauthenticated for now. Documented, not
accidental — closing it needs a gateway/service-identity mechanism,
which comes in later phases.

---

## 7. Ownership checks — moved out of the database, into application code

Phase 4: `wallet.getUser().getEmail()` — a live JPA navigation, cheap,
same database.

Phase 5: Transaction Service can't do that anymore (no `User`/`Wallet`
entities locally), so ownership checks became:

```
Read userId from JWT (already verified, no extra call)
        |
        v
Fetch the wallet from Account Service (1 HTTP call)
        |
        v
Compare wallet's userId to the JWT's userId
        |
        v
Match → proceed.  No match → 403.
```

Every "does this belong to you" check across `TransferService`,
`TransactionController`, and `BeneficiaryService` follows this same
three-step pattern now.

---

## 8. Transfer — the flow that exposes the real lesson of Phase 5

```
1. Client → Transaction Service (with JWT)
2. Transaction Service verifies JWT, gets userId
3. Transaction Service → Account Service: debit source wallet
4. Account Service updates account_db, returns success
5. Transaction Service → Account Service: credit destination wallet
6. Account Service updates account_db, returns success
7. Transaction Service creates Transaction + LedgerEntry rows
8. transaction_db saves them
```

`@Transactional` on this method now only protects step 8 — the local
write. It cannot undo steps 3–4 if step 5–6 fails partway through.
That's the whole point: Phase 4's single-database guarantee is gone the
moment a real network hop sits between two writes. Phase 11 (Saga +
Compensation + Outbox) is what actually fixes this — Phase 5's job was
just to make the problem visible and understood.

---

## 9. What was deliberately left out of Phase 5

Per the plan's own scope:
```
Eureka · API Gateway · Config Server · OpenFeign · Resilience4j
Kafka · RabbitMQ · Saga · Outbox · Distributed tracing · Kubernetes
```
None of these were added. Service URLs are hardcoded in config
(`account-service.url` in `application.yaml`), not discovered. REST
calls are plain `RestTemplate`, no retries or circuit breaker yet.

---

## 10. Known gaps carried forward on purpose (for the next phases to solve)

| Gap | What's missing | Solved in |
|---|---|---|
| Deposit/withdraw don't create transaction records | No cross-service event for it | Phase 10 (Kafka) |
| Transfer isn't atomic across services | No compensation on partial failure | Phase 11 (Saga) |
| Transaction Service → Account Service calls are unauthenticated | No service identity/gateway | Phase 7+ |
| Hardcoded service URLs | No service discovery | Phase 6 (Eureka) |
| No retry/fallback if Account Service is down | No resilience layer | Phase 9 (Resilience4j) |

---

## 11. Files added this phase

```
account-service/          — full standalone Spring Boot app
transaction-service/      — full standalone Spring Boot app
  transaction-service/.../client/AccountServiceClient.java
  transaction-service/.../security/JwtAuthenticationFilter.java (new)
docker-compose.yml (if built) — account-db, transaction-db, both services
account-service-data.sql
transaction-service-data.sql
```

---

## 12. What Phase 5 should let you explain, out loud, without notes

```
Why split a monolith into services in the first place?
Why does database-per-service matter?
Why can't JPA relationships cross a service boundary?
Why does a JWT need to carry more than just an email now?
Why does @Transactional stop being enough once a network call is involved?
What actually breaks if a transfer fails halfway through?
Where's the security gap in this phase, and why does it exist on purpose?
```

If all seven of those have a confident, concrete answer, Phase 5 is done.