# 🧩 Microservices Architecture — Config, Eureka, Feign, Gateway & JWT

> A teaching note explaining how **Account Service**, **Transaction Service**, **Config Server**, **Eureka Server**, and **API Gateway** talk to each other — and how a user's login identity (JWT) travels across all of them.

---

## 1️⃣ The Cast of Services

Think of this like a small company with different departments:

| Service | Role | Simple analogy |
|---|---|---|
| 🗂️ **Config Server** | Holds every service's settings (DB URL, ports, etc.) in one place, pulled from a GitHub repo | The company's shared settings binder |
| 📡 **Eureka Server** | Keeps track of which services are alive and where they live | The company's phone directory |
| 💰 **Account Service** | Owns users, wallets, balances. Also handles login. | HR + Accounts department |
| 💸 **Transaction Service** | Handles transfers; asks Account Service to debit/credit wallets | The "processing" department that needs Accounts' approval |
| 🚪 **API Gateway** | The single door everyone knocks on (`localhost:8888`) | The receptionist who forwards you to the right department |

**Golden rule of startup order:**

```
Config Server → Eureka Server → Account Service → Transaction Service → API Gateway
```

**Why this order?** Nobody can do their job without first (a) knowing their own settings (Config Server) and (b) being findable by others (Eureka). So those two must be up first.

---

## 2️⃣ Two Profiles: `local` vs `docker`

Every microservice ships with two tiny files whose **only job** is to say "here's where to find the Config Server":

**`application-local.yaml`**
```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

**`application-docker.yaml`**
```yaml
spring:
  config:
    import: "optional:configserver:http://config-server:8888"
```

> 💡 **Why the URL changes:** Inside Docker's internal network, containers find each other by **service name** (`config-server`), not `localhost`. `localhost` inside a container means "this container," not "my laptop."

Think of these two files as a **sticky note with a phone number** — nothing else. The real settings (DB URLs, Eureka address, etc.) don't live here at all.

---

## 3️⃣ Where the Real Settings Live: GitHub

In the Git-backed config repo, each service gets **3 files**:

| File | Purpose |
|---|---|
| `account-service.yml` | Shared/common settings (app name, etc.) |
| `account-service-local.yml` | Settings only for local runs (e.g. `localhost` DB) |
| `account-service-docker.yml` | Settings only for Docker runs (e.g. `account-db` DB) |

**Local:**
```yaml
# account-service-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/AccountService
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

**Docker:**
```yaml
# account-service-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://account-db:5432/AccountService
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

**Rule to remember:** Config Server automatically merges `{service}.yml` (common) + `{service}-{profile}.yml` (profile-specific), based on whichever profile is active. Same pattern for `transaction-service*.yml`.

---

## 4️⃣ Step-by-Step: What Actually Happens on Startup

### Step 1 — Config Server starts
Connects to GitHub, exposes all the `*.yml` files over HTTP.

### Step 2 — Eureka Server starts
Becomes the empty "phone book," ready for services to register.

### Step 3 — Health check (don't skip this!)
"Started" ≠ "ready." Always confirm via Actuator:

```yaml
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health
```

Hit `http://localhost:8888/actuator/health` → expect `{"status":"UP"}`.

**In Docker Compose**, this health check becomes a **gate** — nothing starts until its dependencies are actually healthy, not just "container running":

```yaml
account-service:
  image: account-service:latest
  depends_on:
    account-db:
      condition: service_healthy
    config-server:
      condition: service_healthy
    eureka-server:
      condition: service_healthy
```

```yaml
# The healthcheck block itself, on config-server / eureka-server / account-db
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 10s
  timeout: 5s
  retries: 5
```

> 🎯 **Why this matters:** This is the fix for the classic "my app crashed because the DB wasn't ready yet" bug.

### Step 4 — Account Service starts

| Mode | How the profile is set |
|---|---|
| Local (IntelliJ) | Run Config → Environment Variables → `SPRING_PROFILES_ACTIVE=local` |
| Docker | `docker-compose.yml` → `environment: SPRING_PROFILES_ACTIVE=docker` |

Flow: profile activates → tiny local yaml tells it where Config Server is → Config Server returns merged settings → service starts fully configured → **registers itself with Eureka** using:
```yaml
spring:
  application:
    name: account-service
```

### Step 5 — Transaction Service starts
Same flow, but it also calls Account Service via a **Feign Client**:

```java
@FeignClient(name = "account-service")
public interface AccountClient {
    @PostMapping("/api/wallets/{walletId}/debit")
    void debit(@PathVariable("walletId") Long walletId, @RequestBody AmountRequest amount);
}
```

🔑 **Three things must match exactly, or it breaks silently:**
1. `name = "account-service"` must equal Account Service's `spring.application.name` (what it registered in Eureka as).
2. The path, path variable, and request body must match the real controller method.
3. Both services must actually be visible in Eureka — Feign uses Eureka (via Spring Cloud LoadBalancer) to turn `account-service` into a real `host:port`. You never hardcode it.

**Matching controller side:**
```java
@PostMapping("/api/wallets/{walletId}/debit")
public void debit(@PathVariable Long walletId, @RequestBody AmountRequest amount) {
    // debit logic
}
```

### Step 6 — API Gateway starts
Registers with Eureka, fetches the service registry, and becomes the single door clients knock on.

---

## 5️⃣ Why We Need an API Gateway

Without one, clients must remember every service's port:
- Account Service → `localhost:8081`
- Transaction Service → `localhost:8082`

That doesn't scale, and it leaks internal architecture to the outside world. Instead, clients only ever call **one door**: `localhost:8888`.

```yaml
routes:
  - id: account-service
    uri: lb://account-service
    predicates:
      - Path=/api/users/**,/api/auth/**,/api/admin/**,/api/wallets/**
```

- `lb://account-service` → `lb` means **load-balanced**; the Gateway asks Eureka for the real address.
- `Path=...` → any request matching these paths gets forwarded there.

The Gateway never hardcodes a port. It resolves everything dynamically via Eureka.

---

## 6️⃣ Full Picture (Sequence Diagram)

```mermaid
sequenceDiagram
    participant Dev as Student/Client
    participant CS as Config Server
    participant EU as Eureka Server
    participant AS as Account Service
    participant TS as Transaction Service
    participant GW as API Gateway

    Dev->>CS: 1. Start Config Server (connects to GitHub)
    Dev->>EU: 2. Start Eureka Server
    Dev->>CS: 3. Health check /actuator/health
    Dev->>EU: 3. Health check /actuator/health
    Dev->>AS: 4. Start Account Service (profile=local/docker)
    AS->>CS: Fetch account-service.yml + account-service-{profile}.yml
    AS->>EU: Register as "account-service"
    Dev->>TS: 5. Start Transaction Service
    TS->>CS: Fetch transaction-service.yml + transaction-service-{profile}.yml
    TS->>EU: Register as "transaction-service"
    Dev->>GW: 6. Start API Gateway
    GW->>EU: Register + fetch service registry

    Note over Dev,GW: --- Runtime request flow ---
    Dev->>GW: POST localhost:8888/api/wallets/1/debit
    GW->>EU: Resolve "account-service" address
    GW->>AS: Forward request
    Note over TS,AS: When Transaction Service needs Account Service
    TS->>EU: Resolve "account-service" via Feign
    TS->>AS: POST /api/wallets/{id}/debit
```

---

## 7️⃣ Extra Points Worth Knowing

- **The `optional:` prefix** in `spring.config.import` means the app **won't crash** if Config Server is unreachable at startup. Handy for local dev, but risky in production — a service could silently boot with missing config. Worth discussing the trade-off.
- **`/actuator/refresh`** (with `spring-cloud-starter-bus` or a manual `POST`) reloads config from GitHub **without restarting** the service.
- **Eureka self-preservation mode**: if too many services go down at once, Eureka stops evicting them — this protects against false alarms during a network blip, but can confuse you into thinking a dead service is still "UP."
- **Feign + Load Balancing**: if multiple instances of `account-service` are running, Feign automatically spreads requests across them via Spring Cloud LoadBalancer — no extra code needed.
- **Common pitfall**: forget `spring.application.name` in `account-service.yml` → Eureka registers it as the generic name `application` → `@FeignClient(name = "account-service")` can no longer find it. Silent, confusing failure.
- **Gateway routing vs controller mapping are two separate things.** `Path=/api/wallets/**` on the Gateway is just a *forwarding rule* — it has zero connection to the `@RequestMapping` inside the controller. Both have to independently line up correctly.
- **Retry + mutating calls don't mix (yet).** If you later add Resilience4j retries on Feign calls, only retry **read** operations (like `getWallet`). Retrying `debit`/`credit` blindly can double-charge a wallet — that's only safe once you add idempotency keys (a later phase).
- **Environment variables can override YAML** at any layer (Docker Compose `environment:` block, for instance) — useful for secrets that shouldn't sit in Git at all (e.g. DB passwords, JWT signing keys).

---

## 8️⃣ Quick Recap Cheat-Sheet

- [ ] `application-{profile}.yaml` → only tells the app **where Config Server is**
- [ ] `{service}.yml` (Git repo, common) → shared config
- [ ] `{service}-{profile}.yml` (Git repo) → profile-specific overrides (DB, Eureka URL)
- [ ] Config Server merges common + profile config → serves it to the app
- [ ] App registers itself with Eureka using `spring.application.name`
- [ ] Feign `@FeignClient(name=...)` must match the registered Eureka name
- [ ] Feign method signature must match the target controller's mapping, path variables, and body
- [ ] API Gateway routes by `Path` predicate → resolves real host:port via Eureka (`lb://`)
- [ ] Docker Compose `depends_on` + `condition: service_healthy` (backed by Actuator) ensures correct startup order
- [ ] Never retry a mutating call (debit/credit) without idempotency in place

---

## 9️⃣ How JWT Ties All of This Together

So far, everything above is about services *finding* and *calling* each other. This section is about **identity** — how the system knows *who* is making a request, as that request hops from the client → Gateway → Transaction Service → Account Service.

### The core idea

A **JWT (JSON Web Token)** is like a **stamped ID card** the user carries with them on every request. Once Account Service checks the user's password *once* at login, it stamps an ID card (the JWT) containing everything anyone downstream needs to know about that user — so nobody else has to ask "who are you, and are you allowed to do this?" by hitting the database again.

### Step 1 — Login creates the JWT

```
POST /api/auth/login   (handled by Account Service)
```

Account Service:
1. Validates username + password.
2. Builds a JWT containing **claims** (facts about the user), for example:

```json
{
  "sub": "raj@email.com",
  "userId": 42,
  "role": "CUSTOMER",
  "walletId": 108,
  "iat": 1719800000,
  "exp": 1719803600
}
```

3. Signs the token (so it can't be tampered with) and sends it back in the response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
  "refreshToken": "..."
}
```

> 💡 **Why put `walletId` and `role` inside the token, not just `userId`?** So that downstream services (Transaction Service, Gateway) can make decisions **without calling Account Service again** just to look up "which wallet does this user own?" It trades a network call for a bit of duplicated data in the token — a classic, interview-worthy microservices trade-off. (Downside: if a user's role or wallet changes mid-session, the old token still has the stale value until it expires — worth mentioning to students.)

### Step 2 — Client stores and resends the JWT

The client (Postman, frontend app) stores the token and attaches it to **every subsequent request** as a header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6...
```

### Step 3 — The token travels through the Gateway

```
POST localhost:8888/api/transfers
Authorization: Bearer <token>
```

The Gateway can do a **first-pass check** here — reject the request early if the token is missing, malformed, or expired — before it even reaches Transaction Service. This saves downstream services from doing wasted work on garbage requests.

### Step 4 — Transaction Service extracts the claims

Inside Transaction Service, a filter (`JwtAuthenticationFilter`) runs **before** the controller:

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws IOException, ServletException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtTokenProvider.validateToken(token)) {
                Long userId   = jwtTokenProvider.getUserId(token);
                String role   = jwtTokenProvider.getRole(token);
                Long walletId = jwtTokenProvider.getWalletId(token);

                // Build a Spring Security Authentication object
                // so the rest of the app can use @PreAuthorize, SecurityContextHolder, etc.
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Optionally stash walletId for controller/service use
                request.setAttribute("walletId", walletId);
            }
        }
        chain.doFilter(request, response);
    }
}
```

This filter does three jobs, every single request:
1. **Validates** the token — checks the signature and expiry.
2. **Extracts** `userId`, `role`, `walletId` from the claims.
3. **Populates Spring Security's context**, so `@PreAuthorize("hasRole('CUSTOMER')")` and ownership checks downstream just work, without re-parsing the token everywhere.

### Step 5 — Ownership check (preventing IDOR)

This is where the extracted `walletId` earns its keep. Before Transaction Service processes a transfer, it checks:

```java
if (!request.getWalletId().equals(jwtWalletId)) {
    throw new AccessDeniedException("You cannot transfer from a wallet that isn't yours");
}
```

Without this check, User A could send a transfer request *claiming* wallet ID 999 in the request body, even though their JWT says their wallet is 108 — and silently move someone else's money. This exact check is what closes that gap.

### One key requirement: shared trust

For Transaction Service (and the Gateway) to **validate** a token that Account Service **signed**, they all need to agree on the signing secret/key:

| Signing approach | What it means |
|---|---|
| **Symmetric (HMAC, e.g. HS256)** | One shared secret. Every service that validates tokens needs the *same* secret — usually pulled from Config Server so it's centrally managed, not hardcoded per service. |
| **Asymmetric (RSA, e.g. RS256)** | Account Service holds a *private* key to sign tokens; every other service only needs the matching *public* key to verify. Slightly more setup, but safer — only one service can ever mint tokens. |

### JWT Flow — Sequence Diagram

```mermaid
sequenceDiagram
    participant U as User/Client
    participant GW as API Gateway
    participant AS as Account Service
    participant TS as Transaction Service

    U->>AS: POST /api/auth/login (username, password)
    AS->>AS: Validate credentials
    AS->>AS: Build JWT (userId, role, walletId, exp)
    AS-->>U: 200 OK { accessToken, refreshToken }

    Note over U: Client stores JWT, attaches it to every future request

    U->>GW: POST /api/transfers  (Authorization: Bearer <JWT>)
    GW->>GW: Quick check: token present & well-formed?
    GW->>TS: Forward request (with JWT header intact)
    TS->>TS: JwtAuthenticationFilter validates signature + expiry
    TS->>TS: Extract userId, role, walletId from JWT
    TS->>TS: Check: walletId in request == walletId in JWT?
    alt mismatch
        TS-->>U: 403 Forbidden
    else match
        TS->>TS: Proceed with transfer logic
        TS-->>U: 200 OK
    end
```

### Cheat-sheet addition

- [ ] Login (Account Service) validates credentials, then **mints** the JWT
- [ ] JWT carries `userId`, `role`, `walletId` (and standard `iat`/`exp`) as claims
- [ ] Client sends the JWT as `Authorization: Bearer <token>` on every request after login
- [ ] Gateway can reject malformed/missing/expired tokens early, before routing
- [ ] Each downstream service that needs identity runs its own `JwtAuthenticationFilter` to validate + extract claims
- [ ] Ownership checks compare the JWT's `walletId`/`userId` against what the request is trying to act on — this is what prevents IDOR
- [ ] All services that **verify** tokens must share the same secret (HMAC) or the signer's public key (RSA) — usually distributed via Config Server, not hardcoded
