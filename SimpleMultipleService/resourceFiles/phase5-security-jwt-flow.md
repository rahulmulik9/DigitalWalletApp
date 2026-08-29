# PayFlow — Phase 5 Security & JWT Flow

There are actually **two separate flows** happening, and they work
completely differently. This is the most important thing to understand:

1. **Client → a service** — this IS authenticated, using JWT.
2. **Transaction Service → Account Service** — this is **NOT** authenticated
   at all right now. That's a deliberate (documented) gap, not something
   working invisibly under the hood. More on this in Section 4.

---

## 1. How the JWT is issued (Account Service only)

Only Account Service creates tokens. Transaction Service never does.

```
Client sends email + password
        |
        v
Account Service verifies password
        |
        v
Account Service builds a JWT containing:
  - subject  = email
  - claim: userId
  - claim: walletId
  - claim: role   (CUSTOMER / ADMIN)
        |
        v
Account Service signs it with a secret key
        |
        v
Token returned to client
```

The secret key used to sign it is just a string in `application.yaml`
(`jwt.secret`) — **the exact same string is copy-pasted into both
services' config files.** That shared secret is what makes it possible
for Transaction Service to independently verify a token it never issued.

---

## 2. What's actually inside the token

Think of a JWT as a small tamper-proof note. Anyone can *read* what's
inside it (it's not encrypted, just signed) but nobody can *edit* it
without invalidating the signature.

```
Token = { email, userId, walletId, role } + signature
```

The signature is a hash of the content + the secret key. If even one
character of the content changes, the signature no longer matches, and
verification fails. That's what "verifying" a token actually means —
recompute the hash with your copy of the secret, check it matches.

---

## 3. Client → Transaction Service (the real authenticated flow)

This is what happens on every request the client makes directly to
Transaction Service (create transfer, add beneficiary, view history).

```
Client sends request
  Header: Authorization: Bearer <token>
        |
        v
JwtAuthenticationFilter (Transaction Service) intercepts it
        |
        v
Verify signature using the SAME shared secret
        |
   valid? ──no──> reject immediately, 401, stop here
        |
       yes
        |
        v
Pull userId / walletId / role out of the token
        |
        v
Attach them to the current request
        |
        v
Controller runs
        |
        v
SecurityUtils reads userId/walletId/role off the request
        |
        v
Controller/Service uses them for ownership checks,
e.g. "does this transfer's source wallet belong to this userId?"
```

Nothing here involves a network call to Account Service. Transaction
Service verifies the token completely on its own, because it has its
own copy of the shared secret. This is the whole point of JWT being
"stateless" — no service needs to ask another service "is this user
real?" on every request.

---

## 4. Transaction Service → Account Service (the part with NO authentication)

This is the part worth being very clear-eyed about, because it's easy to
assume the client's token "just carries over" — it doesn't, unless you
explicitly forward it, and right now we don't.

```
Transaction Service needs wallet info
  (e.g. checking if a wallet exists, fetching its owner)
        |
        v
AccountServiceClient makes a plain HTTP call
  GET /api/wallets/{id}/internal
  POST /api/wallets/{id}/debit
  POST /api/wallets/{id}/credit
        |
        v
NO Authorization header is attached to this call
        |
        v
Account Service's SecurityConfig has these three routes
marked permitAll — meaning Account Service does not check
who is calling them at all
        |
        v
Account Service just executes the request and responds
```

So right now: **any request to these three endpoints succeeds,
regardless of who sends it** — there's no verification that the caller
is actually Transaction Service and not, say, a random `curl` command
hitting `localhost:8081/api/wallets/5/debit` directly. In a real
production system this is exactly the kind of gap that needs closing
before going live.

**Why did we do it this way?** Because forwarding the client's JWT
doesn't actually solve the problem — Account Service's normal wallet
endpoint enforces "you can only view/touch your own wallet," but
Transaction Service legitimately needs to check *other people's*
wallets too (the destination wallet in a transfer isn't the caller's
own). So instead of fighting that ownership check, we carved out
separate internal-only routes and left them unauthenticated for now.

**How this gets fixed later, in real systems:**
- A gateway that only allows internal service traffic on a private network
- Mutual TLS between services (each service proves its identity with a
  certificate, not a user token)
- A separate "service account" token, different from a user's token,
  that Transaction Service authenticates itself with
- Signed/short-lived internal tokens issued specifically for
  service-to-service calls

None of these are built in Phase 5 — this is intentionally left as a
known gap, worth being able to explain in an interview: "here's the
trust boundary I left open, and here's how I'd close it."

---

## 5. Full flow, end to end — a transfer request

Putting both flows together for one real request:

```
1. Client → Transaction Service
     Authorization: Bearer <token>
        |
        v
2. JwtAuthenticationFilter verifies token, extracts userId/walletId/role
        |
        v
3. TransferController → TransferService
        |
        v
4. TransferService → AccountServiceClient → Account Service
     GET /api/wallets/{fromWalletId}/internal   (no auth — permitAll)
        |
        v
5. Account Service returns wallet info (owner, balance)
        |
        v
6. TransferService checks: does fromWallet's userId match
   the caller's userId (extracted from the JWT in step 2)?
        |
   no ──> reject, 403
   yes
        |
        v
7. AccountServiceClient → Account Service
     POST /api/wallets/{fromWalletId}/debit   (no auth — permitAll)
        |
        v
8. AccountServiceClient → Account Service
     POST /api/wallets/{toWalletId}/credit   (no auth — permitAll)
        |
        v
9. Transaction Service creates Transaction + LedgerEntry rows locally
        |
        v
10. Response returned to client
```

**Key insight:** the *ownership* check (step 6) happens entirely inside
Transaction Service, using data it already has from the JWT + the
wallet info it fetched. Account Service's internal endpoints don't
re-check anything — they trust whatever Transaction Service tells them
to do. That trust is currently unguarded (Section 4), which is fine for
a learning project, but would be a real vulnerability in production.

---

## 6. Quick summary table

| | Client → Account Service | Client → Transaction Service | Transaction Service → Account Service |
|---|---|---|---|
| Authenticated? | Yes — Spring Security + JWT | Yes — custom filter + JWT | **No** — permitAll |
| Who verifies the token? | Account Service's filter | Transaction Service's own filter (same shared secret) | N/A — no token sent |
| What identifies the caller? | JWT claims | JWT claims | Nothing |
| Ownership enforced by? | Account Service itself | Transaction Service, using JWT claims + fetched wallet data | Nobody |
