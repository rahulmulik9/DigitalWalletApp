# Phase 8 — Config Server (Full Explanation)

This document explains **everything** we did in Phase 8, in simple
language. No assumptions — if you're learning this for the first time,
you should be able to follow every point here.

---

## 1. What problem were we trying to solve?

Before this phase, every microservice (account-service, transaction-service,
eureka-server, api-gateway) had its **own** `application.yml` file sitting
inside its own project folder.

Problems this caused:

- If the **JWT secret** needed to change, you had to open and edit it in
  **4 different files**, in 4 different projects.
- If a **database password** changed, same problem — edit it everywhere.
- To switch between "running on my laptop" and "running in Docker," you
  had to **manually comment/uncomment lines** in each file, every time.
  Easy to forget. Easy to commit the wrong version by mistake.
- There was no single place to look at "what config is this service
  actually using right now?"

**Config Server solves this** by pulling all these settings out of each
service and putting them in **one central place** — a Git repository —
that every service asks for its settings from, at startup.

---

## 2. The big picture — how it all fits together

```
        ┌─────────────────────────┐
        │   GitHub Repo            │
        │   (payflow-config-repo)  │
        │                          │
        │   account-service.yml    │
        │   transaction-service.yml│
        │   eureka-server.yml      │
        │   api-gateway.yml        │
        │   application.yml (shared)│
        └───────────┬──────────────┘
                     │
                     │ Config Server reads from here
                     ▼
        ┌─────────────────────────┐
        │     config-server        │
        │     (port 8888)          │
        └───────────┬──────────────┘
                     │
   Every other service asks config-server:
   "What are MY settings?"
                     │
   ┌─────────┬───────┼────────┬─────────────┐
   ▼         ▼       ▼        ▼             ▼
eureka-   account-  transaction-  api-gateway
server    service   service
```

**Rule to remember:** `config-server` is the very first thing that must
be running. Nothing else can properly start without it, because every
other service now asks it for their settings.

---

## 3. What actually lives in the Git repo

We created a **brand new, separate Git repository** just for config
files — not mixed in with the actual application code.

```
payflow-config-repo/
├── application.yml              ← shared by EVERY service
├── account-service.yml          ← account-service's own settings
├── account-service-local.yml    ← ONLY used when running locally
├── account-service-docker.yml   ← ONLY used when running in Docker
├── transaction-service.yml
├── transaction-service-local.yml
├── transaction-service-docker.yml
├── eureka-server.yml            ← no local/docker split needed here
├── api-gateway.yml
├── api-gateway-local.yml
└── api-gateway-docker.yml
```

**What went into the shared `application.yml`:**
- JWT secret
- Database username/password

**What went into each service's own file:**
- Server port
- JPA/database settings
- Logging levels
- Eureka address (different for local vs docker)
- Gateway routing rules (only in api-gateway's file)

---

## 4. How Config Server actually finds the right file (important!)

This is the part students usually get confused about, so read carefully.

**Config Server does NOT do anything clever.** It just matches
**filenames** using a fixed rule:

```
When a service asks for its config, it sends TWO pieces of information:

   1. Its own name       (e.g. "account-service")
   2. Its active profile (e.g. "local" or "docker")

Config Server then looks for files named EXACTLY:

   {name}-{profile}.yml   ← most specific, checked first
   {name}.yml
   application-{profile}.yml
   application.yml         ← shared, checked last

Whatever files exist get merged together and sent back.
```

**Example:** account-service asks with name=`account-service`,
profile=`docker`. Config Server looks for:
```
account-service-docker.yml   ✔ found → used
account-service.yml          ✔ found → used
application.yml              ✔ found → used
```
All three get merged into one final config and sent back.

**Important rule:** the filenames must match **exactly**. If your
service's name is `account-service` but you accidentally name the file
`accountservice.yml` (no hyphen), Config Server will simply not find
it — and it won't loudly tell you either. It just quietly serves less
config than you expected. Always double-check filenames match exactly.

---

## 5. The chicken-and-egg problem — and how we solved it

Here's a tricky question: **if all the settings live in Config Server,
how does a service know WHERE Config Server even is?**

That one piece of information — "where is Config Server" — **cannot**
itself live inside Config Server. That would be circular. A service
needs to know this **before** it can ask Config Server for anything.

**Solution:** every service keeps a tiny local file that never
changes and never gets centralized:

```yaml
# application.yaml (stays local, tiny)
spring:
  application:
    name: account-service
```

And then we use **two more small local files** to hold just the
address of Config Server, depending on where the app is running:

```yaml
# application-local.yaml (only used when running locally)
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

```yaml
# application-docker.yaml (only used when running in Docker)
spring:
  config:
    import: "optional:configserver:http://config-server:8888"
```

This works because **Spring Boot itself** (not Config Server) already
knows how to load `application-{profile}.yaml` automatically, based on
whichever profile is active. So this piece doesn't depend on Config
Server at all — it's the one exception, and it has to be.

---

## 5a. Recap — why account-service alone needs 6 YAML files

This confuses almost everyone the first time, so here's the short
version, all in one place.

**The one real reason:** `account-service` needs its settings (DB URL,
Eureka URL, ports, etc.) from Config Server — but the address of Config
Server itself is different depending on where the app is running,
because `localhost` means "this machine" on your laptop, but means
"this container" inside Docker. Something has to hold that difference,
and it can't be Config Server, because you haven't reached it yet.

That single fact splits the config into **two stages**, and each stage
needs its own local vs docker version:

**Stage 1 — Bootstrap: "How do I even reach Config Server?"**
Lives in the `resources` folder, baked into the jar. Can't come from
Config Server (chicken-and-egg). This is 3 files:

- `application.yaml` — just the app name + actuator exposure, same
  everywhere, no profile needed.
- `application-local.yaml` — Config Server is at `localhost:8888`.
- `application-docker.yaml` — Config Server is at `config-server:8888`
  (the Docker Compose service name).

```yaml
spring:
  config:
    activate:
      on-profile: local
    import: "optional:configserver:http://localhost:8888"
---
spring:
  config:
    activate:
      on-profile: docker
    import: "optional:configserver:http://config-server:8888"
```

**Stage 2 — Runtime: "Now that I'm connected, what's my actual config?"**
Lives in the GitHub config repo, served by Config Server. This is the
other 3 files:

- `account-service.yml` — port, JPA/DDL, SQL init, logging. Same
  everywhere, no profile needed.
- `account-service-local.yml` — DB at `localhost:5433`, Eureka at
  `localhost:8761`.
- `account-service-docker.yml` — DB at `account-db:5432`, Eureka at
  `eureka-server:8761`.

**How the two stages stay in sync:** a single environment variable,
`SPRING_PROFILES_ACTIVE`, set once per environment:

- **Local run** → IDE Run Configuration → Environment Variables →
  `SPRING_PROFILES_ACTIVE=local`
- **Docker run** → `docker-compose.yml` → `environment:` →
  `SPRING_PROFILES_ACTIVE=docker`

Flipping that one variable decides which resource-folder file
activates (so it knows where Config Server is) **and** which GitHub
file gets fetched (so it gets the right DB/Eureka addresses) — both at
once, automatically.

**Could this be fewer physical files?** Yes — each pair
(`application-local.yaml` + `application-docker.yaml`, or
`account-service-local.yml` + `account-service-docker.yml`) can be
merged into a single file using YAML's `---` multi-document syntax with
`spring.config.activate.on-profile`, as shown above. The *file count*
is a style choice; the *concept* of "local values differ from docker
values, and something has to hold both" is not optional — it's the
actual reason all 6 pieces of information exist, whether they live in
3 files, 6 files, or 2 merged files.

---

## 6. What is a "Profile," in simple terms?

A **profile** is just a **label** you give to a specific way of running
your app. You pick the label with an environment variable:

```
SPRING_PROFILES_ACTIVE=local     → tells Spring "use my local settings"
SPRING_PROFILES_ACTIVE=docker    → tells Spring "use my docker settings"
```

- **Running in IntelliJ:** you set this in Run → Edit Configurations →
  Environment Variables.
- **Running in Docker:** you set this inside `docker-compose.yml` under
  `environment:`.

**Why this is better than the old way (commenting lines in/out):**

| Old way (comment toggling) | New way (profiles) |
|---|---|
| You edit the actual file every time | The file never changes |
| Easy to forget to flip it back | The environment decides automatically |
| Only one version "active" at a time | Both versions always exist, side by side |

---

## 7. Step-by-step: what changed in EVERY service

For **account-service**, **transaction-service**, **eureka-server**, and
**api-gateway**, we made the exact same three changes:

**Step 1 — `pom.xml`:** add one new dependency
```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```
⚠️ **After adding this, you MUST reload Maven** (right-click `pom.xml` →
Maven → Reload Project). If you forget, the dependency isn't actually
usable yet, even though it's written in the file — and this causes a
very confusing crash later (explained in section 9).

**Step 2 — shrink `application.yaml`** down to almost nothing:
```yaml
spring:
  application:
    name: account-service
```

**Step 3 — add two new files:** `application-local.yaml` and
`application-docker.yaml`, each holding just the `spring.config.import`
line pointing at the correct Config Server address (shown in section 5).

That's it. Every other setting that used to live in the big
`application.yaml` now lives in the Git repo instead.

---

## 8. Building `config-server` itself

`config-server` is a **brand new microservice**, separate from the
other four. Its whole job is to read the Git repo and serve files.

```java
@SpringBootApplication
@EnableConfigServer     // ← this one annotation turns on the whole feature
public class ConfigServerApplication {
  public static void main(String[] args) {
    SpringApplication.run(ConfigServerApplication.class, args);
  }
}
```

Its own `application.yml`:
```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-username/payflow-config-repo.git
          default-label: main
```

**Important:** `config-server` does **not** connect to Eureka, and does
**not** pull its own settings from anywhere else. It's the very root of
the whole chain — it has to be fully self-contained.

**⚠️ Gotcha we actually hit:** `default-label: main` failed because the
GitHub repo's real default branch was called `master`, not `main`.
Config Server automatically fell back to `master` and still worked, but
it's better to fix the setting to match reality rather than rely on the
fallback silently covering the mistake.

---

## 9. Real mistakes we made — and what they taught us

**Mistake 1 — forgot to reload Maven after adding the dependency**

What happened: we added `spring-cloud-starter-config` to `pom.xml` but
never reloaded Maven in IntelliJ. The app crashed with a confusing error
about `jwt.secret` not being found — which had **nothing to do** with
JWT itself. The real problem: without the dependency actually loaded,
Spring couldn't process `spring.config.import`, so it never fetched
ANY settings from Config Server — including the JWT secret.

**Lesson:** if you add something to `pom.xml`, always reload Maven
before running the app again.

**Mistake 2 — Docker images don't auto-update**

What happened: we edited local YAML files (splitting them into
`-local`/`-docker` versions), but the Docker containers kept crashing
with the same old error. Why? Because Docker images are built **once**,
at a specific point in time (using `mvn jib:buildTar`). Editing a file
on your computer does **not** change an image that was already built.

**Lesson:** every time you change code or config files that live
*inside* a service's own project, you must rebuild and reload that
service's Docker image before `docker compose up` will use the new
version.

**Mistake 3 — startup order got stricter, not simpler**

Before this phase, `eureka-server` needed nothing to start — it just
booted up on its own. After wiring it to Config Server, it now needs
`config-server` to be running FIRST. This made the required start order
longer:
```
config-server → eureka-server → account-service → transaction-service → api-gateway
```

**Lesson:** adding a new dependency (Config Server) can tighten your
startup requirements even for services that seem unrelated to it.

---

## 10. Why `/actuator/health` matters for Docker

We added Spring Boot **Actuator** to every service and made sure
`/actuator/health` doesn't require login (`permitAll`). Here's why this
matters, explained simply:

**The old way — `depends_on`:**
```yaml
depends_on:
  - config-server
```
This only tells Docker: "don't even *launch* this container until that
other container has started launching."

**The problem:** "started launching" and "actually ready to use" are
very different things. A Spring Boot app can take 20-30 seconds after
its container starts before it's actually done — connecting to the
database, registering with Eureka, fetching its settings from Config
Server, etc. `depends_on` alone doesn't know or care about any of that.

**The fix — health checks:**
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8888/actuator/health"]
  interval: 5s
  timeout: 3s
  retries: 10
  start_period: 20s

# and on the DEPENDENT service:
depends_on:
  config-server:
    condition: service_healthy
```

Now Docker actually **calls** `/actuator/health` again and again, and
only considers the container "healthy" once Spring Boot itself reports
that it's truly up and running. Any other container that depends on it
will **wait** until that real health check passes — not just until the
process was launched.

**⚠️ CRITICAL — do not forget this for any new service:**

`/actuator/health` **must** be set to `permitAll()` in every service's
security rules. If it's accidentally left behind a login/JWT check,
Docker's health check call gets rejected (401 Unauthorized) instead of
succeeding (200 OK). Docker then thinks the app is "unhealthy" forever,
even though the app is actually working completely fine. Every service
that depends on it will then wait forever (or time out), and it will
look like a startup bug — when the real problem is just a forgotten
security rule.

---

## 11. Known simplification (be honest about this in interviews)

Right now, the **JWT secret** and **database passwords** live inside the
Git config repo, same as everything else. In a real company, this is
**not** how it's done — secrets should live in a dedicated secrets tool
like **HashiCorp Vault** or **AWS Secrets Manager**, completely separate
from normal config, and never committed to Git at all (even a private
repo).

We kept it simple here on purpose, since setting up a real secrets
manager is a bigger task outside this phase's scope. If asked in an
interview: *"I understand the difference between config and secrets —
for this project I combined them into one Config Server for simplicity,
but in a real production system I'd separate secrets into a dedicated
secrets manager."*

---

## 12. Quick recap — the whole phase in 6 lines

1. Made a separate Git repo holding every service's settings.
2. Built a new `config-server` app that reads that repo and serves files.
3. Every other service now asks `config-server` for its settings at startup, instead of reading a local file.
4. Local vs Docker is now controlled by an environment variable (a "profile"), not by editing files.
5. Added health checks so Docker containers wait for each other to be *truly ready*, not just *started*.
6. Documented, on purpose, that real secrets (passwords, JWT key) would live somewhere safer in a real company — Vault or AWS Secrets Manager, not a Git repo.