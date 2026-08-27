# PayFlow — Phase 5 Documentation

---

## 1. Account Service — What Changed

### `User` entity
- Removed: `beneficiaries` list.
- Reason: `Beneficiary` now lives in Transaction Service. Account Service
  has no reason to know about it.

### `Wallet` entity
- Removed: `ledgerEntries` list.
- Reason: `LedgerEntry` now lives in Transaction Service.

### `WalletService.deposit()` / `withdraw()`

**Old flow:**
```
deposit/withdraw request
      |
      v
update wallet balance
      |
      v
create Transaction row
      |
      v
create LedgerEntry row
      |
      v
save everything (same DB)
```

**New flow:**
```
deposit/withdraw request
      |
      v
update wallet balance
      |
      v
save wallet
      |
      v
(done — no Transaction/LedgerEntry created)
```
Deposit/withdraw no longer show up in transaction history. Deliberate Phase 5
simplification — not a bug.

### `WalletController` — new endpoints
- `POST /api/wallets/{walletId}/debit`
- `POST /api/wallets/{walletId}/credit`

Flow for both:
```
Transaction Service calls debit/credit
      |
      v
Account Service validates wallet + balance
      |
      v
Account Service updates balance
      |
      v
Account Service returns success/failure
```

---

## 2. Transaction Service — What Changed

### `Beneficiary` entity
- `user` (relation) → `userId` (plain value)
- `beneficiaryWallet` (relation) → `beneficiaryWalletId` (plain value)

### `LedgerEntry` entity
- `wallet` (relation) → `walletId` (plain value)
- `transaction` (relation) — unchanged, stays local.

### `Transaction` entity
- `fromWallet` (relation) → `fromWalletId` (plain value)
- `toWallet` (relation) → `toWalletId` (plain value)

### `BeneficiaryService.create()` / `getAll()` / `delete()`

**Old flow:**
```
lookup User by email (local DB)
      |
      v
lookup Wallet by id (local DB)
      |
      v
validate + save Beneficiary (with entity references)
```

**New flow:**
```
receive userId directly (from JWT, via SecurityUtils)
      |
      v
call Account Service → does this wallet exist?
      |
      v
validate + save Beneficiary (with plain IDs)
```

### `TransactionService.getHistory()` / `getFilteredHistory()`

**Old flow:**
```
check wallet exists (local DB)
      |
      v
fetch transaction history (local DB)
```

**New flow:**
```
call Account Service → does this wallet exist?
      |
      v
fetch transaction history (local DB)
```

### `TransferService.transfer()`

**Old flow (Phase 4, one database):**
```
debit source wallet (local)
credit destination wallet (local)
create transaction + ledger (local)
      |
      v
one @Transactional — all or nothing
```

**New flow (Phase 5, two services):**
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
      | create transaction + ledger entries
      v
7. transaction_db
```
`@Transactional` here only protects step 7 (the local write). It does NOT
make steps 2–5 atomic. If debit succeeds and credit fails, money is stuck
in an inconsistent state. This is intentional — Phase 11 fixes it with Saga.

### `TransactionController` / `TransferController` — ownership checks

**Old flow:**
```
get current user's email (Spring Security context)
      |
      v
compare against wallet.getUser().getEmail()
```

**New flow:**
```
get current user's userId (from JWT, via SecurityUtils)
      |
      v
call Account Service → get wallet's userId
      |
      v
compare the two IDs
```

Admin check — same idea:
```
Old: check ROLE_ADMIN from Spring Security's Authentication object
New: check "role" claim pulled out of the JWT by SecurityUtils
```

---

## 3. How JWT Works Now (Both Services)

Account Service still issues the token at login — same as Phase 2/4. What's
new is *what's packed inside it*, and *who reads it*.

**Token issued at login (Account Service):**
```
login success
      |
      v
build JWT containing:
  - email
  - userId
  - walletId
  - role
      |
      v
sign with shared secret
      |
      v
return token to client
```

**Every request to Transaction Service:**
```
request arrives with Authorization: Bearer <token>
      |
      v
JwtAuthenticationFilter intercepts it
      |
      v
verify signature using the SAME shared secret
      |
      v
if valid → extract userId, walletId, role from token
      |
      v
if invalid/missing → reject request (401), stop here
```

Transaction Service never talks to Account Service just to know "who is
this user" — everything it needs (userId, walletId, role) already travels
inside the token itself. That's the whole point of JWT being stateless.

---

## 4. How `SecurityUtils` Works (Transaction Service)

`SecurityUtils` doesn't decode anything itself — it just reads values that
`JwtAuthenticationFilter` already placed on the request earlier in the same
request's lifecycle.

```
Controller calls securityUtils.getCurrentUserId()
      |
      v
SecurityUtils reads request.getAttribute("userId")
      |
      v
returns it
```

Same pattern for `getCurrentWalletId()` (`"walletId"`) and
`isCurrentUserAdmin()` (`"role"`, compared to `"ADMIN"`).

---

## 5. How the Request Gets Filled (`JwtAuthenticationFilter`)

This is the piece that makes `SecurityUtils` work — it runs once per
request, before the controller method executes.

```
request comes in
      |
      v
extract token from Authorization header
      |
      v
token present? ──no──> let request continue unauthenticated
      |
     yes
      |
      v
verify signature + expiry
      |
      v
valid? ──no──> reject with 401, stop here
      |
     yes
      |
      v
extract claims from token: userId, walletId, role
      |
      v
attach each one onto the request as an attribute
  (userId  → request attribute "userId")
  (walletId → request attribute "walletId")
  (role    → request attribute "role")
      |
      v
let the request continue to the controller
      |
      v
controller/SecurityUtils reads those attributes later
```

Nothing here touches `SecurityContextHolder` — that's a Spring Security
concept, and Transaction Service doesn't have Spring Security. Request
attributes are a plain servlet mechanism, available with just `spring-web`.
