# PayFlow — Old Flow (Phase 1–4) vs New Flow (Phase 5)

---

## Old Flow — Monolith (Phase 1–4)

One application, one database, everything talks via local Java method calls.

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

**Register:**
```
Client → UserController → UserService → save User + Wallet → DB
```

**Deposit/Withdraw:**
```
Client → WalletController → WalletService
      → update balance
      → create Transaction + LedgerEntry
      → save all (same DB, one @Transactional)
```

**Transfer:**
```
Client → TransferController → TransferService
      → debit wallet A (local)
      → credit wallet B (local)
      → create Transaction + LedgerEntry
      → save all (same DB, one @Transactional)
      → all-or-nothing guaranteed by the database
```

---

## New Flow — Microservices (Phase 5)

Two independent apps, two independent databases, communication over HTTP.

```
                         Client
                       /         \
                      /           \
                     v             v
          Account Service     Transaction Service
              :8081                 :8082
                 |                     |
                 v                     v
            account_db           transaction_db
                 ^
                 |
                 | REST (AccountServiceClient)
                 |
     Transaction Service ------------
```

**Register / Login:** unchanged — fully inside Account Service.
```
Client → Account Service → account_db
```

**Deposit/Withdraw:**
```
Client → Account Service
      → update balance
      → save wallet
      (no Transaction/LedgerEntry — that data lives in the other service now,
       and Phase 5 keeps this simple: no cross-service call for it yet)
```

**Add Beneficiary:**
```
Client → Transaction Service
      → read userId from JWT
      → call Account Service: does this wallet exist?
      → save Beneficiary (userId + walletId as plain values)
      → transaction_db
```

**View Transaction History:**
```
Client → Transaction Service
      → read userId from JWT
      → call Account Service: does this wallet exist + who owns it?
      → compare owner to caller (or allow if admin)
      → fetch history from transaction_db
```

**Transfer (the big one):**
```
1. Client
      |
      v
2. Transaction Service
      |
      | debit source wallet
      v
3. Account Service → account_db
      |
      | success
      v
4. Transaction Service
      |
      | credit destination wallet
      v
5. Account Service → account_db
      |
      | success
      v
6. Transaction Service
      |
      | create Transaction + LedgerEntry
      v
7. transaction_db
```

**The key difference from the monolith:**
```
Old: one @Transactional, one DB → all-or-nothing, guaranteed by Postgres
New: two services, two DBs, two network calls in between →
     @Transactional only protects step 7 (the local write).
     If step 2-3 succeeds and step 4-5 fails, wallet A is already
     short money with nothing to undo it automatically.
```

This gap is deliberate — it's what Phase 11 (Saga + Compensation +
Idempotency + Outbox) exists to close. Phase 5's job is just to make the
gap visible and understood, not to fix it.

---

## Why This Matters (Interview Angle)

| Question | Old (Monolith) | New (Phase 5) |
|---|---|---|
| Who owns wallet balance? | Same app as everything else | Account Service only |
| How does Transaction Service reach a wallet? | Direct Java object reference | HTTP call via `AccountServiceClient` |
| What guarantees atomicity? | Single DB transaction | Nothing yet — known gap |
| How does the app know who's calling? | Spring Security context | JWT claims read manually per service |
| What happens if one step fails mid-transfer? | Whole transaction rolls back | Partial state is possible — not yet handled |
