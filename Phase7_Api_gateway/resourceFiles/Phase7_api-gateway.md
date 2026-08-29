# PayFlow — Phase 7 Complete Summary
**Branch:** `phase-7-api-gateway`
**From:** clients calling account-service and transaction-service directly, on separate ports
**To:** a single API Gateway as the only externally-facing entry point, with JWT validated at the edge

---

## 1. What Phase 6 looked like

```
Client → http://localhost:8081/api/... (account-service directly)
Client → http://localhost:8082/api/... (transaction-service directly)
```

Both services were discoverable *from each other* via Eureka, but a client
(Postman, a frontend) still had to know both ports and call each service
by its own address. Nothing hid the internal topology.

---

## 2. What Phase 7 built

```
                          Client
                            |
                            v
                  api-gateway :8080
              (JwtValidationFilter runs here)
                    /              \
                   v                v
         lb://account-service   lb://transaction-service
                   |                |
                   v                v
         Eureka-resolved      Eureka-resolved
         account-service      transaction-service
         instance                instance
```

Client only ever talks to `localhost:8080` now. The gateway resolves
where things actually live via Eureka, same `lb://` mechanism already
proven in Phase 6's `AccountServiceClient`.

---

## 3. New service — `api-gateway-service`

```
api-gateway-service/
├── pom.xml
├── ApiGatewayServiceApplication.java   (@EnableDiscoveryClient)
├── application.yml
└── filter/
    └── JwtValidationFilter.java
```

**Version note, same shape as Phase 6's discovery:** initial `pom.xml`
draft used Spring Cloud `2025.1.3`, which turned out to be built against
Spring Boot 4.0.8 per its own release notes — not confirmed against
4.1.x like `2025.1.2` explicitly is. Downgraded to `2025.1.2` to match
every other service in the project, avoiding a fourth variable if
something broke later.

**Artifact/property rename, confirmed via search before writing config:**
Spring Cloud 2025.1.x renamed both the Gateway starter and its config
prefix:
```
OLD (deprecated): spring-cloud-starter-gateway
                  spring.cloud.gateway.routes

NEW (this phase): spring-cloud-starter-gateway-server-webflux
                  spring.cloud.gateway.server.webflux.routes
```
Using the old prefix silently fails to bind — routes just don't register,
no obvious error pointing at the real cause. Worth remembering next time
this project touches Gateway config.

---

## 4. Routing — and a real path collision that had to be resolved

Pulling the actual `@RequestMapping` prefixes from every controller
revealed `WalletController` (account-service) and `TransactionController`
(transaction-service) both lived under `/api/wallets`:

```
/api/wallets/{id}                     → account-service
/api/wallets/{id}/deposit             → account-service
/api/wallets/{id}/transactions        → transaction-service  (collision!)
/api/wallets/{id}/transactions/filter → transaction-service  (collision!)
```

A single `Path=/api/wallets/**` rule can't distinguish these — whichever
service it pointed at would get the other service's requests too.

**Two options were weighed:**
- **A — order-dependent gateway routing:** put a more specific
  `/api/wallets/*/transactions/**` rule before the general
  `/api/wallets/**` rule, relying on Spring Cloud Gateway's
  first-match-wins route evaluation.
- **B — rename the colliding path in code**, giving transaction-service
  its own clean namespace.

**Chose B.** Renamed `TransactionController`:
```
@RequestMapping("/api/wallets")  → @RequestMapping("/api/transactions")
/{walletId}/transactions          → /wallet/{walletId}
/{walletId}/transactions/filter   → /wallet/{walletId}/filter
```
Reasoning: a route table where each rule is unambiguous on its own is
more interview-relevant and more maintainable than one that depends on
remembering "this rule must stay above that one." No code outside the
controller referenced the old path, so the rename was clean.

**Bug hit during this rename, worth remembering:** only `filteredHistory()`
got the path fix on the first pass — `history()` was left on its old
mapping (`/{walletId}/transactions`). Since the class-level mapping had
already changed to `/api/transactions`, the real (accidental) live route
became `/api/transactions/{walletId}/transactions`, matching nothing
Postman or the gateway expected. Result: Spring's `NoResourceFoundException`
(it tried to serve the unmatched path as a static resource) rather than a
controller-level 404 — a good example of how a partial rename can produce
a confusing error that doesn't obviously point at "you missed one
`@GetMapping`."

**Final clean route config, no ordering trick needed:**
```yaml
routes:
  - id: account-service
    uri: lb://account-service
    predicates:
      - Path=/api/users/**,/api/auth/**,/api/admin/**,/api/wallets/**

  - id: transaction-service
    uri: lb://transaction-service
    predicates:
      - Path=/api/transactions/**,/api/transfers/**,/api/beneficiaries/**
```

---

## 5. JWT validation — defense in depth, not a replacement

Explicit architecture decision, discussed before building:

```
Gateway:              validates JWT signature + expiry, rejects bad
                       tokens before they're routed anywhere
Account Service:       KEEPS its full Spring Security verification
                       (unchanged from Phase 2/5)
Transaction Service:   KEEPS its lightweight custom filter verification
                       (unchanged from Phase 5)
```

Rejected "gateway-only, services trust it blindly" — relying purely on
the perimeter is a documented real-world failure mode (misconfigured
routes, direct internal access, a gateway bug), and fintech/banking
practice specifically expects service-level authorization checks that
don't depend on the network path a request took to arrive.

`JwtValidationFilter` (a `GlobalFilter`, `@Order(-1)` so it runs before
routing) checks for a valid `Bearer` token on every request except an
explicit public-path allowlist:
```java
private static final List<String> PUBLIC_PATHS = List.of(
        "/api/users/register",
        "/api/users/login",
        "/api/auth/refresh"
);
```

**`/api/auth/refresh` was a deliberate, discussed addition to this list** —
not an oversight. Requiring a valid access token to call refresh would be
a chicken-and-egg problem, since the whole point of refresh is getting a
new access token once the old one is gone/expired. The actual security
check on that endpoint lives inside `UserService.refreshAccessToken()`,
validating the refresh token itself — that's now the only gate on this
endpoint, worth remembering since the gateway provides zero protection
here by design.

The filter itself only proves a token is authentic and unexpired — it
never reads claims (`userId`, `role`, etc.). Reading and acting on claims
stays downstream, in each service's own logic, consistent with the
defense-in-depth split.

---

## 6. A gotcha with Eureka's client-side registry caching

Not a bug — a timing characteristic worth understanding. On first
`docker compose up`, the gateway briefly returned `503 Service Unavailable`
on `/api/users/login` even though the Eureka *dashboard* already showed
`account-service` as `UP`.

```
account-service registers with the Eureka SERVER instantly on boot
        |
        v
api-gateway's own Eureka CLIENT keeps a local cached copy of the registry
        |
        v
That local cache only refreshes on its own polling interval
(registryFetchIntervalSeconds, ~30s default)
        |
        v
A request arriving before that first refresh sees a stale/empty
local copy → LoadBalancer has nothing to route to → 503
```

Resolved itself within about a minute with no config change — the
gateway's cache caught up on its next poll. Distinct from the
container-startup race `depends_on` addresses: this is about the Eureka
*client's* polling interval, not container boot order. Worth being able
to explain both separately if asked why a fresh `docker compose up`
sometimes needs a short warm-up before the gateway works.

---

## 7. Postman collection updated for this phase

New collection (`Phase 7 (API Gateway)`), changes from the Phase 5-6
version:
- New `gatewayUrl` variable (`http://localhost:8080`) — every request in
  the two main folders now goes through it instead of
  `accountServiceUrl`/`transactionServiceUrl` directly
- Transaction history requests updated to the renamed paths
  (`/api/transactions/wallet/{id}` and `.../filter`)
- Kept a small "Direct-to-service (debugging only)" folder with the old
  direct-port URLs still available — useful for isolating whether a
  future problem is at the gateway layer or inside a service itself
- All existing test scripts, token-capture logic, and variables preserved
  unchanged

---

## 8. What was deliberately left out of Phase 7

Per the plan's own scope, still not touched:
```
Config Server · OpenFeign · Resilience4j · Kafka · RabbitMQ
Saga · Outbox · Distributed tracing · Kubernetes
```

**Also intentionally not done yet:** ports `8081`/`8082` are still open
in `docker-compose.yml`. Closing them (making the gateway the only
externally reachable service) is the natural next hardening step, but
was deliberately deferred until testing was fully done — you lose direct
debugging access to those services the moment you close them.

---

## 9. Known gaps carried forward from earlier phases (unchanged this phase)

| Gap | What's missing | Solved in |
|---|---|---|
| Deposit/withdraw don't create transaction records | No cross-service event for it | Phase 10 (Kafka) |
| Transfer isn't atomic across services | No compensation on partial failure | Phase 11 (Saga) |
| Transaction Service → Account Service calls are unauthenticated | No service identity | Phase 7 partially addresses this at the edge; internal calls remain open |
| No retry/fallback if Account Service is down | No resilience layer | Phase 9 (Resilience4j) |
| Ports 8081/8082 still externally reachable | Not yet closed off | Deferred, ready to close post-Phase-7 testing |

Hardcoded service discovery (Phase 6) and "no single entry point" (this
phase's original gap) are now both resolved.

---

## 10. Files added / modified this phase

```
api-gateway-service/                        — new, full standalone app
  pom.xml
  ApiGatewayServiceApplication.java
  application.yml
  filter/JwtValidationFilter.java
  (Jib plugin config added to pom.xml for containerization)

transaction-service/
  controller/TransactionController.java     — modified (path rename,
                                                fixed in two passes —
                                                see section 4)

docker-compose.yml                          — modified (added
                                                api-gateway service block,
                                                depends_on all three
                                                other services)

Postman collection                          — new version, "Phase 7
                                                (API Gateway)", replaces
                                                direct-port calls with
                                                gatewayUrl
```

---

## 11. What Phase 7 should let you explain, out loud, without notes

```
What does uri: lb://account-service actually resolve to, and how?
Why did two controllers under the same path prefix break routing,
  and why was renaming the code preferred over reordering routes?
Why does the JWT filter run at the gateway AND still get checked again
  downstream — isn't that redundant?
Why is /api/auth/refresh deliberately excluded from JWT validation,
  and what's actually protecting that endpoint instead?
Why did a fresh docker compose up briefly 503 even though Eureka's
  dashboard already showed every service as UP?
What's still open/insecure about this setup, and why hasn't it been
  fixed yet?
```

If all six of those have a confident, concrete answer, Phase 7 is done.