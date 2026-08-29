# PayFlow — Phase 6 Complete Summary
**Branch:** `phase-6-eureka`
**From:** hardcoded service URLs
**To:** service discovery via Eureka + client-side load-balanced calls

---

## 1. What Phase 5 looked like

```
transaction-service --hardcoded http://account-service:8081--> account-service
```

Both services knew each other's location because it was typed directly
into `application.yml`. Worked fine with exactly one instance of each
service, on one fixed hostname/port.

---

## 2. What Phase 6 built

```
                 eureka-server :8761
                 (registry only)
                 register-with-eureka: false
                 fetch-registry: false
                        ^        ^
              register  |        |  register
                        |        |
              account-service   transaction-service
                  :8081              :8082
                        ^
                        |
        "account-service" (logical name, no port)
                        |
        transaction-service --@LoadBalanced RestTemplate-->
                        |
              resolved live instance --> account-service
```

Three services now run instead of two. `eureka-server` holds no
business logic — it only tracks who's alive.

---

## 3. New service — `eureka-server`

```
eureka-server/
├── pom.xml                        (spring-cloud-starter-netflix-eureka-server)
├── EurekaServerApplication.java   (@EnableEurekaServer)
└── application.yml                (port 8761)
```

`enable-self-preservation: false` is set deliberately for local dev —
it evicts dead instances fast instead of waiting out the ~90s
self-preservation window. Flagged as a dev-only choice: in production
you'd want self-preservation ON, since it protects against evicting
healthy instances during a network partition at the cost of stale
entries lingering a bit longer.

---

## 4. Both existing services became Eureka clients

Same three touch points in **both** `account-service` and
`transaction-service`:

```
pom.xml            → + spring-cloud-starter-netflix-eureka-client
                      + spring-cloud.version = 2025.1.2
Main class          → + @EnableDiscoveryClient
application.yml     → + eureka.client.service-url.defaultZone
```

**Version note:** Spring Boot 4.1.1 requires Spring Cloud **2025.1.2**
specifically — 2025.1.1 only supports Boot 4.0.x and fails at startup
with a `CompositeCompatibilityVerifier` bean creation error against
4.1.x. Confirmed against Spring Cloud's own release notes before
picking the version.

---

## 5. Transaction Service's call to Account Service — now load-balanced

This is the part that actually replaces the hardcoded URL, not just
registers with Eureka:

```
pom.xml              → + spring-cloud-starter-loadbalancer
RestTemplateConfig    → + @LoadBalanced on the RestTemplate bean
application.yml       → account-service.url:
                          http://account-service:8081  (old, hardcoded)
                        → http://account-service        (new, no port)
```

```
Before: transaction-service --hardcoded host:port--> account-service
After:  transaction-service --logical name "account-service"-->
        Eureka registry --> resolved live instance --> account-service
```

`AccountServiceClient.java` itself needed **zero code changes** — it
was already reading the base URL from `@Value("${account-service.url}")`
and just concatenating paths onto it. The load-balancing behavior comes
entirely from the `@LoadBalanced` bean intercepting calls made through
that `RestTemplate`, not from anything in the client class.

**Side effect:** the local-vs-docker comment-toggle on
`account-service.url` goes away for this property. `@LoadBalanced`
always resolves by Eureka service name, so `http://account-service`
works the same whether Eureka server is running locally or in Docker —
only the `eureka.client.service-url.defaultZone` line still needs the
local/docker toggle, since that's the one hardcoded address left
(where to find the registry itself).

---

## 6. Why Eureka still matters when Docker DNS already resolves names

Worth being able to say out loud, since it's a natural follow-up
question: in the *current* single-instance-per-service Docker Compose
setup, Eureka is genuinely redundant with Docker's built-in DNS for
basic name resolution — that should be said plainly if asked, not
oversold.

Its real value here:

```
Docker DNS:  name --> container IP
             (resolves the instant the container starts, healthy or not,
              only ever one entry per name on this network)

Eureka:      name --> [ list of instances Eureka has confirmed alive
                         via heartbeat, drops fast if heartbeats stop ]
```

Three concrete gaps Docker DNS can't close that Eureka does:

1. **Health-awareness, not just existence** — an instance only shows
   `UP` after registering and passing heartbeats, not the instant its
   container starts.
2. **Multiple instances of the same service** — invisible right now
   with one instance each, but this is exactly what Phase 14's HPA and
   Phase 9's client-side load balancing need once there's more than one.
3. **Portability beyond one Compose network** — Docker's embedded DNS
   only works because everything shares one Compose network; Eureka
   doesn't care where the instance physically lives.

The pattern (app-level registry with health checks), not the specific
tool, is what carries forward into Kubernetes in Phase 14.

---

## 7. What was deliberately left out of Phase 6

Per the plan's own scope, still not touched:
```
API Gateway · Config Server · OpenFeign · Resilience4j
Kafka · RabbitMQ · Saga · Outbox · Distributed tracing · Kubernetes
```

Also **not yet done**, pending manual verification:
- `docker-compose.yml` doesn't have an `eureka-server` entry yet —
  this phase was tested locally in IntelliJ first, containerize when
  ready.
- End-to-end transfer hasn't been re-tested through the load-balanced
  path yet — confirming both services show `UP` on the Eureka dashboard
  proves registration works, not that a live call actually resolves
  correctly.

---

## 8. Known gaps carried forward from Phase 5 (unchanged this phase)

| Gap | What's missing | Solved in |
|---|---|---|
| Deposit/withdraw don't create transaction records | No cross-service event for it | Phase 10 (Kafka) |
| Transfer isn't atomic across services | No compensation on partial failure | Phase 11 (Saga) |
| Transaction Service → Account Service calls are unauthenticated | No service identity/gateway | Phase 7+ |
| No retry/fallback if Account Service is down | No resilience layer | Phase 9 (Resilience4j) |

Hardcoded service URLs — the one gap Phase 6 was supposed to close —
is now resolved.

---

## 9. Files added / modified this phase

```
eureka-server/                              — new, full standalone app
  pom.xml
  EurekaServerApplication.java
  application.yml

account-service/
  pom.xml                                   — modified (eureka client dep)
  AccountServiceApplication.java            — modified (@EnableDiscoveryClient)
  application.yml                           — modified (eureka client config)

transaction-service/
  pom.xml                                   — modified (eureka client + loadbalancer deps)
  TransactionServiceApplication.java        — modified (@EnableDiscoveryClient)
  application.yml                           — modified (eureka config + url change)
  config/RestTemplateConfig.java            — modified (@LoadBalanced)

  client/AccountServiceClient.java          — untouched, no code changes needed
```

---

## 10. What Phase 6 should let you explain, out loud, without notes

```
What does Eureka actually add on top of Docker's own DNS resolution?
Why does an instance need to register AND heartbeat, not just start?
What's the difference between service discovery and load balancing —
  and why are they two separate Spring Cloud starters?
Why did AccountServiceClient.java need zero code changes for this?
What would happen if transaction-service started before
  account-service had finished registering?
Why is enable-self-preservation: false fine for local dev but risky
  in production?
```

If all six of those have a confident, concrete answer, Phase 6 is done.