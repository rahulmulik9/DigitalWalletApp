# Phase 3 — Advanced JPA & Ledger System

**Branch:** `phase-3-advanced-jpa`
**Builds on:** `phase-2-security-jwt`
**Status:** ✅ Implemented

---

## Table of Contents

1. [Overview](#overview)
2. [Why a Ledger Instead of Just a Balance Column](#why-a-ledger-instead-of-just-a-balance-column)
3. [Domain Model](#domain-model)
4. [Entities](#entities)
5. [Repositories](#repositories)
6. [Services](#services)
7. [API Endpoints](#api-endpoints)
8. [Request / Response Flow](#request--response-flow)
9. [N+1 Problem & Current Mitigation](#n1-problem--current-mitigation)
10. [Database Indexing](#database-indexing)
11. [Seed Data (`data.sql`)](#seed-data-datasql)
12. [Testing Guide](#testing-guide)
13. [Known Limitations](#known-limitations)
14. [What's Next (Phase 4)](#whats-next-phase-4)

---

## Overview

Phase 3 adds a proper **double-entry ledger system** alongside the Phase 2 cached `balance` column. Every movement of money (deposit, withdraw, transfer) writes **immutable ledger rows** and updates the cached wallet balance, giving the system:

- An **auditable trail** of every rupee that ever moved
- The ability to **reconcile** a wallet's balance independently of the cached column
- Support for **saved beneficiaries** (quick transfer targets)
- **Paginated transaction history**

### What Was Added
- `LedgerEntry` — immutable DEBIT/CREDIT rows
- `Beneficiary` — saved payees per user
- Transaction history endpoint with pagination
- Initial N+1 mitigation for wallet references via `@EntityGraph`
- Database indexes on `wallet_id` and `created_at`

---

## Why a Ledger Instead of Just a Balance Column

| Approach | Phase 2 (`balance` column only) | Phase 3 (Ledger) |
|---|---|---|
| Balance storage | Single mutable number | Cached column **+** derivable from ledger |
| Audit trail | ❌ None — old values are gone once overwritten | ✅ Every entry is permanent and immutable |
| Reconciliation | ❌ Can't verify correctness | ✅ `SUM(CREDIT) - SUM(DEBIT)` per wallet |
| Debugging a wrong balance | Guesswork | Read the ledger, find the discrepancy |
| Real-world parity | Toy implementation | How actual banks/payment systems work |

The ledger is the **source of truth**. The `balance` column on `Wallet` is a performance cache that should always agree with `SUM(ledger entries)` for that wallet.

---

## Domain Model

```
User (1) ──1:1── Wallet (1) ──1:M── LedgerEntry (M) ──M:1── Transaction (1)
  │
  └──1:M── Beneficiary (M) ──M:1── Wallet (as beneficiaryWallet)

Transaction (1) ──1:M── LedgerEntry (exactly 2 for TRANSFER, exactly 1 for DEPOSIT/WITHDRAW)
```

**Key relationships:**
- `User` 1:1 `Wallet` — customer registrations create one wallet; operator/admin users may have no wallet
- `User` 1:M `Beneficiary` — a user can save many payees
- `Wallet` 1:M `LedgerEntry` — every ledger row belongs to exactly one wallet
- `Transaction` 1:M `LedgerEntry` — a TRANSFER produces 2 rows (debit + credit), DEPOSIT/WITHDRAW produce 1

---

## Entities

### `LedgerEntry` (NEW)

The core new entity. **Immutable** — no setters, annotated `@org.hibernate.annotations.Immutable`, never updated after creation.

```java
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_ledger_created_at", columnList = "created_at")
})
@Immutable
public class LedgerEntry {
    private Long id;
    private Wallet wallet;          // @ManyToOne, LAZY
    private Transaction transaction; // @ManyToOne, LAZY
    private BigDecimal amount;
    private LedgerEntryType type;   // DEBIT or CREDIT
    private String description;
    private LocalDateTime createdAt;
}
```

| Column | Type | Notes |
|---|---|---|
| `wallet_id` | FK, NOT NULL | indexed |
| `transaction_id` | FK, NOT NULL | which transaction produced this row |
| `amount` | `DECIMAL(19,4)` | always positive |
| `type` | `DEBIT` \| `CREDIT` | direction, not sign |
| `description` | `VARCHAR(255)` | human-readable reason |
| `created_at` | timestamp, NOT NULL | indexed, immutable |

### `LedgerEntryType` (NEW)
```java
public enum LedgerEntryType { DEBIT, CREDIT }
```

### `Beneficiary` (NEW)
A saved payee: which user saved it, which wallet it points to, and a nickname.

```java
@Entity
@Table(name = "beneficiaries", indexes = {
    @Index(name = "idx_beneficiary_user_id", columnList = "user_id")
})
public class Beneficiary {
    private Long id;
    private User user;                // @ManyToOne, owner of the saved payee
    private Wallet beneficiaryWallet; // @ManyToOne, the payee's wallet
    private String nickname;
    private LocalDateTime createdAt;
}
```

### `User` (MODIFIED)
Added the one-to-many side to `Beneficiary`:
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Beneficiary> beneficiaries = new ArrayList<>();
```

### `Wallet` (MODIFIED)
Added the one-to-many side to `LedgerEntry` (lazy, batch-fetched, **not** cascaded — ledger rows must outlive wallet lifecycle events):
```java
@OneToMany(mappedBy = "wallet", fetch = FetchType.LAZY)
@BatchSize(size = 50)
private List<LedgerEntry> ledgerEntries = new ArrayList<>();
```
The `balance` column remains — it's the cached value, kept in sync by the service layer on every deposit/withdraw/transfer.

### `Transaction` (MODIFIED)
Added the one-to-many side to `LedgerEntry`, cascading persist so saving a `Transaction` saves its ledger rows in the same operation:
```java
@OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
@OrderBy("createdAt ASC")
private List<LedgerEntry> ledgerEntries = new ArrayList<>();

public void addLedgerEntry(LedgerEntry entry) {
    ledgerEntries.add(entry);
}
```

### `TransactionStatus` (MODIFIED)
Added `PENDING` so a transaction can exist mid-flight before being marked `SUCCESS`/`FAILED`:
```java
public enum TransactionStatus { PENDING, SUCCESS, FAILED }
```

---

## Repositories

### `LedgerEntryRepository` (NEW)
```java
List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId);

@Query("""
    SELECT COALESCE(SUM(CASE WHEN l.type = :credit THEN l.amount ELSE -l.amount END), 0)
    FROM LedgerEntry l WHERE l.wallet.id = :walletId
    """)
BigDecimal calculateBalance(@Param("walletId") Long walletId, @Param("credit") LedgerEntryType credit);
```
`calculateBalance` is the **reconciliation query** — it derives the true balance straight from the ledger, independent of the cached `Wallet.balance` column.

### `BeneficiaryRepository` (NEW)
```java
List<Beneficiary> findByUserIdOrderByCreatedAtDesc(Long userId);
Optional<Beneficiary> findByIdAndUserId(Long id, Long userId);
boolean existsByUserIdAndBeneficiaryWalletId(Long userId, Long beneficiaryWalletId);
```

### `TransactionRepository` (MODIFIED)
Added a paginated history query with `@EntityGraph` for wallet references:
```java
@EntityGraph(attributePaths = {"fromWallet", "toWallet"})
@Query("""
    SELECT t FROM Transaction t
    WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId)
    ORDER BY t.createdAt DESC
    """)
Page<Transaction> findHistory(Long walletId, Pageable pageable);
```

---

## Services

### `TransferService.transfer()` (MODIFIED)

Handles P2P transfers atomically. Sequence:

1. Reject self-transfer (`fromWalletId == toWalletId`)
2. Load `fromWallet`, verify caller owns it (403 if not)
3. Load `toWallet`
4. Check sufficient balance (throws `InsufficientBalanceException` → 409)
5. Mutate both cached balances
6. Build a `Transaction` (`type=TRANSFER`, `status=PENDING`)
7. Build **two** `LedgerEntry` rows: DEBIT on `fromWallet`, CREDIT on `toWallet`
8. Attach both to the transaction, flip status to `SUCCESS`
9. Save — all inside a single `@Transactional` boundary

```java
@Transactional
public Transaction transfer(TransferRequest request, String callerEmail) {
    // ... validation ...
    fromWallet.setBalance(fromWallet.getBalance().subtract(amount));
    toWallet.setBalance(toWallet.getBalance().add(amount));

    Transaction txn = Transaction.builder()
        .fromWallet(fromWallet).toWallet(toWallet).amount(amount)
        .type(TransactionType.TRANSFER).status(TransactionStatus.PENDING).build();

    LedgerEntry debit  = LedgerEntry.builder().wallet(fromWallet).transaction(txn)
        .amount(amount).type(LedgerEntryType.DEBIT)
        .description("Transfer to wallet " + toWallet.getId()).build();
    LedgerEntry credit = LedgerEntry.builder().wallet(toWallet).transaction(txn)
        .amount(amount).type(LedgerEntryType.CREDIT)
        .description("Transfer from wallet " + fromWallet.getId()).build();

    txn.addLedgerEntry(debit);
    txn.addLedgerEntry(credit);
    txn.setStatus(TransactionStatus.SUCCESS);

    return transactionRepository.save(txn); // cascades ledger entries via PERSIST
}
```

**Why `@Transactional` matters here:** if the process dies after debiting `fromWallet` but before crediting `toWallet`, the whole database transaction rolls back — you never end up with money vanishing or duplicating. This single-database ACID guarantee is what makes the simple version work; it stops holding once wallets live in separate services (a future phase would need a Saga instead).

### `WalletService.deposit()` / `withdraw()` (MODIFIED)

Same pattern, but produce **one** ledger entry instead of two:

- `deposit()` → balance +amount → `Transaction(type=DEPOSIT)` → 1 `CREDIT` entry
- `withdraw()` → checks balance ≥ amount → balance −amount → `Transaction(type=WITHDRAW)` → 1 `DEBIT` entry

### `TransactionService.getHistory()` (NEW)

Thin wrapper: verifies the wallet exists, then delegates to `TransactionRepository.findHistory()` with the pagination parameters.

### `BeneficiaryService` (NEW)

- `create()` — resolves the caller by email, validates the target wallet exists, blocks adding your **own** wallet as a beneficiary, blocks duplicates, saves
- `getAll()` — returns the caller's beneficiaries, newest first
- `delete()` — scoped by `(id, userId)` so you can only delete your own beneficiaries

---

## API Endpoints

| Method | Endpoint | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/transfers` | Bearer token | Transfer money between two wallets |
| `GET` | `/api/wallets/{walletId}/transactions` | Bearer token + ownership | Paginated transaction history |
| `POST` | `/api/beneficiaries` | Bearer token | Save a payee |
| `GET` | `/api/beneficiaries` | Bearer token | List caller's saved payees |
| `DELETE` | `/api/beneficiaries/{id}` | Bearer token | Remove a saved payee |

### `POST /api/transfers`

**Request**
```json
{
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 500.00
}
```

**Response (201 Created)**
```json
{
  "id": 11,
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 500.00,
  "type": "TRANSFER",
  "status": "SUCCESS",
  "createdAt": "2026-08-26T10:15:00"
}
```

**Errors**
| Status | Cause |
|---|---|
| 400 | `fromWalletId == toWalletId` |
| 500 (current) | Caller doesn't own `fromWallet`; the intended API status is 403, but the global exception handler currently maps it to 500 |
| 404 | Either wallet not found |
| 409 | Insufficient balance |

---

### `GET /api/wallets/{walletId}/transactions`

**Query parameters:**

| Param | Type | Default | Purpose |
|---|---|---|---|
| `page` | `int` | `0` | zero-indexed page |
| `size` | `int` | `10` | page size |

**Response (200 OK)** — Spring `Page<TransactionResponse>`, each including its ledger entries:
```json
{
  "content": [
    {
      "id": 4,
      "fromWalletId": 1,
      "toWalletId": 2,
      "amount": 500.00,
      "type": "TRANSFER",
      "status": "SUCCESS",
      "createdAt": "2026-08-26T09:00:00",
      "ledgerEntries": [
        { "id": 4, "walletId": 1, "transactionId": 4, "amount": 500.00, "type": "DEBIT",  "description": "Transfer to wallet 2",   "createdAt": "..." },
        { "id": 5, "walletId": 2, "transactionId": 4, "amount": 500.00, "type": "CREDIT", "description": "Transfer from wallet 1", "createdAt": "..." }
      ]
    }
  ],
  "totalElements": 6,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

Ownership is enforced the same way as `WalletController` — admins bypass the check and regular users can only view their own wallet's history. The intended rejection status is 403; the current global exception handler returns 500 for this `ResponseStatusException`.

---

### `POST /api/beneficiaries`

**Request**
```json
{ "beneficiaryWalletId": 3, "nickname": "Priya" }
```

**Response (201 Created)**
```json
{ "id": 1, "beneficiaryWalletId": 3, "nickname": "Priya", "createdAt": "2026-08-26T10:00:00" }
```

**Errors:** 400 if adding your own wallet or a duplicate, 404 if wallet doesn't exist.

### `GET /api/beneficiaries`
Returns the caller's saved payees, newest first — same `BeneficiaryResponse` shape as above, as a list.

### `DELETE /api/beneficiaries/{id}`
`204 No Content` on success, `404` if the beneficiary doesn't belong to the caller.

---

## Request / Response Flow

### Transfer Flow
```
Client → POST /api/transfers { fromWalletId, toWalletId, amount }
       → JwtAuthenticationFilter validates token, sets SecurityContext
       → TransferController extracts callerEmail via SecurityUtils
       → TransferService.transfer() [@Transactional]
             1. validate not self-transfer
             2. load fromWallet, verify ownership
             3. load toWallet
             4. check balance sufficiency
             5. mutate both cached balances
             6. build Transaction (PENDING)
             7. build 2 LedgerEntry rows (DEBIT + CREDIT)
             8. flip Transaction → SUCCESS
             9. save (cascades ledger entries)
       → 201 Created + TransactionResponse
```

### Transaction History Flow
```
Client → GET /api/wallets/1/transactions?page=0&size=10
       → JwtAuthenticationFilter validates token
       → TransactionController verifies ownership (or admin)
       → TransactionService.getHistory()
       → TransactionRepository.findHistory() [@EntityGraph fromWallet, toWallet]
       → maps each Transaction → TransactionResponse (incl. ledgerEntries)
       → 200 OK + Page<TransactionResponse>
```

---

## N+1 Problem & Current Mitigation

**The problem:** transaction history maps both wallet references and ledger entries. Without eager fetching, accessing related entities across a page can cause additional queries per transaction.

- 1 query for the page of transactions
- **+10 more queries**, one per transaction, to lazily load each `fromWallet`/`toWallet`

That's the N+1 problem — 11 queries instead of 1 for a page of 10 rows, and it gets worse linearly with page size.

**Current mitigation:** `@EntityGraph(attributePaths = {"fromWallet", "toWallet"})` on `findHistory()` fetches the source and destination wallet associations with the history query. This reduces extra wallet reads, but it does **not** fetch `Transaction.ledgerEntries`, which are accessed when the response is built. Therefore the implementation can still issue additional ledger-entry queries for a page of transactions; it is not guaranteed to be one query total.

```java
@EntityGraph(attributePaths = {"fromWallet", "toWallet"})
@Query("""
    SELECT t FROM Transaction t
    WHERE ...
    """)
Page<Transaction> findHistory(...);
```

`LedgerEntry.wallet` is lazy. The `@BatchSize(size = 50)` annotation is on `Wallet.ledgerEntries`; it does not batch the `Transaction.ledgerEntries` collection used by the history response. A future optimisation should fetch or project the required ledger entries explicitly and measure the generated queries.

---

## Database Indexing

| Table | Index | Column(s) | Why |
|---|---|---|---|
| `ledger_entries` | `idx_ledger_wallet_id` | `wallet_id` | balance reconciliation queries filter by wallet |
| `ledger_entries` | `idx_ledger_created_at` | `created_at` | ledger is typically read newest-first |
| `beneficiaries` | `idx_beneficiary_user_id` | `user_id` | every beneficiary lookup is scoped to a user |

Without these, every history/reconciliation query would force a full table scan as data grows — fine at seed-data scale, not fine in production.

---

## Seed Data (`data.sql`)

Phase 3 seed data extends the Phase 2 seed with `ledger_entries` and `beneficiaries`, verified arithmetically to reconcile:

| Wallet | Owner | Balance (from `wallets`) | Balance (derived from ledger) | Match |
|---|---|---|---|---|
| 1 | Rahul | 3500.00 | 3500.00 | ✅ |
| 2 | Amit | 3100.00 | 3100.00 | ✅ |
| 3 | Priya | 2700.00 | 2700.00 | ✅ |

Every `TRANSFER` transaction has exactly 2 ledger rows (1 DEBIT + 1 CREDIT); every `DEPOSIT`/`WITHDRAW` has exactly 1. Two beneficiaries are seeded: Rahul → Priya's wallet, Amit → Rahul's wallet. All five auto-increment sequences (`users`, `wallets`, `transactions`, `ledger_entries`, `beneficiaries`) are resynced at the end so app-generated inserts don't collide with seed IDs.

> **Gotcha hit during Phase 3:** the `ledger_entries` and `beneficiaries` INSERT blocks were drafted but never actually saved into `src/main/resources/data.sql` — only the Phase 2 `users`/`wallets`/`transactions` portion was present in the real file. Symptom: those two tables stayed empty on every restart even though the entities and repositories were correct. Fixed by appending the missing INSERTs and the two missing `setval()` calls to the actual file.

---

## Testing Guide

| # | Scenario | Expected |
|---|---|---|
| 1 | Transfer between own wallets | 400 (self-transfer blocked) |
| 2 | Transfer from wallet you don't own | Currently 500; intended API status is 403 (see known limitations) |
| 3 | Transfer more than balance | 409 |
| 4 | Valid transfer | 201, 2 ledger rows created, both balances updated |
| 5 | View own wallet's history | 200, paginated results |
| 6 | View another user's wallet history (non-admin) | Currently 500; intended API status is 403 (see known limitations) |
| 7 | Admin views any wallet's history | 200 |
| 8 | Request a later history page | 200 with the requested `page` and `size` |
| 9 | Add own wallet as beneficiary | 400 |
| 10 | Add duplicate beneficiary | 400 |
| 11 | Add valid beneficiary | 201 |
| 12 | List beneficiaries | 200, only caller's own |
| 13 | Delete another user's beneficiary by ID | 404 |
| 14 | Reconcile: `SUM(CREDIT) - SUM(DEBIT)` per wallet | Equals `Wallet.balance` |

---

## Known Limitations

- `Wallet.balance` is a **cached** value updated alongside ledger writes, not derived on every read — if the two ever drift (e.g. a bug, a manual DB edit), nothing currently auto-corrects it. `LedgerEntryRepository.calculateBalance()` exists for manual/scheduled reconciliation but isn't wired into an automated job yet.
- The `@Transactional` guarantee in `TransferService` only holds because both wallets live in the **same database**. It will not survive a future split into separate services (flagged in code comments as a Phase 11 concern — Saga pattern).
- Pagination validation currently only checks `@Min` on `page` and `size`; arbitrary large `size` values are not capped.
- `TransferService` and `TransactionController` throw `ResponseStatusException(HttpStatus.FORBIDDEN, ...)` for ownership violations, but `GlobalExceptionHandler` currently catches it in its generic `Exception` handler and returns `500`. Add a dedicated `ResponseStatusException` handler before treating the documented `403` status as the API's actual behaviour.

---

## What's Next (Phase 4)

Based on the current trajectory, Phase 4 would build on this ledger foundation with:
- Admin-facing transaction monitoring and dispute handling
- Ledger-based balance reconciliation jobs (scheduled, using `calculateBalance()`)
- Audit logging for admin actions
- Analytics/reporting endpoints (volume, top users, daily/monthly stats)
