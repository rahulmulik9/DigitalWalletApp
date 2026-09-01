# 🧩 Phase 9 — Feign + Resilience4j Notes

> How Transaction Service calls Account Service — before Feign, after Feign, and how Resilience4j protects that call.

---

## 1️⃣ Life Before Feign: Plain `RestTemplate`

Without Feign, calling Account Service looks like this:

```java
String url = "http://account-service/api/wallets/" + walletId;
ResponseEntity<WalletResponse> response =
        restTemplate.exchange(url, HttpMethod.GET, null, WalletResponse.class);
WalletResponse wallet = response.getBody();
```

**Pain points students should notice:**
- You manually build the URL as a string — easy to typo, no compile-time safety.
- You manually pick the HTTP method, manually cast the response type.
- Error handling (404, 500, timeouts) is all manual `try/catch` around `exchange()`.
- If the service name changes or the path changes, nothing catches it until runtime.

It works, but it's **boilerplate you write again for every single call, to every single service.**

---

## 2️⃣ What Feign Actually Needs

Feign replaces all of that with a plain Java **interface** — you describe the call, Feign does the rest.

**Ingredients:**

1. **Dependency:**
   ```xml
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-openfeign</artifactId>
   </dependency>
   ```

2. **Enable it** on the main application class:
   ```java
   @EnableFeignClients
   @SpringBootApplication
   public class TransactionServiceApplication { ... }
   ```

3. **Declare the interface** — no implementation needed, Feign generates it at runtime:
   ```java
   @FeignClient(name = "account-service")
   public interface AccountClient {

       @GetMapping("/api/wallets/{walletId}")
       WalletResponse getWallet(@PathVariable("walletId") Long walletId);
   }
   ```

That's it. Call `accountClient.getWallet(id)` like a normal method — Feign turns it into an HTTP call.

**Why this works without a hardcoded URL:** `name = "account-service"` is the name Account Service registered under in Eureka. Feign + Spring Cloud LoadBalancer resolve that name into a real `host:port` at call time — the same discovery mechanism from Phase 6, just used automatically now instead of manually.

**The catch:** Feign *by itself* gives you zero protection. If Account Service is slow or down, your thread just hangs or throws immediately. That's the gap Resilience4j fills.

---

## 3️⃣ Why Resilience4j — the Big Picture

Resilience4j isn't one thing — it's **four independent safety mechanisms** you can mix and match, each guarding against a different kind of problem:

| Pattern | Question it answers | Protects against |
|---|---|---|
| **Circuit Breaker** | "Has this dependency been failing a lot? Should I even bother calling it?" | Wasting time/threads hammering a service that's clearly down |
| **Retry** | "Did this one call fail? Should I try again?" | Transient, one-off blips (a dropped packet, a momentary GC pause) |
| **Rate Limiter** | "Am I calling this dependency too fast?" | Overwhelming a healthy dependency with too much traffic |
| **Bulkhead** | "How many calls to this dependency am I allowed to have in-flight at once?" | One slow dependency eating all your threads and starving unrelated requests |

Think of a ship: **bulkheads** are physical compartments so one flooded section doesn't sink the whole ship. Software bulkheads do the same thing for threads/calls.

None of these fix the underlying problem (Account Service being slow) — they all exist to stop that problem from **spreading** and taking Transaction Service down with it.

---

## 4️⃣ Circuit Breaker — Line by Line

```yaml
resilience4j:
  circuitbreaker:
    instances:
      accountService:
        register-health-indicator: true
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        ignore-exceptions:
          - com.rahul.transaction_service.exception.WalletNotFoundException
          - com.rahul.transaction_service.exception.InsufficientBalanceException
```

**The 3 states (like an electrical circuit breaker in your house):**
- **CLOSED** — normal. Calls go through as usual.
- **OPEN** — tripped. Calls **fail immediately** without even trying — no network call happens at all.
- **HALF_OPEN** — cautiously testing. Lets a few real calls through to check "has it recovered?"

**Every property, explained:**

| Property | Meaning |
|---|---|
| `register-health-indicator: true` | This circuit breaker's current state shows up on `/actuator/health` — useful for monitoring/debugging. |
| `sliding-window-size: 10` | Judges health based on the **last 10 calls only**, not all-time history. |
| `minimum-number-of-calls: 5` | Won't even calculate a failure rate until at least 5 calls have happened — avoids overreacting to "1 out of 1 failed." |
| `failure-rate-threshold: 50` | If **≥50%** of the calls in the window failed, trip to OPEN. |
| `wait-duration-in-open-state: 10s` | Once OPEN, stay OPEN for 10 seconds before even trying HALF_OPEN — gives the failing service time to recover. |
| `permitted-number-of-calls-in-half-open-state: 3` | In HALF_OPEN, allow exactly 3 trial calls. If they mostly succeed → back to CLOSED. If they mostly fail → back to OPEN. |
| `ignore-exceptions` | These exceptions **don't count as failures** for the circuit breaker at all — as if the call never happened for health-tracking purposes. |

**Why `WalletNotFoundException` and `InsufficientBalanceException` are ignored here — this is the key teaching point:**
A circuit breaker should trip on **infrastructure problems** (timeouts, connection refused, 500s) — signs Account Service itself is unhealthy. `WalletNotFoundException` and `InsufficientBalanceException` are **normal business outcomes** — Account Service is working perfectly fine, it's just telling you "that wallet doesn't exist" or "not enough balance." If you didn't ignore these, a burst of legitimate "insufficient balance" errors from real users could accidentally trip the circuit breaker and start rejecting *everyone's* requests — even people with plenty of balance. Don't let business logic errors masquerade as infrastructure failures.

---

## 5️⃣ Retry — Line by Line

```yaml
  retry:
    instances:
      accountService:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        ignore-exceptions:
          - com.rahul.transaction_service.exception.WalletNotFoundException
          - com.rahul.transaction_service.exception.InsufficientBalanceException
```

| Property | Meaning |
|---|---|
| `max-attempts: 3` | Total attempts **including the first call** — so up to 2 retries after the initial failure. |
| `wait-duration: 500ms` | Base wait time between attempts. |
| `enable-exponential-backoff: true` | Instead of waiting a flat 500ms every time, the wait **grows** between attempts. |
| `exponential-backoff-multiplier: 2` | Attempt 1 fails → wait ~500ms → Attempt 2 fails → wait ~500ms × 2 = ~1000ms → Attempt 3. |
| `ignore-exceptions` | Same two business exceptions — **don't retry these at all**, fail immediately instead. |

**Why exponential backoff instead of flat retries:** if Account Service is briefly overloaded, hammering it with retries every 500ms flat makes the overload *worse*. Growing the wait gives it breathing room to recover.

**Why ignore the same exceptions here too:** retrying "insufficient balance" three times is pointless — the balance won't change between attempt 1 and attempt 3 just because you waited 500ms. Retry only makes sense for **transient** failures, not deterministic business outcomes.

**🚨 The most important rule in this whole file:** `@Retry` is only ever applied to `getWallet()` (a read) — **never** to `debit()` or `credit()` (a write), and this is intentional, not an oversight. If a debit call times out *after* Account Service already processed it, but *before* the response reaches Transaction Service, a blind retry would debit the wallet **twice**. Retrying writes safely requires an idempotency key so the retried call can be recognized and ignored server-side — that's Phase 11's job, not Phase 9's.

---

## 6️⃣ Rate Limiter — Line by Line

```yaml
  ratelimiter:
    instances:
      accountService:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 500ms
```

| Property | Meaning |
|---|---|
| `limit-for-period: 10` | Max 10 calls allowed per period. |
| `limit-refresh-period: 1s` | The period is 1 second — so effectively "max 10 calls/sec to Account Service." |
| `timeout-duration: 500ms` | If the limit is already used up, an incoming (e.g. 11th) call **waits up to 500ms** for the next window to open. If it still can't get a slot, it fails fast instead of hanging. |

**Circuit Breaker vs Rate Limiter — the difference students often mix up:**
- Circuit Breaker is **reactive** — it responds to Account Service *already failing*.
- Rate Limiter is **proactive** — it caps outgoing traffic regardless of whether Account Service is healthy or not. It protects Account Service **from Transaction Service itself** sending too much traffic, not the other way around.

---

## 7️⃣ Bulkhead — Line by Line

```yaml
  bulkhead:
     instances:
       accountService:
         max-concurrent-calls: 5
         max-wait-duration: 500ms
```

| Property | Meaning |
|---|---|
| `max-concurrent-calls: 5` | At most 5 calls to Account Service can be **in-flight at the same time**. |
| `max-wait-duration: 500ms` | A 6th call waits up to 500ms for one of those 5 slots to free up, then fails fast if none does. |

**Why this matters even with a Circuit Breaker in place:** the Circuit Breaker only trips after enough calls have already failed. Before it trips, if Account Service is *slow* (not down), every incoming request could pile up waiting on a slow response — potentially exhausting all of Transaction Service's own threads. Bulkhead caps how many requests are allowed to be "stuck waiting" on Account Service at once, protecting Transaction Service's own capacity to serve *other* unrelated requests.

> There's also a **ThreadPoolBulkhead** variant (a separate dedicated thread pool per dependency) vs the **SemaphoreBulkhead** shown here (just a counting limit on the calling thread). Semaphore is simpler and is what's configured above — worth knowing ThreadPoolBulkhead exists if you go deeper later, but not needed now.

---

## 8️⃣ The Annotations, Mapped to Config

```java
@Bulkhead(name = "accountService")
@RateLimiter(name = "accountService")
@Retry(name = "accountService")
@CircuitBreaker(name = "accountService", fallbackMethod = "getWalletFallback")
public WalletResponse getWallet(Long walletId) {
    return accountClient.getWallet(walletId);
}

public WalletResponse getWalletFallback(Long walletId, Throwable t) {
    throw new AccountServiceUnavailableException("Account service unavailable, try again later");
}
```

- **`name = "accountService"` must match the `instances:` key** in the YAML (`resilience4j.circuitbreaker.instances.accountService`, etc). This is the wiring — the annotation and the config block are connected purely by this string matching.
- **`fallbackMethod`** — only `@CircuitBreaker` defines one here. Its signature must match the original method's parameters **plus a trailing `Throwable`**. It's called when the circuit is OPEN, or when any exception not in `ignore-exceptions` propagates all the way out.

### ⚠️ Correcting a common misconception: annotation order ≠ execution order

It's natural to assume the order you *stack* these annotations top-to-bottom controls the order they run in (like wrapping decorators). **That's not how Resilience4j's Spring integration works** — the actual execution order is fixed internally by the library (each aspect has its own priority), **regardless of how you arrange the annotations in your code.**

Resilience4j's documented default order, outermost to innermost, is:

```
Retry → CircuitBreaker → RateLimiter → Bulkhead → (the actual call)
```

**Why this specific order is deliberately chosen, not arbitrary:** Retry sits on the *outside* so that when it retries, **each individual attempt passes back through the Circuit Breaker** and gets recorded in its sliding window as a separate call. If it were the other way around (Circuit Breaker outside Retry), the Circuit Breaker would only ever see "one call" per request — no matter how many times Retry silently tried underneath — and it would never get an accurate picture of the real failure rate.

**Practical takeaway for students:** don't try to control execution order by rearranging `@Bulkhead`/`@RateLimiter`/`@Retry`/`@CircuitBreaker` in your source — it won't do anything. If you ever genuinely need to change the order, Resilience4j exposes explicit properties for that (e.g. `resilience4j.retry.retryAspectOrder`), but the default order above is correct for almost every real use case, including this one.

---

## 9️⃣ Putting It All Together — What Happens on One Call

Tracing a single `getWallet()` call through the default order (`Retry → CircuitBreaker → RateLimiter → Bulkhead → actual call`):

```
1. Retry wrapper receives the call.
2. Attempt #1:
   a. CircuitBreaker check: is the circuit OPEN? If yes → fail immediately, skip to fallback.
   b. RateLimiter check: is there room in this second's quota? If not → wait up to 500ms, then fail fast if still none.
   c. Bulkhead check: is there a free concurrent-call slot (max 5)? If not → wait up to 500ms, then fail fast if still none.
   d. Actual Feign call goes out → Eureka resolves account-service → real HTTP request.
3. If attempt #1 fails with a retryable exception (not WalletNotFoundException/InsufficientBalanceException):
   → wait ~500ms → Attempt #2 (repeats steps a-d, including a *fresh* CircuitBreaker/RateLimiter/Bulkhead check)
   → if that fails too → wait ~1000ms → Attempt #3
4. If all attempts are exhausted, or a non-retryable exception is thrown at any point:
   → CircuitBreaker's fallbackMethod (getWalletFallback) runs, throwing AccountServiceUnavailableException.
5. GlobalExceptionHandler catches that and returns a clean 503 to the client — not a raw stack trace.
```

---

## 🔟 Quick Recap Cheat-Sheet

- [ ] Feign replaces manual `RestTemplate` boilerplate with a declarative interface — `name` in `@FeignClient` must match the target's registered Eureka name
- [ ] Feign alone = zero protection. Resilience4j adds four independent, combinable safety nets
- [ ] **Circuit Breaker** — stops calling a service that's clearly failing (judged over a sliding window, not all-time)
- [ ] **Retry** — re-attempts a failed call, with growing wait times (exponential backoff)
- [ ] **Rate Limiter** — proactively caps outgoing call rate, regardless of success/failure
- [ ] **Bulkhead** — caps concurrent in-flight calls, so a slow dependency can't starve unrelated requests
- [ ] `ignore-exceptions` on both CircuitBreaker and Retry = "this is a normal business outcome, not an infrastructure failure — don't count it, don't retry it"
- [ ] Never apply `@Retry` to a mutating call (debit/credit) without idempotency in place first
- [ ] Annotation stacking order in your Java code is cosmetic — actual execution order is fixed by Resilience4j: `Retry → CircuitBreaker → RateLimiter → Bulkhead`
- [ ] Only one fallback method is usually needed — on `@CircuitBreaker` — as the final catch-all after everything else has been tried